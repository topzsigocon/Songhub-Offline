package com.songhub.offline

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.songhub.offline.data.PlayQueue
import com.songhub.offline.data.Prefs
import com.songhub.offline.data.Song
import com.songhub.offline.databinding.ActivityPlaylistBinding
import com.songhub.offline.ui.SongAdapter
import java.io.File

/** Laman ng isang playlist. */
class PlaylistActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAME = "playlist_name"
    }

    private lateinit var b: ActivityPlaylistBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: SongAdapter
    private var name = ""
    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        prefs = Prefs(this)
        name = intent.getStringExtra(EXTRA_NAME) ?: run { finish(); return }

        adapter = SongAdapter(
            prefs,
            onClick = { list, i -> play(list, i) },
            onMore = { song, anchor ->
                PopupMenu(this, anchor).apply {
                    menu.add(0, 1, 0, R.string.remove_from_playlist)
                    setOnMenuItemClickListener {
                        prefs.removeFromPlaylist(name, song.path)
                        refresh()
                        true
                    }
                    show()
                }
            },
            onFavoriteChanged = {}
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.btnPlayAll.setOnClickListener { if (songs.isNotEmpty()) play(songs, 0) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        b.toolbar.title = name
        songs = (prefs.playlists()[name] ?: emptyList<String>())
            .map { Song.fromFile(File(it)) }
            .filter { it.file.exists() }
        b.toolbar.subtitle = getString(R.string.song_count, songs.size)
        adapter.submit(songs)
        b.emptyText.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        b.btnPlayAll.isEnabled = songs.isNotEmpty()
    }

    private fun play(list: List<Song>, index: Int) {
        PlayQueue.set(list.map { it.path }, index)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.rename)
        menu.add(0, 2, 1, R.string.delete_playlist)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> rename()
            2 -> AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_playlist_q, name))
                .setPositiveButton(R.string.delete_playlist) { _, _ ->
                    prefs.deletePlaylist(name)
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun rename() {
        val input = EditText(this).apply { setText(name) }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (prefs.renamePlaylist(name, newName)) {
                    name = newName
                    refresh()
                } else Toast.makeText(this, R.string.playlist_exists, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
