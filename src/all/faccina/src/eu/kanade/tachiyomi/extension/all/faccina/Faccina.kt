package eu.kanade.tachiyomi.extension.all.faccina

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.extension.all.faccina.FaccinaHelper.getIdFromUrl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

open class Faccina(private val suffix: String = "") : ConfigurableSource, UnmeteredSource,
    HttpSource() {
    override val baseUrl by lazy { getPrefBaseUrl() }

    override val lang = "all"

    override val name by lazy { "Faccina (${getPrefCustomLabel()})" }

    override val supportsLatest = true

    private val json by lazy { Injekt.get<Json>() }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().build()

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/library?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = json.decodeFromString<LibraryResponse>(response.body.string())

        return MangasPage(
            data.archives.map {
                it.toSManga()
            }.toList(),
            data.page * data.limit < data.total,
        )
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/library?page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/api/library?page=$page&query=$query", headers)

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/api/g/${getIdFromUrl(manga.url)}", headers)

    override fun mangaDetailsParse(response: Response) =
        json.decodeFromString<Archive>(response.body.string()).toSManga()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/g/${getIdFromUrl(manga.url)}"

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                url = "/g/${getIdFromUrl(manga.url)}/read"
                name = "Chapter"
            },
        ),
    )

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    // Page List

    override fun pageListRequest(chapter: SChapter) =
        GET("$baseUrl/api/g/${getIdFromUrl(chapter.url)}")

    override fun pageListParse(response: Response) =
        json.decodeFromString<Archive>(response.body.string()).toPageList()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Preferences
    override val id by lazy {
        // Retain previous ID for first entry
        val key = "faccina" + (if (suffix == "1") "" else "_$suffix") + "/all/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    internal val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(HOSTNAME_KEY, HOSTNAME_DEFAULT)!!

    private fun getPrefCustomLabel(): String =
        preferences.getString(CUSTOM_LABEL_KEY, suffix)!!.ifBlank { suffix }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        if (suffix == "1") {
            ListPreference(screen.context).apply {
                key = EXTRA_SOURCES_COUNT_KEY
                title = "Number of extra sources"
                summary =
                    "Number of additional sources to create. There will always be at least one Faccina source."
                entries = EXTRA_SOURCES_ENTRIES
                entryValues = EXTRA_SOURCES_ENTRIES

                setDefaultValue(EXTRA_SOURCES_COUNT_DEFAULT)
                setOnPreferenceChangeListener { _, newValue ->
                    try {
                        val setting = preferences.edit()
                            .putString(EXTRA_SOURCES_COUNT_KEY, newValue as String).commit()
                        Toast.makeText(
                            screen.context,
                            "Restart Tachiyomi to apply new setting.",
                            Toast.LENGTH_LONG,
                        ).show()
                        setting
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }.also(screen::addPreference)
        }
        screen.addPreference(
            screen.editTextPreference(
                HOSTNAME_KEY,
                "Hostname",
                HOSTNAME_DEFAULT,
                baseUrl,
                refreshSummary = true,
            ),
        )
        screen.addPreference(
            screen.editTextPreference(
                CUSTOM_LABEL_KEY,
                "Custom Label",
                "",
                "Show the given label for the source instead of the default.",
            ),
        )
    }

    private fun androidx.preference.PreferenceScreen.checkBoxPreference(
        key: String,
        title: String,
        default: Boolean,
        summary: String = "",
    ): androidx.preference.CheckBoxPreference {
        return androidx.preference.CheckBoxPreference(context).apply {
            this.key = key
            this.title = title
            this.summary = summary
            setDefaultValue(default)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(this.key, newValue as Boolean).commit()
            }
        }
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(
        key: String,
        title: String,
        default: String,
        summary: String,
        isPassword: Boolean = false,
        refreshSummary: Boolean = false,
    ): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            this.key = key
            this.title = title
            this.summary = summary
            this.setDefaultValue(default)

            if (isPassword) {
                setOnBindEditTextListener {
                    it.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val newString = newValue.toString()
                    val res = preferences.edit().putString(this.key, newString).commit()

                    if (refreshSummary) {
                        this.apply {
                            this.summary = newValue as String
                        }
                    }

                    Toast.makeText(
                        context,
                        "Restart Tachiyomi to apply new setting.",
                        Toast.LENGTH_LONG,
                    ).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    companion object {
        internal const val EXTRA_SOURCES_COUNT_KEY = "extraSourcesCount"
        internal const val EXTRA_SOURCES_COUNT_DEFAULT = "2"
        private val EXTRA_SOURCES_ENTRIES = (0..10).map { it.toString() }.toTypedArray()

        private const val HOSTNAME_DEFAULT = "http://127.0.0.1:3823"
        private const val HOSTNAME_KEY = "hostname"
        private const val CUSTOM_LABEL_KEY = "customLabel"
    }
}
