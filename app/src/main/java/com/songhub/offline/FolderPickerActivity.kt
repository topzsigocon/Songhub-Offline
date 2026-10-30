package com.songhub.offline

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.songhub.offline.data.Song
import com.songhub.offline.databinding.ActivityFolderPickerBinding
import com.songhub.offline.ui.FolderAdapter
import com.songhub.offline.ui.FolderEntry
import com.songhub.offline.util.StorageRoot
import com.songhub.offline.util.StorageUtils
import java.io.File
import java.util.concurrent.Executors

/**
 * BUILT-IN FOLDER PICKER
 * Pumili ng folder sa SD Card (o internal storage) na may mga MIDI file.
 * Hindi kailangan ng ibang File Manager app.
 */
class FolderPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "picked_path"
    }

    private lateinit var b: ActivityFolderPickerBinding
    private lateinit var adapter: FolderAdapter
    private val executor = Executors.newSingleThreadExecutor()

    private var roots: List<StorageRoot> = emptyList()
    /** null = nasa listahan ng storage (Internal / SD Card) */
    private var current: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFolderPickerBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { goUp() }

        adapter = FolderAdapter { entry ->
            if (entry.kind != FolderEntry.KIND_MIDI) open(entry.file)
        }
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.btnSelect.setOnClickListener {
            current?.let {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, it.absolutePath))
                finish()
            }
        }
        b.btnCancel.setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goUp()
        })

        roots = StorageUtils.roots(this)
        // Kung may SD card, buksan agad ang listahan ng storage
        open(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun goUp() {
        val c = current
        if (c == null) {
            finish()
            return
        }
        val isRoot = roots.any { it.dir.absolutePath == c.absolutePath }
        open(if (isRoot) null else c.parentFile)
    }

    private fun open(dir: File?) {
        current = dir
        if (dir == null) {
            b.toolbar.title = getString(R.string.picker_title)
            b.toolbar.subtitle = getString(R.string.picker_choose_storage)
            b.pathInfo.text = getString(R.string.picker_hint)
            b.btnSelect.isEnabled = false
            adapter.submit(roots.map {
                FolderEntry(
                    it.dir, it.label, it.dir.absolutePath,
                    if (it.removable) FolderEntry.KIND_SD else FolderEntry.KIND_INTERNAL
                )
            })
            return
        }
        b.toolbar.title = dir.name
        b.toolbar.subtitle = dir.absolutePath
        b.btnSelect.isEnabled = true
        b.progress.visibility = View.VISIBLE
        executor.execute {
            val children = dir.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            val dirs = children.filter { it.isDirectory }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            val midis = children.filter { it.isFile && Song.isMidi(it.name) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            val entries = dirs.map { FolderEntry(it, it.name, "", FolderEntry.KIND_DIR) } +
                    midis.map { FolderEntry(it, it.name, "", FolderEntry.KIND_MIDI) }
            runOnUiThread {
                if (isFinishing || current != dir) return@runOnUiThread
                b.progress.visibility = View.GONE
                b.pathInfo.text = getString(R.string.picker_info, dirs.size, midis.size)
                adapter.submit(entries)
                b.recycler.scrollToPosition(0)
            }
        }
    }
}
