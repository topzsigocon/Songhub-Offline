package com.songhub.offline.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.songhub.offline.R
import com.songhub.offline.midi.KaraokeLyrics
import kotlin.math.roundToInt

/**
 * Karaoke lyrics display.
 * Nasa gitna ang kasalukuyang linya - nagha-highlight (kulay amber) habang kinakanta,
 * syllable by syllable, may smooth na "wipe" sa loob ng bawat syllable.
 */
class LyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val sungColor = ContextCompat.getColor(context, R.color.lyric_sung)
    private val unsungColor = ContextCompat.getColor(context, R.color.lyric_unsung)
    private val dimColor = ContextCompat.getColor(context, R.color.lyric_dim)

    private val tvCountdown = makeText(22f, sungColor, true)
    private val tvPrev = makeText(18f, dimColor, false)
    private val tvCurrent = makeText(30f, unsungColor, true)
    private val tvNext = makeText(24f, unsungColor, false)
    private val tvNext2 = makeText(18f, dimColor, false)

    private var lyrics: KaraokeLyrics = KaraokeLyrics.EMPTY
    private var title: String = ""

    // Flattened syllables para mabilis hanapin (binary search)
    private var times = LongArray(0)
    private var lineOf = IntArray(0)
    private var charStart = IntArray(0)
    private var charEnd = IntArray(0)

    private var lastLine = Int.MIN_VALUE
    private var lastChars = -1
    private var lastCountdown = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(tvCountdown)
        addView(tvPrev)
        addView(tvCurrent)
        addView(tvNext)
        addView(tvNext2)
    }

    private fun makeText(sp: Float, color: Int, bold: Boolean): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        setShadowLayer(6f, 0f, 2f, 0xAA000000.toInt())
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun setLyrics(newLyrics: KaraokeLyrics, songTitle: String) {
        lyrics = newLyrics
        title = songTitle
        val n = newLyrics.lines.sumOf { it.syllables.size }
        times = LongArray(n); lineOf = IntArray(n); charStart = IntArray(n); charEnd = IntArray(n)
        var k = 0
        newLyrics.lines.forEachIndexed { li, line ->
            var offset = 0
            for (s in line.syllables) {
                times[k] = s.timeMs; lineOf[k] = li
                charStart[k] = offset; offset += s.text.length; charEnd[k] = offset
                k++
            }
        }
        lastLine = Int.MIN_VALUE; lastChars = -1; lastCountdown = "?"
        update(0)
    }

    /** Tawagin ito ~30x per second habang tumutugtog. */
    fun update(posMs: Long) {
        if (!lyrics.hasLyrics) {
            if (lastLine != -100) {
                lastLine = -100
                tvCountdown.text = ""
                tvPrev.text = ""
                tvCurrent.text = title
                tvNext.text = context.getString(R.string.no_lyrics)
                tvNext2.text = ""
            }
            return
        }

        val k = lastSyllableAtOrBefore(posMs)
        val line: Int
        val chars: Int
        var countdown = ""
        if (k < 0) {
            // Intro pa - ipakita ang unang linya + countdown dots
            line = 0
            chars = 0
            val remaining = times[0] - posMs
            countdown = if (remaining in 1..4000) "● ".repeat(((remaining + 999) / 1000).toInt()).trim() else "♪"
        } else {
            line = lineOf[k]
            val len = charEnd[k] - charStart[k]
            val nextT = if (k + 1 < times.size && lineOf[k + 1] == line) times[k + 1] else times[k] + 600
            val dur = (nextT - times[k]).coerceIn(1L, 1500L)
            val frac = ((posMs - times[k]).toFloat() / dur).coerceIn(0f, 1f)
            chars = charStart[k] + (len * frac).roundToInt()
            // Mahabang instrumental pagkatapos ng linya -> countdown sa susunod
            if (k + 1 < times.size && lineOf[k + 1] != line) {
                val remaining = times[k + 1] - posMs
                if (remaining in 1..4000 && times[k + 1] - times[k] > 6000) {
                    countdown = "● ".repeat(((remaining + 999) / 1000).toInt()).trim()
                }
            }
        }

        if (countdown != lastCountdown) {
            lastCountdown = countdown
            tvCountdown.text = countdown
        }
        if (line == lastLine && chars == lastChars) return
        lastLine = line
        lastChars = chars

        val lines = lyrics.lines
        tvPrev.text = if (line >= 1) lines[line - 1].text else ""
        val cur = lines[line].text
        val span = SpannableString(cur)
        val end = chars.coerceIn(0, cur.length)
        if (end > 0) span.setSpan(ForegroundColorSpan(sungColor), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvCurrent.text = span
        tvNext.text = lines.getOrNull(line + 1)?.text ?: ""
        tvNext2.text = lines.getOrNull(line + 2)?.text ?: ""
    }

    private fun lastSyllableAtOrBefore(pos: Long): Int {
        var lo = 0
        var hi = times.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= pos) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
