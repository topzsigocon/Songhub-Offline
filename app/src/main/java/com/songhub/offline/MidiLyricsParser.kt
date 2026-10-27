package com.songhub.offline

import java.io.File

data class LyricLine(val tick: Long, val text: String)

object MidiLyricsParser {

    fun parse(file: File): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        try {
            val bytes = file.readBytes()
            var i = 0
            while (i < bytes.size - 5) {
                // Hanapin ang Lyric Meta Event: FF 05
                if (bytes[i] == 0xFF.toByte() && bytes[i+1] == 0x05.toByte()) {
                    val length = bytes[i+2].toInt() and 0xFF
                    if (i + 3 + length < bytes.size) {
                        val textBytes = bytes.copyOfRange(i + 3, i + 3 + length)
                        val text = String(textBytes, Charsets.UTF_8).trim()
                        // I-filter lang yung readable lyrics
                        if (text.isNotEmpty() && text.length < 100) {
                            // Ang tick ay approximation lang, gagamitin para sa timing
                            lyrics.add(LyricLine(i.toLong(), text))
                        }
                    }
                }
                i++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Kung walang FF 05, subukan naman Text Event FF 01 (minsan dun nakalagay)
        if (lyrics.isEmpty()) {
           // fallback: balik empty, papakita na lang filename
        }
        
        return lyrics
    }
}
