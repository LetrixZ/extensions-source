package eu.kanade.tachiyomi.extension.all.faccina

import android.app.Application
import android.content.Context
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
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

open class Faccina(private val suffix: String = "") :
    ConfigurableSource, UnmeteredSource, HttpSource() {

    override val baseUrl by lazy {
        preferences.getString(PREF_HOST_ADDRESS, "")!!.removeSuffix("/")
    }

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

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()

            if (request.url.toString().contains("/image/") &&
                request.url.queryParameter("type") == null
            ) {
                val reader = serverConfig?.reader
                val presetHash = imagePreset?.split(":")?.last()

                if (reader != null) {
                    if (presetHash != null && reader.presets.any { it.hash == presetHash }) {
                        val newRequest =
                            request.newBuilder().url("${request.url}?type=$presetHash").build()
                        return@addInterceptor chain.proceed(newRequest)
                    } else if (reader.defaultPreset != null) {
                        val newRequest =
                            request.newBuilder().url("${request.url}?type=${reader.defaultPreset}")
                                .build()
                        return@addInterceptor chain.proceed(newRequest)
                    }
                } else if (presetHash != null) {
                    val newRequest =
                        request.newBuilder().url("${request.url}?type=$presetHash").build()
                    return@addInterceptor chain.proceed(newRequest)
                }
            }

            return@addInterceptor chain.proceed(request)
        }.build()

    private val json by lazy { Injekt.get<Json>() }

    internal val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val displayName by lazy { preferences.getString(PREF_DISPLAY_NAME, "")!! }

    private val imagePreset by lazy {
        preferences.getString(
            PREF_IMAGE_PRESET,
            PREF_IMAGE_ORIGINAL_PRESET,
        )
    }

    private val serverConfig: ServerConfig? by lazy {
        try {
            client.newCall(GET("$baseUrl/api/config", headers)).execute().use {
                json.decodeFromStream<ServerConfig>(it.body.byteStream())
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/library?page=$page&q=$query".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    val state = filter.state
                    url.addQueryParameter(
                        "sort",
                        when (state?.index) {
                            1 -> "created_at"
                            2 -> "title"
                            3 -> "pages"
                            else -> "released_at"
                        },
                    )

                    if (state != null) {
                        url.addQueryParameter(
                            "order",
                            if (state.ascending) "asc" else "desc",
                        )
                    }
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

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
                name = "1. Chapter"
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

    // Filters

    override fun getFilterList() = FilterList(
        SortFilter(),
    )

    private class SortFilter : Filter.Sort(
        "Sort",
        arrayOf("Date released", "Date added", "Title", "Pages"),
        Selection(0, false),
    )

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (suffix.isEmpty()) {
            ListPreference(screen.context).apply {
                title = "Number of extra sources"
                summary = "Number of additional sources to create."
                entries = PREF_EXTRA_SOURCES_ENTRIES
                entryValues = PREF_EXTRA_SOURCES_ENTRIES
                key = PREF_EXTRA_SOURCES_COUNT

                setDefaultValue(PREF_EXTRA_SOURCES_DEFAULT)
                setOnPreferenceChangeListener { _, _ ->
                    toastRestart(screen.context)
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
            key = PREF_HOST_ADDRESS,
            restartRequired = true,
        )

        val presetList = ListPreference(screen.context).apply {
            title = "Image quality preset"
            entries = arrayOf("Original")
            entryValues = arrayOf(PREF_IMAGE_ORIGINAL_PRESET)
            summary = imagePreset?.split(":")?.first()
            key = PREF_IMAGE_PRESET

            setDefaultValue(PREF_IMAGE_ORIGINAL_PRESET)
            setOnPreferenceChangeListener { _, _ ->
                toastRestart(screen.context)
                true
            }
        }.also(screen::addPreference)

        CoroutineScope(Dispatchers.IO).launch {
            val reader = serverConfig?.reader

            if (reader != null) {
                if (reader.presets.isNotEmpty()) {
                    val entries = mutableListOf<String>()
                    val entryValues = mutableListOf<String>()

                    if (reader.allowOriginal) {
                        entries.add("Original")
                        entryValues.add(PREF_IMAGE_ORIGINAL_PRESET)
                    }

                    reader.presets.forEach {
                        entries.add(it.label)
                        entryValues.add(it.toValue())
                    }

                    presetList.entries = entries.toTypedArray()
                    presetList.entryValues = entryValues.toTypedArray()
                }

                if (imagePreset == null && reader.defaultPreset != null) {
                    reader.presets.find { it.hash === reader.defaultPreset.hash }.also {
                        presetList.setDefaultValue(it!!.toValue())
                    }
                }

                if (imagePreset != null && !presetList.entryValues.contains(imagePreset)) {
                    if (reader.defaultPreset != null || reader.presets.isNotEmpty()) {
                        val defaultPreset =
                            reader.presets.find { it.hash === reader.defaultPreset?.hash }
                                ?: reader.presets.first()
                        preferences.edit().putString(PREF_IMAGE_PRESET, defaultPreset.toValue())
                            .apply()
                        presetList.summary = defaultPreset.label
                        presetList.value = defaultPreset.toValue()
                    } else if (reader.allowOriginal) {
                        preferences.edit().putString(PREF_IMAGE_PRESET, PREF_IMAGE_ORIGINAL_PRESET)
                            .apply()
                        presetList.summary = "Original"
                        presetList.value = PREF_IMAGE_ORIGINAL_PRESET
                    }
                }
            }
        }
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
                        toastRestart(context)
                    }

                    result
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.also(::addPreference)
    }

    private fun toastRestart(context: Context) {
        Toast.makeText(
            context,
            "Restart Tachiyomi to apply new settings.",
            Toast.LENGTH_LONG,
        ).show()
    }

    companion object {
        internal const val PREF_EXTRA_SOURCES_COUNT = "EXTRA_SOURCES"
        internal const val PREF_EXTRA_SOURCES_DEFAULT = "0"
        private val PREF_EXTRA_SOURCES_ENTRIES = (0..10).map { it.toString() }.toTypedArray()

        private const val PREF_DISPLAY_NAME = "DISPLAY_NAME"
        private const val PREF_HOST_ADDRESS = "HOST_ADDRESS"
        private const val PREF_IMAGE_PRESET = "IMAGE_PRESET"
        private const val PREF_IMAGE_ORIGINAL_PRESET = "Original:"
    }
}
