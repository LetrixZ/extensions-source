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
    private val artists: List<String> = emptyList(),
    private val circles: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/g/$id"
        title = this@Archive.title
        description = this@Archive.description
        thumbnail_url = "$baseUrl/image/$hash/$thumbnail?type=cover"
        artist = this@Archive.artists.joinToString(", ").ifEmpty { null }
        author = this@Archive.circles.joinToString(", ").ifEmpty { null }
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
    val archives: List<ArchiveLibrary> = emptyList(),
    val total: Int,
    val page: Int,
    val limit: Int,
)

@Serializable
class ArchiveLibrary(
    private val id: Int,
    private val hash: String,
    private val title: String,
    private val thumbnail: Int,
    private val artists: List<String> = emptyList(),
    private val circles: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/g/$id"
        title = this@ArchiveLibrary.title
        thumbnail_url = "$baseUrl/image/$hash/$thumbnail?type=cover"
        artist = this@ArchiveLibrary.artists.joinToString(", ").ifEmpty { null }
        author = this@ArchiveLibrary.circles.joinToString(", ").ifEmpty { null }
        genre = this@ArchiveLibrary.tags.joinToString(", ")
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED
    }
}
