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
}
