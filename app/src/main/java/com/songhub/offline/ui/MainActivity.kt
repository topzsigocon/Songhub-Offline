package com.songhub.offline.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.songhub.offline.data.MidiFile
import com.songhub.offline.databinding.ActivityMainBinding
import com.songhub.offline.utils.MidiParser

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private var songList = listOf<MidiFile>()

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            loadSongsFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(songList) { song ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("SONG_TITLE", song.title)
                putExtra("SONG_LYRICS", song.lyrics.toTypedArray())
                putExtra("SONG_URI", song.uri.toString())
            }
            startActivity(intent)
        }
        binding.recyclerViewSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSongs.adapter = songAdapter
    }

    private fun loadSongsFromUri(uri: Uri) {
        songList = MidiParser.scanFolderForMidi(this, uri)
        songAdapter.updateList(songList)
        Toast.makeText(this, "Loaded ${songList.size} MIDI files.", Toast.LENGTH_SHORT).show()
    }
}
