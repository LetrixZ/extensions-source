package eu.kanade.tachiyomi.extension.all.faccina

import eu.kanade.tachiyomi.extension.all.faccina.FaccinaHelper.parseDate
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

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
    val pages: Int,
    override val thumbnail: Int,
    override val tags: List<Tag> = emptyList(),
    val createdAt: String?,
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
    val chapters: List<SeriesChapter>,
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
    private val hash: String,
    private val title: String,
    val number: Int,
    private val pages: Int,
    private val createdAt: String,
) {
    fun toPageList(baseUrl: String) = (1..pages).map { page ->
        Page(
            index = page - 1,
            url = "/g/$id/read/$page",
            imageUrl = "$baseUrl/image/$hash/$page",
        )
    }

    fun toSChapter() = SChapter.create().apply {
        url = "/g/$id/read"
        name = "$number. $title"
        chapter_number = number.toFloat()
        date_upload = parseDate(createdAt)
    }
}

@Serializable
class LibraryResponse(
    val data: List<Base> = emptyList(),
    val total: Int,
    val page: Int,
    val limit: Int,
)

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
