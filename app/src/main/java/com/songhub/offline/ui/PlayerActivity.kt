package com.songhub.offline.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.songhub.offline.databinding.ActivityPlayerBinding
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra("SONG_TITLE") ?: "Unknown Song"
        val lyrics = intent.getStringArrayExtra("SONG_LYRICS")?.toList() ?: emptyList()
        val uriString = intent.getStringExtra("SONG_URI")

        binding.txtPlayerTitle.text = title
        binding.txtLyrics.text = lyrics.joinToString("\n")

        binding.btnPlayPause.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                binding.btnPlayPause.text = "Pause"
                Toast.makeText(this, "Playing MIDI offline...", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnPlayPause.text = "Play"
            }
        }
    }
}
