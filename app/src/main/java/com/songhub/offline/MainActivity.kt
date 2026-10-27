package com.songhub.offline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val midiFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        // Para sa Android 11, kailangan ng MANAGE_ALL_FILES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            } else {
                scanAndDisplayFiles()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            } else {
                scanAndDisplayFiles()
            }
        }
    }

    private fun scanAndDisplayFiles() {
        midiFiles.clear()
        val songhubDir = File(Environment.getExternalStorageDirectory(), "Songhub")
        if (!songhubDir.exists()) {
            songhubDir.mkdirs()
            Toast.makeText(this, "Gumawa ng folder: /SDCard/Songhub/ - Lagay mo MIDI files mo dyan", Toast.LENGTH_LONG).show()
        }

        // Scan lahat ng .mid
        songhubDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension.equals("mid", true) || file.extension.equals("midi", true))) {
                midiFiles.add(file)
            }
        }

        if (midiFiles.isEmpty()) {
            Toast.makeText(this, "Walang MIDI file sa /Songhub/", Toast.LENGTH_SHORT).show()
        }

        // Ilagay sa RecyclerView
        recyclerView.adapter = SongAdapter(midiFiles) { file ->
            // Pag pinindot ang kanta, punta sa Player
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("MIDI_PATH", file.absolutePath)
            intent.putExtra("SONG_TITLE", file.name)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // I-scan ulit pag bumalik sa app
        if (Environment.isExternalStorageManager()) {
            scanAndDisplayFiles()
        }
    }
}
