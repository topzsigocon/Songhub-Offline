package com.songhub.offline.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.songhub.offline.midi.KaraokeLyrics
import com.songhub.offline.midi.LyricsExtractor
import com.songhub.offline.midi.MidiFile
import com.songhub.offline.midi.MidiTransformer
import com.songhub.offline.midi.TempoMap
import java.io.File
import java.io.FileInputStream

/**
 * Karaoke MIDI player gamit ang built-in Android MIDI synthesizer (MediaPlayer).
 * 100% offline - walang kailangang internet o external soundfont.
 *
 * Kapag pinalitan ang Key o Tempo, gumagawa ng bagong MIDI file sa cache
 * at itinutuloy ang tugtog sa parehong posisyon (tick-accurate).
 */
class KaraokePlayer(context: Context) {

    interface Listener {
        /** Tinatawag tuwing bagong MIDI ang na-load (bagong kanta, bagong key o tempo). */
        fun onLoaded()
        fun onCompletion()
        fun onError(message: String)
    }

    companion object {
        const val MIN_KEY = -12
        const val MAX_KEY = 12
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.5f
    }

    private val appContext = context.applicationContext
    private var source: MidiFile? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempoMap: TempoMap = TempoMap.DEFAULT

    var listener: Listener? = null

    var lyrics: KaraokeLyrics = KaraokeLyrics.EMPTY
        private set
    var transpose = 0
        private set
    var speed = 1.0f
        private set
    var volume = 0.8f
        private set

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (e: IllegalStateException) { false }

    val positionMs: Int
        get() = try { mediaPlayer?.currentPosition ?: 0 } catch (e: IllegalStateException) { 0 }

    val durationMs: Int
        get() = try { mediaPlayer?.duration ?: 0 } catch (e: IllegalStateException) { 0 }

    /** I-load ang MIDI bytes. Ang key ay pwedeng i-restore mula sa naka-save na setting. */
    fun load(bytes: ByteArray, initialKey: Int, autoPlay: Boolean) {
        try {
            source = MidiFile.parse(bytes)
        } catch (e: Exception) {
            source = null
            releasePlayer()
            listener?.onError(e.message ?: "Hindi mabasa ang MIDI file")
            return
        }
        transpose = initialKey.coerceIn(MIN_KEY, MAX_KEY)
        speed = 1.0f
        rebuild(0L, autoPlay)
    }

    fun play() {
        try { mediaPlayer?.start() } catch (e: IllegalStateException) { }
    }

    fun pause() {
        try { if (isPlaying) mediaPlayer?.pause() } catch (e: IllegalStateException) { }
    }

    fun seekTo(ms: Int) {
        try { mediaPlayer?.seekTo(ms.coerceAtLeast(0)) } catch (e: IllegalStateException) { }
    }

    fun changeKey(newKey: Int) {
        val k = newKey.coerceIn(MIN_KEY, MAX_KEY)
        if (k == transpose) return
        transpose = k
        rebuildKeepingPosition()
    }

    fun changeSpeed(newSpeed: Float) {
        val s = (Math.round(newSpeed * 100f) / 100f).coerceIn(MIN_SPEED, MAX_SPEED)
        if (s == speed) return
        speed = s
        rebuildKeepingPosition()
    }

    fun changeVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        try { mediaPlayer?.setVolume(volume, volume) } catch (e: IllegalStateException) { }
    }

    private fun rebuildKeepingPosition() {
        val wasPlaying = isPlaying
        val tick = tempoMap.msToTick(positionMs.toDouble())
        rebuild(tick, wasPlaying)
    }

    private fun rebuild(startTick: Long, autoPlay: Boolean) {
        val src = source ?: return
        try {
            val midi = MidiTransformer.transform(src, transpose, speed)
            tempoMap = TempoMap.build(midi)
            lyrics = LyricsExtractor.extract(midi, tempoMap)

            releasePlayer()
            val out = File(appContext.cacheDir, "songhub_now_playing.mid")
            out.writeBytes(midi.toBytes())

            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            FileInputStream(out).use { mp.setDataSource(it.fd) }
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { listener?.onCompletion() }
            mp.setOnErrorListener { _, what, extra ->
                listener?.onError("Player error ($what / $extra)")
                true
            }
            mp.prepare()
            val startMs = tempoMap.tickToMs(startTick).toInt()
            if (startMs > 0) mp.seekTo(startMs)
            if (autoPlay) mp.start()
            mediaPlayer = mp
            listener?.onLoaded()
        } catch (e: Exception) {
            listener?.onError(e.message ?: e.toString())
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.release() } catch (e: Exception) { }
        mediaPlayer = null
    }

    fun release() {
        releasePlayer()
        listener = null
    }
}
