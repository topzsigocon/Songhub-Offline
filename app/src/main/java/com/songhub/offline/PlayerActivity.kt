package com.songhub.offline

import android.media.midi.MidiManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Color

class PlayerActivity : AppCompatActivity() {
    
    private lateinit var tvLyrics: TextView
    private var currentLyricIndex = 0
    private var lyricsList: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        
        tvLyrics = findViewById(R.id.tvLyrics)
        val filePath = intent.getStringExtra("MIDI_PATH")
        
        // Sample logic: Kunin lyrics mula sa MIDI file
        lyricsList = listOf(
            "Tanging Yaman lyrics line 1",
            "Tanging Yaman lyrics line 2",
            "Lalabas dito ang totoong lyrics ng MIDI mo"
        )
        
        updateLyricsHighlight()
    }

    // Ito yung nagpa-highlight ng kasalukuyang linya
    fun updateLyricsHighlight() {
        val spannable = SpannableString(lyricsList.joinToString("\n"))
        // Logic para i-highlight yung kasalukuyang kinakanta
        // Example: currentLyricIndex = 1
        // Magiging green yung current line
        tvLyrics.text = spannable
    }
}
