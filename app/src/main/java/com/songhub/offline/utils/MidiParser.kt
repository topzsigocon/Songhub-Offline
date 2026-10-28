package com.songhub.offline.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.songhub.offline.data.MidiFile
import java.io.InputStream

object MidiParser {

    /**
     * Scans a selected folder URI for .mid and .midi files
     */
    fun scanFolderForMidi(context: Context, folderUri: Uri): List<MidiFile> {
        val midiList = mutableListOf<MidiFile>()
        val rootDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return midiList

        for (file in rootDoc.listFiles()) {
            if (file.isFile && file.name != null) {
                val name = file.name!!.lowercase()
                if (name.endsWith(".mid") || name.endsWith(".midi")) {
                    val title = file.name!!.substringBeforeLast(".")
                    val lyrics = parseLyrics(context, file.uri)
                    midiList.add(MidiFile(title, file.uri, lyrics))
                }
            }
        }
        return midiList
    }

    /**
     * Basic MIDI meta event parser for Lyric events (FF 05)
     */
    private fun parseLyrics(context: Context, uri: Uri): List<String> {
        val lyrics = mutableListOf<String>()
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            val bytes = inputStream.readBytes()
            inputStream.close()

            // Simple byte scan for Meta Event Type 0x05 (Lyric)
            var i = 0
            while (i < bytes.size - 2) {
                if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0x05.toByte()) {
                    val length = bytes[i + 2].toInt() and 0xFF
                    if (i + 3 + length <= bytes.size) {
                        val lyricText = String(bytes, i + 3, length, Charsets.UTF_8)
                        lyrics.add(lyricText)
                    }
                    i += 3 + length
                } else {
                    i++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return if (lyrics.isEmpty()) listOf("No embedded lyrics found in this MIDI file.") else lyrics
    }
}
