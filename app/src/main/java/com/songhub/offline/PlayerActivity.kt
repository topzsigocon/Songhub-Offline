package com.songhub.offline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.songhub.offline.data.PlayQueue
import com.songhub.offline.data.Prefs
import com.songhub.offline.data.Song
import com.songhub.offline.databinding.ActivityPlayerBinding
import com.songhub.offline.player.KaraokePlayer
import com.songhub.offline.util.formatTime
import java.io.File

/**
 * Player screen: lyrics sa gitna, controls sa baba
 * (Play/Pause, Previous, Next, Key +/-, Tempo +/-, Volume).
 */
class PlayerActivity : AppCompatActivity(), KaraokePlayer.Listener {

    private lateinit var b: ActivityPlayerBinding
    private lateinit var prefs: Prefs
    private lateinit var player: KaraokePlayer
    private val handler = Handler(Looper.getMainLooper())

    private var queue: List<String> = emptyList()
    private var index = 0
    private var currentPath: String? = null
    private var currentTitle = ""
    private var userSeeking = false

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 30)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        prefs = Prefs(this)
        player = KaraokePlayer(this)
        player.listener = this
        player.changeVolume(prefs.volume / 100f)

        setupControls()

        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null) {
            loadUri(data)
        } else {
            queue = PlayQueue.paths
            if (queue.isEmpty()) {
                finish()
                return
            }
            loadIndex(PlayQueue.index, true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) loadUri(data)
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        if (::player.isInitialized) player.release()
    }

    // ---------------- Loading ----------------

    private fun loadIndex(i: Int, autoPlay: Boolean) {
        if (queue.isEmpty()) return
        index = ((i % queue.size) + queue.size) % queue.size
        val path = queue[index]
        currentPath = path
        val song = Song.fromFile(File(path))
        currentTitle = song.title
        b.toolbar.title = song.title
        b.toolbar.subtitle = listOfNotNull(song.code, song.folder.ifEmpty { null }).joinToString(" • ")
        updateFavIcon()
        b.btnFav.visibility = View.VISIBLE
        try {
            player.load(File(path).readBytes(), prefs.getKey(path), autoPlay)
        } catch (e: Exception) {
            onError(e.message ?: getString(R.string.cannot_read))
        }
    }

    /** Binuksan galing sa ibang File Manager app. */
    private fun loadUri(uri: Uri) {
        queue = emptyList()
        currentPath = if (uri.scheme == "file") uri.path else null
        currentTitle = displayName(uri)?.substringBeforeLast('.') ?: getString(R.string.app_name)
        b.toolbar.title = currentTitle
        b.toolbar.subtitle = ""
        b.btnFav.visibility = if (currentPath != null) View.VISIBLE else View.GONE
        updateFavIcon()
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException(getString(R.string.cannot_read))
            player.load(bytes, currentPath?.let { prefs.getKey(it) } ?: 0, true)
        } catch (e: Exception) {
            onError(e.message ?: getString(R.string.cannot_read))
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { uri.lastPathSegment }
    }

    // ---------------- Controls ----------------

    private fun setupControls() {
        b.btnPlay.setOnClickListener { if (player.isPlaying) player.pause() else player.play(); updatePlayIcon() }
        b.btnNext.setOnClickListener { if (queue.isNotEmpty()) loadIndex(index + 1, true) }
        b.btnPrev.setOnClickListener {
            if (player.positionMs > 3000 || queue.isEmpty()) player.seekTo(0) else loadIndex(index - 1, true)
        }
        b.btnRestart.setOnClickListener { player.seekTo(0); player.play() }
        b.btnFav.setOnClickListener {
            currentPath?.let {
                val nowFav = prefs.toggleFavorite(it)
                Toast.makeText(this, if (nowFav) R.string.added_favorite else R.string.removed_favorite, Toast.LENGTH_SHORT).show()
                updateFavIcon()
            }
        }

        // KEY (Transpose)
        b.keyDown.setOnClickListener { setKey(player.transpose - 1) }
        b.keyUp.setOnClickListener { setKey(player.transpose + 1) }
        b.keyLabel.setOnClickListener { setKey(0) }

        // TEMPO
        b.tempoDown.setOnClickListener { player.changeSpeed(player.speed - 0.05f); updateLabels() }
        b.tempoUp.setOnClickListener { player.changeSpeed(player.speed + 0.05f); updateLabels() }
        b.tempoLabel.setOnClickListener { player.changeSpeed(1.0f); updateLabels() }

        // VOLUME
        b.volume.progress = prefs.volume
        b.volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) player.changeVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { prefs.volume = sb.progress }
        })

        // SEEK
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) b.timeCurrent.text = formatTime(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                userSeeking = false
                player.seekTo(sb.progress)
            }
        })
    }

    private fun setKey(k: Int) {
        player.changeKey(k)
        currentPath?.let { prefs.setKey(it, player.transpose) }
        updateLabels()
    }

    private fun updateLabels() {
        val k = player.transpose
        b.keyLabel.text = getString(R.string.key_label, if (k > 0) "+$k" else k.toString())
        b.tempoLabel.text = getString(R.string.tempo_label, Math.round(player.speed * 100))
    }

    private fun updatePlayIcon() {
        b.btnPlay.setImageResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateFavIcon() {
        val fav = currentPath?.let { prefs.isFavorite(it) } == true
        b.btnFav.setImageResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_border)
        b.btnFav.setColorFilter(ContextCompat.getColor(this, if (fav) R.color.accent else R.color.text_primary))
    }

    private var lastPlaying: Boolean? = null

    private fun tick() {
        val pos = player.positionMs
        if (!userSeeking) {
            b.seek.progress = pos
            b.timeCurrent.text = formatTime(pos)
        }
        b.lyrics.update(pos.toLong())
        val playing = player.isPlaying
        if (playing != lastPlaying) {
            lastPlaying = playing
            updatePlayIcon()
        }
    }

    // ---------------- KaraokePlayer.Listener ----------------

    override fun onLoaded() {
        val dur = player.durationMs
        b.seek.max = dur.coerceAtLeast(1)
        b.timeTotal.text = formatTime(dur)
        b.lyrics.setLyrics(player.lyrics, player.lyrics.title ?: currentTitle)
        updateLabels()
        updatePlayIcon()
    }

    override fun onCompletion() {
        if (queue.isNotEmpty() && index < queue.size - 1) {
            loadIndex(index + 1, true)
        } else {
            player.seekTo(0)
            updatePlayIcon()
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, getString(R.string.play_error, message), Toast.LENGTH_LONG).show()
        updatePlayIcon()
    }
}
