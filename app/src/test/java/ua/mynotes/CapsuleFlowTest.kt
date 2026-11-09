package ua.mynotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Monospace measure: 1 unit per char; pad 1 per side, gap 1, line width 20. */
class CapsuleFlowTest {
    private val style = FlowStyle(hPad = 1f, gap = 1f, minPiece = 3f)
    private fun flow(vararg t: String) =
        flowCapsules(t.toList(), t.map { 0f }, 20f, style) { it.length.toFloat() }

    @Test fun shortCapsulesShareALine() {
        val s = flow("aaa", "bbb", "ccc")
        assertTrue(s.all { it.line == 0 && it.first && it.last })
        assertEquals(listOf(0f, 6f, 12f), s.map { it.x })
    }

    @Test fun capsuleIsCutAtLineEndAndContinues() {
        val s = flow("aaaaaaaaaa", "bbb ccc ddd")  // first ends at 12, 7 left
        assertEquals(3, s.size)
        assertEquals("bbb", s[1].text); assertTrue(s[1].first && !s[1].last); assertEquals(0, s[1].line)
        assertEquals("ccc ddd", s[2].text); assertTrue(!s[2].first && s[2].last); assertEquals(1, s[2].line)
        assertEquals(0f, s[2].x)
    }

    @Test fun tooLittleRoomMovesWholeCapsule() {
        val s = flow("aaaaaaaaaaaaaaa", "bbb ccc")  // ends at 17, 2 left
        assertEquals(1, s[1].line); assertTrue(s[1].first && s[1].last)
    }

    @Test fun longTextIsCutWithEllipsisAtOneAndHalfLines() {
        val s = flow("w".repeat(5) + " " + "x".repeat(60))
        val total = s.sumOf { it.width.toDouble() }
        assertTrue("total=$total", total <= 30.0)
        assertTrue(s.last().text.endsWith("…")); assertTrue(s.last().last)
    }

    @Test fun maxPerLineIsRespected() {
        val s = flowCapsules(List(6) { "a" }, List(6) { 0f }, 100f, FlowStyle(1f, 1f, 3f, maxPerLine = 4)) { it.length.toFloat() }
        assertEquals(listOf(0, 0, 0, 0, 1, 1), s.map { it.line })
    }

    @Test fun hugeWordIsCutRightAtLineEnd() {
        val s = flow("aaaaaa", "x".repeat(25))  // ends at 8, 11 left
        assertEquals(0, s[1].line); assertTrue(s[1].first && !s[1].last)
        assertEquals(1, s[2].line)
    }

    @Test fun normalWordIsNotCutMidWord() {
        val s = flow("aaaaaaaaaa", "bbbbbbbbb")  // 7 left, word fits a full line
        assertEquals(1, s[1].line); assertTrue(s[1].first && s[1].last)
    }
}
