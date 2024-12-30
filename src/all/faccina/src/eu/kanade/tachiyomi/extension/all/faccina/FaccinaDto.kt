package eu.kanade.tachiyomi.extension.all.faccina

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable

@Serializable
class Archive(
    private val id: Int,
    private val hash: String,
    private val title: String,
    private val description: String? = null,
    private val pages: Int,
    private val thumbnail: Int,
    private val tags: List<Tag> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/g/$id"
        title = this@Archive.title
        description = this@Archive.description
        thumbnail_url = "$baseUrl/image/$hash/$thumbnail?type=cover"
        artist = Tag.artists(this@Archive.tags).ifEmpty { null }
        author = Tag.circles(this@Archive.tags).ifEmpty { null }
        genre = this@Archive.tags.joinToString(", ")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    fun toPageList(baseUrl: String) = (1..pages).map { page ->
        Page(
            index = page - 1,
            url = "/g/$id/read/$page",
            imageUrl = "$baseUrl/image/$hash/$page",
        )
    }
}

@Serializable
class LibraryResponse(
    val archives: List<Archive> = emptyList(),
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
