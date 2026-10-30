package com.songhub.offline.midi

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Isang pantig (syllable) ng lyrics at kung kailan ito kakantahin. */
data class Syllable(val timeMs: Long, val text: String)

/** Isang linya ng lyrics na binubuo ng mga syllable. */
class LyricLine(val syllables: List<Syllable>) {
    val text: String = syllables.joinToString("") { it.text }
}

class KaraokeLyrics(val lines: List<LyricLine>, val title: String?) {
    val hasLyrics: Boolean get() = lines.isNotEmpty()

    companion object {
        val EMPTY = KaraokeLyrics(emptyList(), null)
    }
}

/**
 * Kinukuha ang built-in karaoke lyrics mula sa MIDI file.
 *
 * Sinusuportahan:
 *  1. Lyric Meta Event (FF 05) - standard na lyrics
 *  2. Text Meta Event (FF 01) - .KAR format (Soft Karaoke), kung saan:
 *       "\" = bagong paragraph, "/" = bagong linya, "@" = header info (@T = title)
 *  Pareho ring kinikilala ang "\r" at "\n" bilang line break.
 */
object LyricsExtractor {

    private const val MIN_EVENTS = 4
    private const val MAX_LINE_CHARS = 34
    private const val GAP_BREAK_MS = 5000L

    private val WIN1252: Charset = try {
        Charset.forName("windows-1252")
    } catch (e: Exception) {
        Charsets.ISO_8859_1
    }

    fun extract(midi: MidiFile, tempo: TempoMap): KaraokeLyrics {
        val title = findKarTitle(midi)

        // 1) Hanapin ang track na may pinakamaraming FF 05 lyric events
        val lyricTrack = midi.tracks.maxByOrNull { countEvents(it, MidiFile.META_LYRIC, false) }
        val type: Int
        val track: MidiTrack
        if (lyricTrack != null && countEvents(lyricTrack, MidiFile.META_LYRIC, false) >= MIN_EVENTS) {
            track = lyricTrack
            type = MidiFile.META_LYRIC
        } else {
            // 2) Fallback: .KAR style text events (FF 01)
            val textTrack = midi.tracks.maxByOrNull { countEvents(it, MidiFile.META_TEXT, true) }
            if (textTrack == null || countEvents(textTrack, MidiFile.META_TEXT, true) < MIN_EVENTS) {
                return KaraokeLyrics(emptyList(), title)
            }
            track = textTrack
            type = MidiFile.META_TEXT
        }

        val builder = LineBuilder()
        for (e in track.events) {
            if (e !is MidiEvent.Meta || e.type != type) continue
            val text = decode(e.data)
            if (type == MidiFile.META_TEXT && isKarHeader(text)) continue
            builder.add(tempo.tickToMs(e.tick).toLong(), text)
        }
        return KaraokeLyrics(builder.finish(), title)
    }

    private fun countEvents(track: MidiTrack, type: Int, skipHeaders: Boolean): Int =
        track.events.count {
            it is MidiEvent.Meta && it.type == type && it.data.isNotEmpty() &&
                    (!skipHeaders || !isKarHeader(decode(it.data)))
        }

    private fun isKarHeader(text: String) = text.startsWith("@") || text.startsWith("%")

    private fun findKarTitle(midi: MidiFile): String? {
        for (t in midi.tracks) for (e in t.events) {
            if (e is MidiEvent.Meta && e.type == MidiFile.META_TEXT) {
                val s = decode(e.data)
                if (s.startsWith("@T") && s.length > 2) return s.substring(2).trim()
            }
        }
        return null
    }

    /** UTF-8 kung valid, kung hindi ay Windows-1252 (karaniwan sa lumang karaoke files). */
    fun decode(bytes: ByteArray): String {
        val raw = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, WIN1252)
        }
        return raw.replace("\u0000", "")
    }

    /** Hinahati ang syllables sa mga linya. */
    private class LineBuilder {
        private val lines = ArrayList<LyricLine>()
        private var current = ArrayList<Syllable>()
        private var currentLen = 0
        private var lastMs = -1L

        fun add(ms: Long, raw: String) {
            var t = raw
            if (current.isNotEmpty() && lastMs >= 0 && ms - lastMs > GAP_BREAK_MS) breakLine()
            if (t.startsWith("\\") || t.startsWith("/")) {
                breakLine()
                t = t.substring(1)
            }
            val parts = t.split('\r', '\n')
            parts.forEachIndexed { i, p ->
                if (i > 0) breakLine()
                addPiece(ms, p)
            }
            if (t.isNotBlank()) lastMs = ms
        }

        private fun addPiece(ms: Long, p: String) {
            if (p.isEmpty()) return
            var piece = p
            // Auto-wrap kapag sobrang haba ng linya (para sa files na walang line breaks)
            if (current.isNotEmpty() && currentLen + piece.length > MAX_LINE_CHARS && piece.startsWith(" ")) {
                breakLine()
            }
            if (current.isEmpty()) piece = piece.trimStart()
            if (piece.isEmpty()) return
            current.add(Syllable(ms, piece))
            currentLen += piece.length
        }

        fun breakLine() {
            if (current.isNotEmpty()) {
                lines.add(LyricLine(current))
                current = ArrayList()
                currentLen = 0
            }
        }

        fun finish(): List<LyricLine> {
            breakLine()
            return lines
        }
    }
}
