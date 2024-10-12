package eu.kanade.tachiyomi.extension.all.faccina

import android.app.Application
import android.content.SharedPreferences
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

open class Faccina(private val suffix: String = "") :
    ConfigurableSource, UnmeteredSource, HttpSource() {

    override val baseUrl by lazy { preferences.getString(PREF_ADDRESS, "")!!.removeSuffix("/") }

    override val lang: String = "all"

    override val name by lazy {
        val displayNameSuffix = displayName
            .ifBlank { suffix }
            .let { if (it.isNotBlank()) " ($it)" else "" }

        "Faccina$displayNameSuffix"
    }

    override val supportsLatest = true

    override val id by lazy {
        val key = "faccina${if (suffix.isNotBlank()) " ($suffix)" else ""}/all/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    override val client = network.cloudflareClient

    private val json by lazy { Injekt.get<Json>() }

    internal val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val displayName by lazy { preferences.getString(PREF_DISPLAY_NAME, "")!! }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/api/library?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = json.decodeFromString<LibraryResponse>(response.body.string())

        return MangasPage(
            data.archives.map {
                it.toSManga(baseUrl)
            }.toList(),
            data.page * data.limit < data.total,
        )
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/library?page=$page", headers)

    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/api/library?page=$page&q=$query", headers)

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Details

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$baseUrl/api/g/${getIdFromUrl(manga.url)}", headers)

    override fun mangaDetailsParse(response: Response) =
        json.decodeFromString<Archive>(response.body.string()).toSManga(baseUrl)

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
        json.decodeFromString<Archive>(response.body.string()).toPageList(baseUrl)

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/g/${getIdFromUrl(chapter.url)}"

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (suffix.isEmpty()) {
            ListPreference(screen.context).apply {
                key = PREF_EXTRA_SOURCES_COUNT
                title = "Number of extra sources"
                summary =
                    "Number of additional sources to create. There will always be at least one Faccina source."
                entries = PREF_EXTRA_SOURCES_ENTRIES
                entryValues = PREF_EXTRA_SOURCES_ENTRIES

                setDefaultValue(PREF_EXTRA_SOURCES_DEFAULT)
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(
                        screen.context,
                        "Restart Tachiyomi to apply new setting.",
                        Toast.LENGTH_LONG,
                    ).show()
                    true
                }
            }.also(screen::addPreference)
        }

        screen.addEditTextPreference(
            title = "Source display name",
            default = suffix,
            summary = displayName.ifBlank { "Here you can change the source displayed suffix" },
            key = PREF_DISPLAY_NAME,
            restartRequired = true,
        )
        screen.addEditTextPreference(
            title = "Address",
            default = "",
            summary = baseUrl.ifBlank { "The server address" },
            dialogMessage = "The address must not end with a forward slash.",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            validate = { it.toHttpUrlOrNull() != null && !it.endsWith("/") },
            validationMessage = "The URL is invalid, malformed, or ends with a slash",
            key = PREF_ADDRESS,
            restartRequired = true,
        )
    }

    private fun PreferenceScreen.addEditTextPreference(
        title: String,
        default: String,
        summary: String,
        dialogMessage: String? = null,
        inputType: Int? = null,
        validate: ((String) -> Boolean)? = null,
        validationMessage: String? = null,
        key: String = title,
        restartRequired: Boolean = false,
    ) {
        EditTextPreference(context).apply {
            this.key = key
            this.title = title
            this.summary = summary
            this.setDefaultValue(default)
            dialogTitle = title
            this.dialogMessage = dialogMessage

            setOnBindEditTextListener { editText ->
                if (inputType != null) {
                    editText.inputType = inputType
                }

                if (validate != null) {
                    editText.addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) {
                            }

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) {
                            }

                            override fun afterTextChanged(editable: Editable?) {
                                requireNotNull(editable)

                                val text = editable.toString()

                                val isValid = text.isBlank() || validate(text)

                                editText.error = if (!isValid) validationMessage else null
                                editText.rootView.findViewById<Button>(android.R.id.button1)
                                    ?.isEnabled = editText.error == null
                            }
                        },
                    )
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val text = newValue as String
                    val result = text.isBlank() || validate?.invoke(text) ?: true

                    if (restartRequired && result) {
                        Toast.makeText(
                            context,
                            "Restart Tachiyomi to apply new settings.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }

                    result
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.also(::addPreference)
    }

    companion object {
        internal const val PREF_EXTRA_SOURCES_COUNT = "Number of extra sources"
        internal const val PREF_EXTRA_SOURCES_DEFAULT = "2"
        private val PREF_EXTRA_SOURCES_ENTRIES = (0..10).map { it.toString() }.toTypedArray()

        private const val PREF_DISPLAY_NAME = "Source display name"
        private const val PREF_ADDRESS = "Address"
    }
}
