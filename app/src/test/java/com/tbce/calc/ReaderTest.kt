package com.tbce.calc

import com.tbce.calc.reader.Packs
import com.tbce.calc.reader.Para
import com.tbce.calc.reader.Ref
import com.tbce.calc.reader.ReferenceParser
import com.tbce.calc.reader.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real packs in src/main/assets, the same way the app does. */
class ReaderTest {
    private val packs = Packs { name -> File("src/main/assets/r/$name").readBytes() }
    private val meta = packs.meta()
    private val parser = ReferenceParser(meta.aliases, meta.translations[0].books.associate { it.code to it.chapters })

    @Test fun everyBookOfEveryTranslationDecrypts() {
        assertEquals(listOf("BSB", "WEB", "KJV"), meta.translations.map { it.abbr })
        for (t in meta.translations) {
            assertEquals(66, t.books.size)
            assertEquals(1189, t.totalChapters)
            var verses = 0
            for (b in t.books) {
                val book = packs.book(t.id, b.code)
                assertEquals(b.chapters, book.chapters.size)
                verses += book.chapters.sumOf { ch -> SearchIndex.verseTexts(ch).size }
            }
            assertTrue("${t.abbr} has $verses verses", verses in 31_000..31_200)
        }
    }

    @Test fun textIsClean() {
        val kjv = packs.book("kjv", "JHN").chapters[2]
        val v16 = SearchIndex.verseTexts(kjv).first { it.first == 16 }.second
        assertEquals("For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.", v16)
        for (t in meta.translations) for (b in listOf("GEN", "PSA", "JHN", "REV")) {
            for (ch in packs.book(t.id, b).chapters) for (bl in ch) if (bl is Para) for (s in bl.segs) {
                assertTrue(s.text, '\\' !in s.text && '|' !in s.text && '¶' !in s.text)
            }
        }
    }

    @Test fun globalIndexRoundTrips() {
        val t = meta.translations[0]
        assertEquals(0 to 1, t.locate(0))
        assertEquals(t.books.lastIndex to 22, t.locate(t.totalChapters - 1))
        val jhn = t.bookIndex("JHN")
        assertEquals(jhn to 3, t.locate(t.globalIndex(jhn, 3)))
    }

    @Test fun references() {
        assertEquals(Ref("JHN", 3, 16), parser.parse("John 3:16"))
        assertEquals(Ref("JHN", 3, 16), parser.parse("jn 3 16"))
        assertEquals(Ref("1CO", 13, 4, 7), parser.parse("1 Cor 13:4-7"))
        assertEquals(Ref("1CO", 13, 4, 7), parser.parse("1cor 13:4–7"))
        assertEquals(Ref("PSA", 23), parser.parse("ps 23"))
        assertEquals(Ref("PSA", 119, 105), parser.parse("Psalm 119:105"))
        assertEquals(Ref("JUD", 1, 5), parser.parse("Jude 5"))
        assertEquals(Ref("SNG", 2, 4), parser.parse("Song of Songs 2:4"))
        assertEquals(Ref("1JN", 4, 8), parser.parse("1 John 4:8"))
        assertEquals(Ref("JON", 1), parser.parse("Jonah"))
        assertEquals(Ref("GEN", 1, 1), parser.parse("Gen. 1:1"))
        assertNull(parser.parse("John 99"))
        assertNull(parser.parse("love one another"))
        assertNull(parser.parse(""))
    }

    @Test fun search() {
        val t = meta.translations.first { it.id == "kjv" }
        val idx = SearchIndex.build(t) { packs.book("kjv", it) }
        val (hits, n) = idx.search("only begotten son")
        assertTrue(n >= 4)
        assertTrue(hits.any { t.books[it.book].code == "JHN" && it.chapter == 3 && it.verse == 16 })
        assertEquals(0, idx.search("zzzqqq").second)
        // Punctuation and case are ignored.
        assertTrue(idx.search("LORD'S").second > 0)
    }

    @Test fun matchesFindTheSearchedWords() {
        val text = "For God so loved the world, that he gave his only begotten Son"
        val m = SearchIndex.matches(text, "only begotten son")
        assertEquals(listOf("only", "begotten", "Son"), m.map { text.substring(it) })
        assertEquals(listOf("loved"), SearchIndex.matches(text, "LOVED").map { text.substring(it) })
        // Folded like the search: an apostrophe or accent in the text still matches.
        val lord = "the LORD’s house"
        assertEquals(listOf("LORD’s"), SearchIndex.matches(lord, "lords").map { lord.substring(it) })
        assertEquals(listOf("Égypte"), SearchIndex.matches("en Égypte", "egypte").map { "en Égypte".substring(it) })
        assertTrue(SearchIndex.matches(text, "  ").isEmpty())
        assertTrue(SearchIndex.matches(text, "zzz").isEmpty())
    }
}
