package com.songhub.offline.midi

/**
 * Tempo map: nagko-convert ng MIDI tick <-> milliseconds.
 * Ginagamit para malaman kung kailan dapat lumabas/mag-highlight ang bawat lyric.
 */
class TempoMap private constructor(
    private val ticks: LongArray,
    private val msStart: DoubleArray,
    private val usPerQuarter: IntArray,
    private val division: Int,
    private val smpteMsPerTick: Double
) {

    fun tickToMs(tick: Long): Double {
        if (smpteMsPerTick > 0) return tick * smpteMsPerTick
        val i = lastIndexAtOrBefore(ticks, tick)
        return msStart[i] + (tick - ticks[i]) * usPerQuarter[i] / 1000.0 / division
    }

    fun msToTick(ms: Double): Long {
        if (smpteMsPerTick > 0) return (ms / smpteMsPerTick).toLong()
        var i = 0
        for (k in msStart.indices) if (msStart[k] <= ms) i = k else break
        return ticks[i] + ((ms - msStart[i]) * 1000.0 * division / usPerQuarter[i]).toLong()
    }

    companion object {
        private const val DEFAULT_TEMPO = 500_000 // 120 BPM

        val DEFAULT = TempoMap(longArrayOf(0), doubleArrayOf(0.0), intArrayOf(DEFAULT_TEMPO), 480, 0.0)

        fun build(midi: MidiFile): TempoMap {
            val div = midi.division
            if (div and 0x8000 != 0) {
                // SMPTE timing (bihira sa karaoke files)
                val fps = -((div shr 8).toByte().toInt())
                val tpf = div and 0xFF
                val msPerTick = 1000.0 / (maxOf(fps, 1) * maxOf(tpf, 1))
                return TempoMap(longArrayOf(0), doubleArrayOf(0.0), intArrayOf(DEFAULT_TEMPO), 480, msPerTick)
            }

            val changes = midi.tracks.asSequence()
                .flatMap { it.events.asSequence() }
                .filterIsInstance<MidiEvent.Meta>()
                .filter { it.type == MidiFile.META_TEMPO && it.data.size >= 3 }
                .map { e ->
                    val us = ((e.data[0].toInt() and 0xFF) shl 16) or
                            ((e.data[1].toInt() and 0xFF) shl 8) or
                            (e.data[2].toInt() and 0xFF)
                    e.tick to us
                }
                .filter { it.second > 0 }
                .sortedBy { it.first }
                .toList()

            val t = arrayListOf(0L)
            val m = arrayListOf(0.0)
            val u = arrayListOf(DEFAULT_TEMPO)
            for ((tick, us) in changes) {
                val last = t.size - 1
                if (tick == t[last]) {
                    u[last] = us
                    continue
                }
                val ms = m[last] + (tick - t[last]) * u[last] / 1000.0 / div
                t.add(tick); m.add(ms); u.add(us)
            }
            return TempoMap(t.toLongArray(), m.toDoubleArray(), u.toIntArray(), div, 0.0)
        }

        /** Binary search: pinakahuling index kung saan arr[i] <= value. */
        fun lastIndexAtOrBefore(arr: LongArray, value: Long): Int {
            var lo = 0
            var hi = arr.size - 1
            var ans = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (arr[mid] <= value) { ans = mid; lo = mid + 1 } else hi = mid - 1
            }
            return maxOf(ans, 0)
        }
    }
}
