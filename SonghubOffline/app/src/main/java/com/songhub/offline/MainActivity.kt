package com.songhub.offline

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.songhub.offline.data.PlayQueue
import com.songhub.offline.data.Prefs
import com.songhub.offline.data.Song
import com.songhub.offline.data.SongScanner
import com.songhub.offline.databinding.ActivityMainBinding
import com.songhub.offline.ui.PlaylistAdapter
import com.songhub.offline.ui.SongAdapter
import com.songhub.offline.util.StoragePermission
import java.io.File
import java.util.concurrent.Executors

/**
 * Main screen: Song List (may search), Favorites, at Playlists.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAB_ALL = 0
        private const val TAB_FAV = 1
        private const val TAB_PLAYLISTS = 2
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var songAdapter: SongAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private val executor = Executors.newSingleThreadExecutor()

    private var allSongs: List<Song> = emptyList()
    private var currentTab = TAB_ALL
    private var query = ""
    private var scanning = false
    private var scannedOnce = false

    /** Resulta ng built-in folder picker. */
    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val path = res.data?.getStringExtra(FolderPickerActivity.EXTRA_PATH)
        if (res.resultCode == RESULT_OK && path != null) {
            prefs.customFolders = prefs.customFolders + path
            toast(getString(R.string.folder_added, path))
            startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        prefs = Prefs(this)

        songAdapter = SongAdapter(
            prefs,
            onClick = { list, index -> openPlayer(list, index) },
            onMore = { song, anchor -> showSongMenu(song, anchor) },
            onFavoriteChanged = { if (currentTab == TAB_FAV) refreshList() }
        )
        playlistAdapter = PlaylistAdapter(
            onOpen = { name ->
                startActivity(Intent(this, PlaylistActivity::class.java).putExtra(PlaylistActivity.EXTRA_NAME, name))
            },
            onCreate = { promptNewPlaylist(null) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = songAdapter

        listOf(R.string.tab_all, R.string.tab_favorites, R.string.tab_playlists).forEach {
            b.tabs.addTab(b.tabs.newTab().setText(it))
        }
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                refreshList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        b.search.doAfterTextChanged {
            query = it?.toString()?.trim() ?: ""
            refreshList()
        }
        b.btnPermission.setOnClickListener { StoragePermission.request(this) }
        b.btnPickFolder.setOnClickListener { openFolderPicker() }

        // Ipakita agad ang huling listahan habang nag-i-scan ulit
        allSongs = prefs.cachedSongs.map { Song.fromFile(File(it)) }
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        val granted = StoragePermission.has(this)
        b.permissionView.visibility = if (granted) View.GONE else View.VISIBLE
        if (granted && !scannedOnce) startScan() else refreshList()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == StoragePermission.REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            b.permissionView.visibility = View.GONE
            startScan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    // ---------------- Menu ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_scan_whole)?.isChecked = prefs.scanWholeSd
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_rescan -> startScan()
            R.id.action_pick_folder -> openFolderPicker()
            R.id.action_manage_folders -> showFolders()
            R.id.action_scan_whole -> {
                prefs.scanWholeSd = !prefs.scanWholeSd
                item.isChecked = prefs.scanWholeSd
                startScan()
            }
            R.id.action_about -> AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // ---------------- Scanning ----------------

    private fun startScan() {
        if (scanning || !StoragePermission.has(this)) return
        scanning = true
        scannedOnce = true
        b.progress.visibility = View.VISIBLE
        b.status.text = getString(R.string.scanning)
        val ctx = applicationContext
        executor.execute {
            val songs = try { SongScanner.scan(ctx, prefs) } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                scanning = false
                allSongs = songs
                prefs.cachedSongs = songs.map { it.path }
                b.progress.visibility = View.GONE
                refreshList()
            }
        }
    }

    private fun refreshList() {
        val q = query.lowercase()
        if (currentTab == TAB_PLAYLISTS) {
            if (b.recycler.adapter !== playlistAdapter) b.recycler.adapter = playlistAdapter
            val pl = prefs.playlists().filter { q.isEmpty() || it.key.lowercase().contains(q) }
            playlistAdapter.submit(pl.map { it.key to it.value.size })
            b.emptyView.visibility = View.GONE
            b.status.text = getString(R.string.playlist_count, pl.size)
            return
        }

        var list = if (currentTab == TAB_FAV) {
            prefs.favorites.map { Song.fromFile(File(it)) }
                .filter { it.file.exists() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        } else allSongs
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        (it.code?.contains(q) == true) ||
                        it.folder.lowercase().contains(q)
            }
        }
        if (b.recycler.adapter !== songAdapter) b.recycler.adapter = songAdapter
        songAdapter.submit(list)

        val showEmpty = list.isEmpty() && !scanning && StoragePermission.has(this)
        b.emptyView.visibility = if (showEmpty) View.VISIBLE else View.GONE
        b.emptyText.setText(
            when {
                q.isNotEmpty() -> R.string.empty_search
                currentTab == TAB_FAV -> R.string.empty_favorites
                else -> R.string.empty_songs
            }
        )
        b.btnPickFolder.visibility = if (currentTab == TAB_ALL && q.isEmpty()) View.VISIBLE else View.GONE
        if (!scanning) b.status.text = getString(R.string.song_count, list.size)
    }

    // ---------------- Actions ----------------

    private fun openPlayer(list: List<Song>, index: Int) {
        PlayQueue.set(list.map { it.path }, index)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun openFolderPicker() {
        if (!StoragePermission.has(this)) {
            StoragePermission.request(this)
            return
        }
        folderPicker.launch(Intent(this, FolderPickerActivity::class.java))
    }

    /** Ipakita ang mga folder na sina-scan; pwedeng alisin ang custom folders. */
    private fun showFolders() {
        val auto = SongScanner.detectSonghubFolders(this).map { it.absolutePath }
        val custom = prefs.customFolders.sorted()
        val items = auto.map { "★ $it  (auto)" } + custom
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.menu_manage_folders)
                .setMessage(R.string.no_folders)
                .setPositiveButton(R.string.menu_pick_folder) { _, _ -> openFolderPicker() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_manage_folders)
            .setItems(items.toTypedArray()) { _, which ->
                if (which < auto.size) {
                    toast(getString(R.string.auto_folder_info))
                } else {
                    val path = custom[which - auto.size]
                    AlertDialog.Builder(this)
                        .setMessage(getString(R.string.remove_folder_q, path))
                        .setPositiveButton(R.string.remove) { _, _ ->
                            prefs.customFolders = prefs.customFolders - path
                            startScan()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .setPositiveButton(R.string.add_folder) { _, _ -> openFolderPicker() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSongMenu(song: Song, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val fav = prefs.isFavorite(song.path)
        popup.menu.add(0, 1, 0, if (fav) R.string.remove_favorite else R.string.add_favorite)
        popup.menu.add(0, 2, 1, R.string.add_to_playlist)
        popup.menu.add(0, 3, 2, R.string.details)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> { prefs.toggleFavorite(song.path); refreshList() }
                2 -> chooseplaylist(song)
                3 -> AlertDialog.Builder(this)
                    .setTitle(song.title)
                    .setMessage(song.path)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            true
        }
        popup.show()
    }

    private fun chooseplaylist(song: Song) {
        val names = prefs.playlists().keys.toList()
        val items = names + getString(R.string.new_playlist)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_to_playlist)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == names.size) promptNewPlaylist(song.path)
                else {
                    val added = prefs.addToPlaylist(names[which], song.path)
                    toast(getString(if (added) R.string.added_to else R.string.already_in, names[which]))
                }
            }
            .show()
    }

    private fun promptNewPlaylist(songPath: String?) {
        val input = EditText(this).apply { hint = getString(R.string.playlist_name) }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_playlist)
            .setView(container)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (prefs.createPlaylist(name)) {
                    if (songPath != null) prefs.addToPlaylist(name, songPath)
                    toast(getString(R.string.playlist_created, name))
                    refreshList()
                } else toast(getString(R.string.playlist_exists))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
