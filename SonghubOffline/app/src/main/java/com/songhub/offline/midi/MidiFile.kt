package com.songhub.offline.midi

import java.io.ByteArrayOutputStream

/**
 * Simpleng Standard MIDI File (SMF) parser + writer.
 * Simple Standard MIDI File parser and writer - pure Kotlin, walang external library.
 *
 * Bawat event ay may "absolute tick" para madaling i-compute ang oras (ms)
 * at madaling i-modify (transpose / tempo) bago isulat ulit.
 */
sealed class MidiEvent(val tick: Long) {

    /** Note on/off, program change, control change, atbp. data2 = -1 kung walang second byte. */
    class Channel(tick: Long, val status: Int, val data1: Int, val data2: Int) : MidiEvent(tick)

    /** Meta event (FF xx). Hal: 0x05 = Lyric, 0x01 = Text, 0x51 = Tempo, 0x2F = End of Track. */
    class Meta(tick: Long, val type: Int, val data: ByteArray) : MidiEvent(tick)

    /** System Exclusive (F0 / F7). */
    class SysEx(tick: Long, val status: Int, val data: ByteArray) : MidiEvent(tick)
}

class MidiTrack(val events: MutableList<MidiEvent>)

class MidiFile(
    val format: Int,
    /** Ticks per quarter note (o SMPTE kung naka-set ang top bit). */
    val division: Int,
    val tracks: MutableList<MidiTrack>
) {

    companion object {
        const val META_TEXT = 0x01
        const val META_TRACK_NAME = 0x03
        const val META_LYRIC = 0x05
        const val META_END_OF_TRACK = 0x2F
        const val META_TEMPO = 0x51

        /**
         * I-parse ang bytes ng .mid / .kar / .rmi file.
         * Sinusuportahan din ang RIFF-RMID wrapper (hinahanap lang ang "MThd").
         */
        fun parse(bytes: ByteArray): MidiFile {
            val start = indexOf(bytes, "MThd")
            require(start >= 0) { "Hindi valid na MIDI file (walang MThd header)" }
            val r = Reader(bytes, start + 4)
            val headerLen = r.int32()
            val format = r.int16()
            r.int16() // declared track count - hindi na kailangan, binabasa natin lahat ng MTrk
            val division = r.int16()
            r.pos = start + 8 + headerLen

            val tracks = ArrayList<MidiTrack>()
            while (r.pos + 8 <= bytes.size) {
                val id = String(bytes, r.pos, 4, Charsets.ISO_8859_1)
                r.pos += 4
                var len = r.int32()
                if (len < 0 || r.pos + len > bytes.size) len = bytes.size - r.pos // sirang length -> basahin hanggang dulo
                if (id == "MTrk") {
                    tracks.add(parseTrack(bytes, r.pos, r.pos + len))
                }
                r.pos += len
            }
            require(tracks.isNotEmpty()) { "Walang laman na track ang MIDI file" }
            return MidiFile(format, if (division == 0) 480 else division, tracks)
        }

        private fun parseTrack(bytes: ByteArray, from: Int, to: Int): MidiTrack {
            val events = ArrayList<MidiEvent>()
            val r = Reader(bytes, from)
            var tick = 0L
            var running = 0
            try {
                while (r.pos < to) {
                    tick += r.vlq()
                    if (r.pos >= to) break
                    val b = r.peek()
                    val status: Int
                    if (b and 0x80 != 0) {
                        status = b
                        r.pos++
                    } else {
                        // Running status: gamitin ulit ang huling status byte
                        if (running == 0) break
                        status = running
                    }
                    when {
                        status == 0xFF -> {
                            val type = r.u8()
                            val len = r.vlq().toInt()
                            val data = r.bytes(len)
                            events.add(MidiEvent.Meta(tick, type, data))
                            if (type == META_END_OF_TRACK) break
                        }
                        status == 0xF0 || status == 0xF7 -> {
                            val len = r.vlq().toInt()
                            events.add(MidiEvent.SysEx(tick, status, r.bytes(len)))
                        }
                        status >= 0x80 -> {
                            running = status
                            val type = status and 0xF0
                            val d1 = r.u8()
                            val d2 = if (type == 0xC0 || type == 0xD0) -1 else r.u8()
                            events.add(MidiEvent.Channel(tick, status, d1, d2))
                        }
                    }
                }
            } catch (e: IndexOutOfBoundsException) {
                // Sirang track - itago na lang ang nabasa na
            }
            return MidiTrack(events)
        }

        private fun indexOf(bytes: ByteArray, tag: String): Int {
            val t = tag.toByteArray(Charsets.ISO_8859_1)
            outer@ for (i in 0..bytes.size - t.size) {
                for (j in t.indices) if (bytes[i + j] != t[j]) continue@outer
                return i
            }
            return -1
        }
    }

    /** Isulat ulit bilang valid na Standard MIDI File bytes. */
    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.ISO_8859_1))
        writeInt32(out, 6)
        writeInt16(out, if (tracks.size > 1 && format == 0) 1 else format)
        writeInt16(out, tracks.size)
        writeInt16(out, division)

        for (track in tracks) {
            val tb = ByteArrayOutputStream()
            var last = 0L
            var endTick = 0L
            // Stable sort: pinapanatili ang orihinal na order ng events sa parehong tick
            val sorted = track.events.sortedBy { it.tick }
            for (e in sorted) {
                if (e is MidiEvent.Meta && e.type == META_END_OF_TRACK) {
                    endTick = maxOf(endTick, e.tick)
                    continue
                }
                writeVlq(tb, e.tick - last)
                last = e.tick
                when (e) {
                    is MidiEvent.Channel -> {
                        tb.write(e.status)
                        tb.write(e.data1 and 0x7F)
                        if (e.data2 >= 0) tb.write(e.data2 and 0x7F)
                    }
                    is MidiEvent.Meta -> {
                        tb.write(0xFF)
                        tb.write(e.type)
                        writeVlq(tb, e.data.size.toLong())
                        tb.write(e.data)
                    }
                    is MidiEvent.SysEx -> {
                        tb.write(e.status)
                        writeVlq(tb, e.data.size.toLong())
                        tb.write(e.data)
                    }
                }
            }
            // Laging may End-of-Track sa dulo
            writeVlq(tb, maxOf(0L, endTick - last))
            tb.write(0xFF); tb.write(META_END_OF_TRACK); tb.write(0)

            val data = tb.toByteArray()
            out.write("MTrk".toByteArray(Charsets.ISO_8859_1))
            writeInt32(out, data.size)
            out.write(data)
        }
        return out.toByteArray()
    }

    private fun writeInt32(o: ByteArrayOutputStream, v: Int) {
        o.write((v ushr 24) and 0xFF); o.write((v ushr 16) and 0xFF)
        o.write((v ushr 8) and 0xFF); o.write(v and 0xFF)
    }

    private fun writeInt16(o: ByteArrayOutputStream, v: Int) {
        o.write((v ushr 8) and 0xFF); o.write(v and 0xFF)
    }

    private fun writeVlq(o: ByteArrayOutputStream, value: Long) {
        var v = value.coerceIn(0L, 0x0FFFFFFFL)
        var buffer = v and 0x7F
        v = v shr 7
        while (v > 0) {
            buffer = (buffer shl 8) or ((v and 0x7F) or 0x80)
            v = v shr 7
        }
        while (true) {
            o.write((buffer and 0xFF).toInt())
            if (buffer and 0x80 != 0L) buffer = buffer shr 8 else break
        }
    }

    /** Maliit na byte reader helper. */
    private class Reader(val b: ByteArray, var pos: Int) {
        fun peek(): Int = b[pos].toInt() and 0xFF
        fun u8(): Int = b[pos++].toInt() and 0xFF
        fun int16(): Int = (u8() shl 8) or u8()
        fun int32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
        fun vlq(): Long {
            var value = 0L
            for (i in 0 until 4) {
                val c = u8()
                value = (value shl 7) or (c and 0x7F).toLong()
                if (c and 0x80 == 0) break
            }
            return value
        }
        fun bytes(len: Int): ByteArray {
            val n = len.coerceIn(0, b.size - pos)
            val out = b.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
    }
}
