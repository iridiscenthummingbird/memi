package ua.mynotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NotesLogicTest {
    private fun note(id: Long, done: Boolean = false, pinned: Boolean = false, order: Long = id, color: Int = 0) =
        Note(id = id, text = "n$id", color = color, pinned = pinned, done = done, created = id, order = order)

    // ---- sorting ----

    @Test fun activeBeforeDone_pinnedFirst_thenManualOrder() {
        val sorted = sortNotes(
            listOf(
                note(1, done = true, order = 0),
                note(2, order = 5),
                note(3, pinned = true, order = 9),
                note(4, order = 1),
                note(5, done = true, pinned = true, order = 3),
            )
        )
        assertEquals(listOf(3L, 4L, 2L, 5L, 1L), sorted.map { it.id })
    }

    @Test fun equalOrderFallsBackToCreated() {
        val sorted = sortNotes(listOf(note(20, order = 1), note(10, order = 1)))
        assertEquals(listOf(10L, 20L), sorted.map { it.id })
    }

    // ---- add ----

    @Test fun addCleansTextAndCyclesColours() {
        var l = emptyList<Note>()
        repeat(NOTE_COLORS + 1) { l = addNote(l, "  a\nb  ", now = 100) }
        assertEquals("a b", l.first().text)
        assertEquals(listOf(0, 1, 2, 3, 0), l.map { it.color })
    }

    @Test fun addKeepsIdsUniqueAndOrderIncreasing() {
        val l = addNote(addNote(emptyList(), "a", now = 100), "b", now = 100)
        assertEquals(listOf(100L, 101L), l.map { it.id })
        assertEquals(2, l.map { it.order }.distinct().size)
        assert(l[1].order > l[0].order)
    }

    @Test fun addIgnoresBlankText() {
        val l = listOf(note(1))
        assertSame(l, addNote(l, " \n ", now = 5))
    }

    // ---- reorder / restore ----

    @Test fun reorderAssignsPositionsAndLeavesOthersAlone() {
        val l = reorderNotes(listOf(note(1), note(2), note(3, order = 42)), listOf(2, 1))
        assertEquals(mapOf(1L to 1L, 2L to 0L, 3L to 42L), l.associate { it.id to it.order })
    }

    @Test fun restoreDoesNotDuplicate() {
        val l = restoreNotes(listOf(note(1)), listOf(note(1), note(2)))
        assertEquals(listOf(1L, 2L), l.map { it.id })
    }

    // ---- JSON ----

    @Test fun jsonRoundTrip() {
        val l = listOf(note(1, done = true, color = 3), note(2, pinned = true, order = 7))
        assertEquals(l, decodeNotes(encodeNotes(l)))
    }

    @Test fun oldFormatWithoutOptionalFieldsStillLoads() {
        val l = decodeNotes("""[{"id": 5, "text": "old"}, {"id": 6, "text": "mid", "created": 3}]""")
        assertEquals(Note(id = 5, text = "old", color = 0, created = 5, order = 5), l[0])
        assertEquals(3L, l[1].order)  // order defaults to created
    }

    @Test(expected = Exception::class)
    fun corruptJsonThrowsInsteadOfReturningEmpty() {
        decodeNotes("[{\"id\": 1, \"text\": ")
    }
}
