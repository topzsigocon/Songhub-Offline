package com.songhub.offline

import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    private val midiFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 1. Scan SD Card for MIDI
        scanMidiFiles()

        // 2. Setup Player
        // Gagamit tayo ng android.media.MediaPlayer + custom lyric parser
        // Ang lyrics ay nasa MIDI Meta Event type 0x05
    }

    private fun scanMidiFiles() {
        val sdCard = Environment.getExternalStorageDirectory()
        val songhubDir = File(sdCard, "Songhub")
        if (!songhubDir.exists()) songhubDir.mkdirs()

        // Recursive scan
        songhubDir.walkTopDown().forEach { file ->
            if (file.extension.equals("mid", true) || file.extension.equals("midi", true)) {
                midiFiles.add(file)
            }
        }
        // TODO: Display midiFiles sa RecyclerView
    }

    // Function para i-parse ang lyrics sa loob ng MIDI file
    fun parseMidiLyrics(file: File): List<String> {
        // Gagamit ng library: com.leff.midi
        // Hanapin ang MidiEvent na Lyric (FF 05)
        return listOf("Lyrics parsing logic here")
    }
}
