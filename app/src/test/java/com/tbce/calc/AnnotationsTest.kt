package com.tbce.calc

import com.tbce.calc.reader.Annotations
import com.tbce.calc.reader.Annotations.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase gates 3 and 4: annotations persist, round-trip, and are anchored by reference only. */
class AnnotationsTest {
    private var stored: ByteArray? = null
    private val a = Annotations { stored = it }

    private fun reloaded() = Annotations().apply { load(stored!!) }

    @Test fun highlightsByVerse() {
        a.setHighlight("JHN", 3, listOf(16, 17, 18), 2)
        assertEquals(2, a.highlight("JHN", 3, 17))
        a.setHighlight("JHN", 3, listOf(17), null)
        assertNull(a.highlight("JHN", 3, 17))
        val b = reloaded()
        assertEquals(2, b.highlight("JHN", 3, 16))
        assertNull(b.highlight("JHN", 3, 17))
        assertEquals(2, b.highlight("JHN", 3, 18))
    }

    @Test fun notesAndFootnotes() {
        val n = a.addNote("ROM", 8, listOf(30, 28), "line one\nline two")
        a.updateNote(n.id, "edited")
        val f1 = a.addFootnote("ROM", 8, 0, "first")
        Thread.sleep(2)
        a.addFootnote("ROM", 8, 31, "second")
        val b = reloaded()
        assertEquals("edited", b.note(n.id)!!.body)
        assertEquals(listOf(28, 30), b.notesIn("ROM", 8).single().verses)
        assertEquals(listOf("first", "second"), b.footnotesIn("ROM", 8).map { it.body })
        assertEquals(31, b.footnotesIn("ROM", 8)[1].verse)
        a.deleteFootnote(f1.id)
        assertEquals(1, reloaded().footnotesIn("ROM", 8).size)
        a.deleteNote(n.id)
        assertTrue(reloaded().notesIn("ROM", 8).isEmpty())
    }

    @Test fun savedItemsWithNotes() {
        val s = a.save(Kind.RANGE, "PSA", 23, listOf(1, 4, 6), "kjv")
        assertSame(s, a.save(Kind.RANGE, "PSA", 23, listOf(6, 1, 4), "bsb"))
        a.setLabel(s.id, "comfort")
        a.addSavedNote(s.id, "read this often")
        val c = a.save(Kind.CHAPTER, "JHN", 1, emptyList(), "bsb")
        val b = reloaded()
        val s2 = b.savedItem(s.id)!!
        assertEquals("comfort", s2.label)
        assertEquals("kjv", s2.translation)
        assertEquals(listOf(1, 4, 6), s2.verses)
        assertEquals("read this often", s2.notes.single().body)
        assertEquals(Kind.CHAPTER, b.savedItem(c.id)!!.kind)
        a.updateSavedNote(s.id, s.notes[0].id, "changed")
        assertEquals("changed", reloaded().savedItem(s.id)!!.notes[0].body)
        a.deleteSaved(s.id)
        assertNull(reloaded().savedItem(s.id))
    }

    @Test fun readerNotesShowOnSavedItems() {
        val touching = a.addNote("PSA", 23, listOf(3, 4), "touches verse 4")
        a.addNote("PSA", 23, listOf(2), "not saved")
        a.addNote("PSA", 24, listOf(4), "other chapter")
        val at4 = a.addFootnote("PSA", 23, 4, "chapter note at 4")
        a.addFootnote("PSA", 23, 0, "whole chapter")
        val verses = a.save(Kind.RANGE, "PSA", 23, listOf(1, 4, 6), "kjv")
        assertEquals(listOf(touching.id), a.notesFor(verses).map { it.id })
        assertEquals(listOf(at4.id), a.footnotesFor(verses).map { it.id })
        val chapter = a.save(Kind.CHAPTER, "PSA", 23, emptyList(), "kjv")
        assertEquals(2, a.notesFor(chapter).size)
        assertEquals(2, a.footnotesFor(chapter).size)
    }

    @Test fun recentPassages() {
        a.visit("GEN", 1); a.visit("GEN", 2); a.visit("GEN", 1); a.visit("GEN", 1)
        assertEquals(listOf("GEN.1", "GEN.2"), reloaded().recent.map { "${it.book}.${it.chapter}" })
        for (i in 1..20) a.visit("PSA", i)
        assertEquals(Annotations.MAX_RECENT, reloaded().recent.size)
        assertEquals(20, reloaded().recent.first().chapter)
    }

    @Test fun clearDropsEverything() {
        a.setHighlight("GEN", 1, listOf(1), 0)
        a.addNote("GEN", 1, listOf(1), "x")
        a.clear()
        assertNull(a.highlight("GEN", 1, 1))
        assertTrue(a.notesIn("GEN", 1).isEmpty())
    }

    @Test fun readsTheOlderRangeFormat() {
        val old = """{"v":1,"h":{},"n":[["a1","JHN",3,16,18,"old note",1,1]],"f":[],
            "s":[{"id":"s1","k":"RANGE","b":"PSA","c":23,"v1":1,"v2":3,"l":"","t":"bsb","at":1,"n":[]},
                 {"id":"s2","k":"CHAPTER","b":"PSA","c":1,"v1":0,"v2":0,"l":"","t":"bsb","at":2,"n":[]}],"r":[]}"""
        val b = Annotations().apply { load(old.toByteArray()) }
        assertEquals(listOf(16, 17, 18), b.note("a1")!!.verses)
        assertEquals(listOf(1, 2, 3), b.savedItem("s1")!!.verses)
        assertTrue(b.savedItem("s2")!!.verses.isEmpty())
    }

    @Test fun verseLists() {
        assertEquals("16, 18", com.tbce.calc.reader.ReaderState.verseList(listOf(18, 16)))
        assertEquals("16–18, 20", com.tbce.calc.reader.ReaderState.verseList(listOf(16, 17, 18, 20)))
        assertEquals("5", com.tbce.calc.reader.ReaderState.verseList(listOf(5)))
    }

    @Test fun mergeAddsWithoutOverwriting() {
        a.setHighlight("JHN", 3, listOf(16), 1)
        val other = Annotations()
        other.setHighlight("JHN", 3, listOf(16, 17), 4)
        other.addNote("JHN", 3, listOf(16), "from backup")
        val s = other.save(Kind.VERSE, "PSA", 23, listOf(1), "bsb")
        other.addSavedNote(s.id, "n1")
        assertEquals(3, a.merge(other))
        assertEquals(1, a.highlight("JHN", 3, 16))
        assertEquals(4, a.highlight("JHN", 3, 17))
        assertEquals(0, a.merge(other))
        assertEquals("from backup", reloaded().notesIn("JHN", 3).single().body)
    }
}
