package eu.kanade.tachiyomi.extension.all.faccina

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class FaccinaFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val firstInstance = Faccina("")
        val count = firstInstance.preferences
            .getString(Faccina.PREF_EXTRA_SOURCES_COUNT, Faccina.PREF_EXTRA_SOURCES_DEFAULT)!!
            .toInt()

        return buildList(count) {
            add(firstInstance)

            for (i in 0 until count) {
                add(Faccina("${i + 2}"))
            }
        }
    }
}
