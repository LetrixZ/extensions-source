package eu.kanade.tachiyomi.extension.all.faccina

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

object FaccinaHelper {
    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }

    fun getIdFromUrl(url: String) = url.split("/")[2]

    fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    fun generateFilename(title: String, tags: List<Tag>): String {
        val artists = tags.filter { it.namespace == "artist" }
        val circles = tags.filter { it.namespace == "circle" }
        val magazines = tags.filter { it.namespace == "magazine" }

        val splits = mutableListOf<String>()

        if (circles.isEmpty()) {
            if (artists.size == 1) {
                splits.add("[${artists[0].name()}]")
            } else if (artists.size == 2) {
                splits.add("[${artists[0].name()} & ${artists[1].name()}]")
            } else if (artists.size > 2) {
                splits.add("[Various]")
            }
        } else if (circles.size == 1) {
            if (artists.size == 1) {
                splits.add("[${circles[0].name()} (${artists[0].name()})]")
            } else if (artists.size == 2) {
                splits.add("[${circles[0].name()} (${artists[0].name()} & ${artists[1].name()})]")
            } else {
                splits.add("[${circles[0].name()}]")
            }
        } else {
            splits.add("[Various]")
        }

        splits.add(title)

        if (magazines.size == 1) {
            splits.add("(${magazines[0].name()})")
        }

        return splits.joinToString(" ")
    }
}
