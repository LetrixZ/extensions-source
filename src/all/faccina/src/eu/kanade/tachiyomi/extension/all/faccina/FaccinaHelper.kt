package eu.kanade.tachiyomi.extension.all.faccina

import okhttp3.HttpUrl.Companion.toHttpUrl

object FaccinaHelper {
    fun getIdFromUrl(url: String) = url.split("/")[2]

    fun getCdnUrl(str: String): String {
        val url = str.toHttpUrl().toUrl()

        return "${url.protocol}://${url.host}"
    }
}
