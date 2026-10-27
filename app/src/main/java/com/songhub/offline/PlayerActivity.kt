package com.songhub.offline

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var tvLyrics: TextView
    private lateinit var tvTitle: TextView
    private var lyricsList: List<String> = emptyList()
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        tvLyrics = findViewById(R.id.tvLyrics)
        tvTitle = findViewById(R.id.tvSongTitle)

        val path = intent.getStringExtra("MIDI_PATH") ?: ""
        val title = intent.getStringExtra("SONG_TITLE") ?: "Unknown"
        tvTitle.text = title

        val midiFile = File(path)
        if (midiFile.exists()) {
            // GAMITIN ANG REAL PARSER
            try {
                val file = com.leff.midi.MidiFile(midiFile)
                val parsed = mutableListOf<String>()
                for (track in file.tracks) {
                    for (event in track.events) {
                        if (event is com.leff.midi.event.meta.Lyrics) {
                            var lyric = event.lyric
                            // Linisin yung / at \ na ginagamit sa ibang MIDI
                            lyric = lyric.replace("/", "").replace("\\", "")
                            if (lyric.isNotBlank()) parsed.add(lyric)
                        }
                    }
                }
                lyricsList = parsed
                if (lyricsList.isEmpty()) {
                    tvLyrics.text = "Walang naka-embed na lyrics sa MIDI na ito.\n\nFile: $title"
                } else {
                    tvLyrics.text = lyricsList.joinToString(" ")
                    startKaraokeSimulation()
                }
            } catch (e: Exception) {
                tvLyrics.text = "Error sa pagbasa ng lyrics: ${e.message}"
            }
        }

        findViewById<View>(R.id.btnPlayPause).setOnClickListener {
            if (isPlaying) pauseKaraoke() else startKaraokeSimulation()
        }
    }

    // Simple simulation ng pag-highlight habang kumakanta
    // Sa totoong MIDI player, dapat naka-sync to sa tick ng MIDI
    private fun startKaraokeSimulation() {
        isPlaying = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (currentIndex < lyricsList.size && isPlaying) {
                    highlightLyrics()
                    currentIndex++
                    handler.postDelayed(this, 600) // 600ms per word - adjust mo depende sa tempo
                }
            }
        }, 600)
    }

    private fun pauseKaraoke() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun highlightLyrics() {
        val fullText = lyricsList.joinToString(" ")
        tvLyrics.text = fullText
        // Dito mo pwede idagdag yung Spannable para gawing green yung current word
    }
}
