package eu.kanade.tachiyomi.extension.all.faccina

import eu.kanade.tachiyomi.extension.all.faccina.FaccinaHelper.getCdnUrl
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Archive(
    private val id: Int,
    private val hash: String,
    private val title: String,
    private val description: String? = null,
    @SerialName("thumbnail_url")
    private val thumbnailUrl: String,
    private val images: List<Image>,
    private val artists: List<Taxonomy> = emptyList(),
    private val circles: List<Taxonomy> = emptyList(),
    private val tags: List<Taxonomy> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$id"
        title = this@Archive.title
        description = this@Archive.description
        thumbnail_url = this@Archive.thumbnailUrl
        artist = this@Archive.artists.joinToString(", ") { it.name }.ifEmpty { null }
        author = this@Archive.circles.joinToString(", ") { it.name }.ifEmpty { null }
        genre = this@Archive.tags.joinToString(", ") { it.name }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    fun toPageList() = images.mapIndexed { i, image ->
        Page(
            index = i,
            url = "/g/$id/read/${image.page_number}",
            imageUrl = "${getCdnUrl(thumbnailUrl)}/image/$hash/${image.filename}",
        )
    }
}

@Serializable
class Taxonomy(
    var name: String,
)

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
    private val title: String,
    @SerialName("thumbnail_url")
    private val thumbnailUrl: String,
    private val artists: List<Taxonomy> = emptyList(),
    private val circles: List<Taxonomy> = emptyList(),
    private val tags: List<Taxonomy> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$id"
        title = this@ArchiveLibrary.title
        thumbnail_url = this@ArchiveLibrary.thumbnailUrl
        artist = this@ArchiveLibrary.artists.joinToString(", ") { it.name }.ifEmpty { null }
        author = this@ArchiveLibrary.circles.joinToString(", ") { it.name }.ifEmpty { null }
        genre = this@ArchiveLibrary.tags.joinToString(", ") { it.name }
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED
    }
}

@Serializable
class Image(
    val filename: String,
    val page_number: Int,
)
