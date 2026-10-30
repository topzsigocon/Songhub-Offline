package com.songhub.offline.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests para sa MIDI engine: parse/write, lyrics (FF 05 at .KAR), transpose, tempo.
 * Patakbuhin: ./gradlew testDebugUnitTest
 */
class MidiEngineTest {

    private fun meta(tick: Long, type: Int, text: String) =
        MidiEvent.Meta(tick, type, text.toByteArray(Charsets.ISO_8859_1))

    /** 480 ticks per quarter, 120 BPM -> 1 quarter = 500 ms. */
    private fun sampleLyricMidi(): MidiFile {
        val conductor = MidiTrack(
            mutableListOf(MidiEvent.Meta(0, MidiFile.META_TEMPO, byteArrayOf(0x07, 0xA1.toByte(), 0x20)))
        )
        val words = MidiTrack(
            mutableListOf(
                meta(480, MidiFile.META_LYRIC, "Ma"),
                meta(720, MidiFile.META_LYRIC, "hal "),
                meta(960, MidiFile.META_LYRIC, "kita\r"),
                meta(1440, MidiFile.META_LYRIC, "I "),
                meta(1680, MidiFile.META_LYRIC, "love "),
                meta(1920, MidiFile.META_LYRIC, "you\n")
            )
        )
        val melody = MidiTrack(
            mutableListOf(
                MidiEvent.Channel(0, 0x90, 60, 100),
                MidiEvent.Channel(480, 0x80, 60, 0),
                MidiEvent.Channel(0, 0x99, 36, 100), // drums - hindi dapat ma-transpose
                MidiEvent.Channel(10, 0xC0, 5, -1)
            )
        )
        return MidiFile(1, 480, mutableListOf(conductor, words, melody))
    }

    @Test
    fun roundTripKeepsEvents() {
        val parsed = MidiFile.parse(sampleLyricMidi().toBytes())
        assertEquals(3, parsed.tracks.size)
        assertEquals(480, parsed.division)
        val lyrics = parsed.tracks[1].events.filterIsInstance<MidiEvent.Meta>().filter { it.type == MidiFile.META_LYRIC }
        assertEquals(6, lyrics.size)
        assertEquals(1920L, lyrics.last().tick)
    }

    @Test
    fun lyricEventsBecomeTimedLines() {
        val midi = MidiFile.parse(sampleLyricMidi().toBytes())
        val lyrics = LyricsExtractor.extract(midi, TempoMap.build(midi))
        assertEquals(2, lyrics.lines.size)
        assertEquals("Mahal kita", lyrics.lines[0].text)
        assertEquals("I love you", lyrics.lines[1].text)
        assertEquals(500L, lyrics.lines[0].syllables[0].timeMs)   // tick 480 @120bpm
        assertEquals(2000L, lyrics.lines[1].syllables[2].timeMs)  // tick 1920
    }

    @Test
    fun karTextEventsAreSupported() {
        val track = MidiTrack(
            mutableListOf(
                meta(0, MidiFile.META_TEXT, "@KMIDI KARAOKE FILE"),
                meta(0, MidiFile.META_TEXT, "@TBakit Ngayon Ka Lang"),
                meta(480, MidiFile.META_TEXT, "\\Ba"),
                meta(600, MidiFile.META_TEXT, "kit "),
                meta(720, MidiFile.META_TEXT, "ngayon"),
                meta(960, MidiFile.META_TEXT, "/Ka "),
                meta(1200, MidiFile.META_TEXT, "lang")
            )
        )
        val midi = MidiFile.parse(MidiFile(1, 480, mutableListOf(track)).toBytes())
        val lyrics = LyricsExtractor.extract(midi, TempoMap.build(midi))
        assertEquals("Bakit Ngayon Ka Lang", lyrics.title)
        assertEquals(listOf("Bakit ngayon", "Ka lang"), lyrics.lines.map { it.text })
    }

    @Test
    fun transposeMovesNotesButNotDrums() {
        val out = MidiTransformer.transform(sampleLyricMidi(), 3, 1.0f)
        val ch = out.tracks[2].events.filterIsInstance<MidiEvent.Channel>()
        assertEquals(63, ch[0].data1)  // note on C4 -> D#4
        assertEquals(63, ch[1].data1)  // note off
        assertEquals(36, ch[2].data1)  // drum channel 10 unchanged
        assertEquals(5, ch[3].data1)   // program change unchanged
    }

    @Test
    fun fasterTempoMakesLyricsEarlier() {
        val fast = MidiTransformer.transform(sampleLyricMidi(), 0, 1.25f)
        val lyrics = LyricsExtractor.extract(fast, TempoMap.build(fast))
        assertEquals(400L, lyrics.lines[0].syllables[0].timeMs)   // 500 ms / 1.25
    }

    @Test
    fun tempoAddedWhenFileHasNone() {
        val noTempo = MidiFile(0, 480, mutableListOf(MidiTrack(mutableListOf(meta(480, MidiFile.META_LYRIC, "La")))))
        val slow = MidiTransformer.transform(noTempo, 0, 0.5f)
        val map = TempoMap.build(slow)
        assertEquals(1000.0, map.tickToMs(480), 0.5)
        assertEquals(480L, map.msToTick(1000.0))
    }

    @Test
    fun runningStatusIsParsed() {
        // MTrk na gumagamit ng running status: 90 3C 64, (delta 0) 3E 64
        val track = byteArrayOf(0x00, 0x90.toByte(), 0x3C, 0x64, 0x00, 0x3E, 0x64, 0x00, 0xFF.toByte(), 0x2F, 0x00)
        val header = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(), 0, 0, 0, 6, 0, 0, 0, 1, 0x01, 0xE0.toByte(),
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(), 0, 0, 0, track.size.toByte()
        )
        val midi = MidiFile.parse(header + track)
        val notes = midi.tracks[0].events.filterIsInstance<MidiEvent.Channel>()
        assertEquals(2, notes.size)
        assertEquals(0x3E, notes[1].data1)
        assertTrue(notes.all { it.status == 0x90 })
    }
}
