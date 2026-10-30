package com.songhub.offline.data

import java.io.File

/**
 * Isang kanta (MIDI file) sa SD Card.
 * Kung ang filename ay nagsisimula sa numero (hal. "10234 - My Way.mid"),
 * ginagamit iyon bilang song code, parang sa karaoke machine.
 */
data class Song(
    val path: String,
    val title: String,
    val code: String?,
    val folder: String
) {
    val file: File get() = File(path)

    companion object {
        private val CODE_REGEX = Regex("^(\\d{3,6})\\s*[-_.)]?\\s*(.+)$")
        val EXTENSIONS = setOf("mid", "midi", "kar", "rmi")

        fun isMidi(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in EXTENSIONS

        fun fromFile(f: File): Song {
            val base = f.nameWithoutExtension.replace('_', ' ').trim()
            val m = CODE_REGEX.find(base)
            val code = m?.groupValues?.get(1)
            val title = m?.groupValues?.get(2)?.trim() ?: base
            return Song(f.absolutePath, title.ifEmpty { base }, code, f.parentFile?.name ?: "")
        }
    }
}

/** Kasalukuyang listahan ng tutugtugin (para sa Next / Previous). */
object PlayQueue {
    var paths: List<String> = emptyList()
        private set
    var index: Int = 0
        private set

    fun set(paths: List<String>, index: Int) {
        this.paths = paths
        this.index = index
    }
}
