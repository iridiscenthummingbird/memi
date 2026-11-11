package app.memi

import kotlin.math.max
import kotlin.math.min

/**
 * One visible piece of a note capsule. Capsules flow one after another like words in a text;
 * a capsule that doesn't fit the rest of the line is cut, and its continuation starts the next line.
 * [first]/[last] tell which ends of the piece are rounded (the real ends of the capsule).
 */
data class CapsuleSeg(
    val index: Int,
    val text: String,
    val x: Float,
    val width: Float,
    val line: Int,
    val first: Boolean,
    val last: Boolean,
)

class FlowStyle(
    /** Horizontal padding inside a piece, on each side. */
    val hPad: Float,
    /** Gap between neighbouring capsules on a line. */
    val gap: Float,
    /** Don't start a cut piece at the end of a line if less text room than this is left. */
    val minPiece: Float,
    /** A capsule takes at most this many line widths; longer text is cut with an ellipsis. */
    val maxLines: Float = 1.5f,
    /** Max pieces on one line (widgets can't hold more than 10 children in a row). */
    val maxPerLine: Int = Int.MAX_VALUE,
)

/**
 * Lays out [texts] as a continuous flow of capsules within [lineWidth].
 * [prefixes] is the extra width of an icon shown in the first piece of each capsule.
 * All values are in the same units as [measure] returns (px for the app, px for the widget).
 */
fun flowCapsules(
    texts: List<String>,
    prefixes: List<Float>,
    lineWidth: Float,
    style: FlowStyle,
    measure: (String) -> Float,
): List<CapsuleSeg> {
    val out = ArrayList<CapsuleSeg>()
    val ellipsis = "…"
    val ellipsisW = measure(ellipsis)
    var line = 0
    var x = 0f          // end of the last piece on the current line; 0 when the line is empty
    var onLine = 0      // pieces on the current line

    fun newLine() { line++; x = 0f; onLine = 0 }

    texts.forEachIndexed { i, text ->
        var rest = text.trim()
        var first = true
        var budget = lineWidth * style.maxLines
        while (true) {
            if (onLine >= style.maxPerLine) newLine()
            val start = if (onLine == 0) 0f else x + style.gap
            val avail = lineWidth - start
            val pad = 2 * style.hPad + if (first) prefixes[i] else 0f
            val full = pad + measure(rest)

            fun place(t: String, w: Float, last: Boolean) {
                out += CapsuleSeg(i, t, start, w, line, first, last)
                x = start + w; onLine++
            }

            if (full <= min(avail, budget)) { place(rest, full, true); break }

            if (budget <= avail) {
                // Longer than the 1.5-line limit: cut the text with an ellipsis
                val n = fitChars(rest, budget - pad - ellipsisW, measure, preferWords = false)
                val t = rest.take(n).trimEnd() + ellipsis
                place(t, pad + measure(t), true)
                break
            }

            // Doesn't fit the rest of the line: cut it here and continue on the next line
            val n = when {
                onLine == 0 -> max(1, fitChars(rest, avail - pad, measure, preferWords = true, allowWordCut = true))
                avail - pad >= style.minPiece -> {
                    // A word longer than a whole line gets cut anyway, so cut it right here
                    val hugeWord = pad + measure(rest.substringBefore(' ')) > lineWidth
                    fitChars(rest, avail - pad, measure, preferWords = true, allowWordCut = hugeWord)
                }
                else -> 0
            }
            if (n == 0) { newLine(); continue }
            val piece = rest.take(n).trimEnd()
            val remaining = rest.drop(n).trimStart()
            if (remaining.isEmpty()) { place(piece, pad + measure(piece), true); break }
            val w = pad + measure(piece)
            place(piece, w, false)
            budget -= w
            rest = remaining
            first = false
            newLine()
        }
    }
    return out
}

/**
 * How many leading chars of [s] fit into [max]. With [preferWords] the cut moves back to a space;
 * if there is no space and [allowWordCut] is false, returns 0 (the word goes to the next line).
 */
private fun fitChars(
    s: String,
    max: Float,
    measure: (String) -> Float,
    preferWords: Boolean,
    allowWordCut: Boolean = true,
): Int {
    if (max <= 0f || s.isEmpty()) return 0
    var lo = 0
    var hi = s.length
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (measure(s.substring(0, mid).trimEnd()) <= max) lo = mid else hi = mid - 1
    }
    if (lo >= s.length) return s.length
    if (lo > 0 && s[lo - 1].isHighSurrogate()) lo--
    if (!preferWords) return lo
    if (lo > 0 && (s[lo] == ' ' || s[lo - 1] == ' ')) return lo
    val space = s.lastIndexOf(' ', lo - 1)
    return when {
        space > 0 -> space + 1
        allowWordCut -> lo
        else -> 0
    }
}
