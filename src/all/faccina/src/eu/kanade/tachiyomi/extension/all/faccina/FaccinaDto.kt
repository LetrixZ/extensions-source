package eu.kanade.tachiyomi.extension.all.faccina

import eu.kanade.tachiyomi.extension.all.faccina.FaccinaHelper.parseDate
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = BaseSerializer::class)
sealed class Base() {
    abstract val id: Int
    abstract val hash: String
    abstract val title: String
    abstract val description: String?
    abstract val thumbnail: Int
    abstract val tags: List<Tag>

    abstract fun toSManga(baseUrl: String): SManga
    abstract fun toSChapterList(): List<SChapter>
}

object BaseSerializer : JsonContentPolymorphicSerializer<Base>(Base::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Base> {
        return if ("chapters" in element.jsonObject) {
            Series.serializer()
        } else {
            Archive.serializer()
        }
    }
}

@Serializable
data class Archive(
    override val id: Int,
    override val hash: String,
    override val title: String,
    override val description: String? = null,
    private val pages: Int,
    override val thumbnail: Int,
    override val tags: List<Tag> = emptyList(),
    private val createdAt: String?,
) : Base() {
    override fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/g/$id"
        title = this@Archive.title
        description = buildString {
            appendLine("Pages: ${this@Archive.pages}")
            if (this@Archive.description != null) {
                appendLine()
                appendLine(this@Archive.description)
            }
        }
        thumbnail_url = "$baseUrl/image/$hash/$thumbnail?type=cover"
        artist = Tag.artists(this@Archive.tags).ifEmpty { null }
        author = Tag.circles(this@Archive.tags).ifEmpty { null }
        genre = this@Archive.tags.joinToString(", ")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    override fun toSChapterList() = listOf(
        SChapter.create().apply {
            url = "/g/$id/read"
            name = "1. Chapter"
            chapter_number = 1f
            date_upload = createdAt?.let { parseDate(it) } ?: 0
        },
    )

    fun toPageList(baseUrl: String) = (1..pages).map { page ->
        Page(
            index = page - 1,
            url = "/g/$id/read/$page",
            imageUrl = "$baseUrl/image/$hash/$page",
        )
    }
}

@Serializable
class Series(
    override val id: Int,
    override val hash: String,
    override val title: String,
    override val description: String? = null,
    override val thumbnail: Int,
    override val tags: List<Tag> = emptyList(),
    private val chapters: List<SeriesChapter>,
) : Base() {
    override fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/s/$id"
        title = this@Series.title
        description = this@Series.description
        thumbnail_url = "$baseUrl/image/$hash/$thumbnail?type=cover"
        artist = Tag.artists(this@Series.tags).ifEmpty { null }
        author = Tag.circles(this@Series.tags).ifEmpty { null }
        genre = this@Series.tags.joinToString(", ")
        status = SManga.UNKNOWN
        update_strategy = UpdateStrategy.ALWAYS_UPDATE
        initialized = true
    }

    override fun toSChapterList() = chapters.map { it -> it.toSChapter() }
}

@Serializable
class SeriesChapter(
    private val id: Int,
    private val title: String,
    private val number: Int,
    private val createdAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/g/$id/read"
        name = "$number. $title"
        chapter_number = number.toFloat()
        date_upload = parseDate(createdAt)
    }
}

@Serializable(with = LibraryResponseSerializer::class)
data class LibraryResponse(
    val archives: List<Archive>? = null,
    val series: List<Series>? = null,
    val total: Int,
    val page: Int,
    val limit: Int,
)

object LibraryResponseSerializer : KSerializer<LibraryResponse> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LibraryResponse") {
        element<List<Archive>?>("archives", isOptional = true)
        element<List<Series>?>("series", isOptional = true)
        element<Int>("total")
        element<Int>("page")
        element<Int>("limit")
    }

    override fun deserialize(decoder: Decoder): LibraryResponse {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")

        val jsonObject = input.decodeJsonElement().jsonObject

        val json = Json {
            ignoreUnknownKeys = true
        }

        val archives = jsonObject["archives"]?.let {
            json.decodeFromJsonElement(ListSerializer(Archive.serializer()), it)
        }

        val series = jsonObject["series"]?.let {
            json.decodeFromJsonElement(ListSerializer(Series.serializer()), it)
        }

        return LibraryResponse(
            archives = archives,
            series = series,
            total = jsonObject["total"]?.jsonPrimitive?.int ?: 0,
            page = jsonObject["page"]?.jsonPrimitive?.int ?: 0,
            limit = jsonObject["limit"]?.jsonPrimitive?.int ?: 0,
        )
    }

    override fun serialize(encoder: Encoder, value: LibraryResponse) {
        throw NotImplementedError()
    }
}

@Serializable
class Tag(
    val namespace: String,
    private val name: String,
    private val displayName: String? = null,
) {
    override fun toString(): String =
        if (this.displayName !== null) "${this.namespace}:${this.displayName}" else "${this.namespace}:${this.name}"

    fun name(): String = if (this.displayName !== null) this.displayName else this.name

    companion object {
        fun artists(tags: List<Tag>): String {
            var artists = tags.filter { it.namespace == "artist" }

            if (artists.isEmpty()) {
                artists = tags.filter { it.namespace == "circle" }
            }

            return artists.joinToString(", ") { it.name() }
        }

        fun circles(tags: List<Tag>): String {
            var circles = tags.filter { it.namespace == "circle" }

            if (circles.isEmpty()) {
                circles = tags.filter { it.namespace == "artist" }
            }

            return circles.joinToString(", ") { it.name() }
        }
    }
}

@Serializable
class ServerConfig(
    val reader: Reader,
)

@Serializable
class Reader(
    val presets: List<ReaderPreset>,
    val defaultPreset: ReaderPreset?,
    val allowOriginal: Boolean,
)

@Serializable
class ReaderPreset(
    val label: String,
    val hash: String,
) {
    fun toValue() = "${this.label}:${this.hash}"
}
