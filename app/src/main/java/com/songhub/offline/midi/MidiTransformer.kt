package com.songhub.offline.midi

/**
 * Transpose (Key +/-) at Tempo control.
 *
 * Paano gumagana: gumagawa ng BAGONG kopya ng MIDI kung saan
 *  - lahat ng note (maliban sa drums, channel 10) ay inililipat ng N semitones
 *  - lahat ng tempo event ay pinapabilis/pinababagal ayon sa speed
 * Tapos ito ang pinapatugtog ng built-in Android MIDI synth (MediaPlayer / Sonivox).
 */
object MidiTransformer {

    private const val DRUM_CHANNEL = 9
    private const val DEFAULT_TEMPO = 500_000

    fun transform(src: MidiFile, semitones: Int, speed: Float): MidiFile {
        val changeSpeed = speed != 1.0f
        var foundTempo = false

        val newTracks = src.tracks.map { track ->
            val events = ArrayList<MidiEvent>(track.events.size + 1)
            for (e in track.events) {
                events.add(
                    when (e) {
                        is MidiEvent.Channel -> transposeEvent(e, semitones)
                        is MidiEvent.Meta -> {
                            if (e.type == MidiFile.META_TEMPO && e.data.size >= 3) {
                                foundTempo = true
                                if (changeSpeed) MidiEvent.Meta(e.tick, e.type, encodeTempo(scale(decodeTempo(e.data), speed))) else e
                            } else e
                        }
                        else -> e
                    }
                )
            }
            MidiTrack(events)
        }.toMutableList()

        // Walang tempo event? Default ay 120 BPM - magdagdag ng isa para gumana ang tempo control
        if (changeSpeed && !foundTempo && newTracks.isNotEmpty()) {
            newTracks[0].events.add(0, MidiEvent.Meta(0, MidiFile.META_TEMPO, encodeTempo(scale(DEFAULT_TEMPO, speed))))
        }
        return MidiFile(src.format, src.division, newTracks)
    }

    private fun transposeEvent(e: MidiEvent.Channel, semitones: Int): MidiEvent {
        if (semitones == 0) return e
        val type = e.status and 0xF0
        val channel = e.status and 0x0F
        if (channel == DRUM_CHANNEL) return e
        return if (type == 0x80 || type == 0x90 || type == 0xA0) {
            MidiEvent.Channel(e.tick, e.status, (e.data1 + semitones).coerceIn(0, 127), e.data2)
        } else e
    }

    private fun scale(us: Int, speed: Float): Int = (us / speed).toInt().coerceIn(1, 0xFFFFFF)

    private fun decodeTempo(d: ByteArray): Int =
        ((d[0].toInt() and 0xFF) shl 16) or ((d[1].toInt() and 0xFF) shl 8) or (d[2].toInt() and 0xFF)

    private fun encodeTempo(us: Int): ByteArray = byteArrayOf(
        ((us shr 16) and 0xFF).toByte(),
        ((us shr 8) and 0xFF).toByte(),
        (us and 0xFF).toByte()
    )
}
