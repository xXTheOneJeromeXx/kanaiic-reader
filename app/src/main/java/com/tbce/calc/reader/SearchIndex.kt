package com.tbce.calc.reader

import java.text.Normalizer

class Hit(val book: Int, val chapter: Int, val verse: Int, val text: String)

/** In-memory full-text index of one translation. Built after unlock, never written to disk. */
class SearchIndex private constructor(
    private val book: IntArray,
    private val chapter: IntArray,
    private val verse: IntArray,
    private val text: Array<String>,
    private val folded: Array<String>,
) {
    val size get() = text.size

    /** Verses containing every word of [query], in text order. */
    fun search(query: String, limit: Int = 500): Pair<List<Hit>, Int> {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList<Hit>() to 0
        val hits = ArrayList<Hit>()
        var count = 0
        for (i in folded.indices) {
            val f = folded[i]
            if (words.all { f.contains(it) }) {
                count++
                if (hits.size < limit) hits += Hit(book[i], chapter[i], verse[i], text[i])
            }
        }
        return hits to count
    }

    companion object {
        fun build(translation: Translation, loadBook: (String) -> Book): SearchIndex {
            val b = ArrayList<Int>(32_000)
            val c = ArrayList<Int>(32_000)
            val v = ArrayList<Int>(32_000)
            val t = ArrayList<String>(32_000)
            translation.books.forEachIndexed { bi, meta ->
                loadBook(meta.code).chapters.forEachIndexed { ci, blocks ->
                    for ((verse, s) in verseTexts(blocks)) {
                        b += bi; c += ci + 1; v += verse; t += s
                    }
                }
            }
            val text = t.toTypedArray()
            return SearchIndex(b.toIntArray(), c.toIntArray(), v.toIntArray(), text, Array(text.size) { fold(text[it]) })
        }

        /** Joins each verse's segments, in order. */
        fun verseTexts(blocks: List<Block>): List<Pair<Int, String>> {
            val out = LinkedHashMap<Int, StringBuilder>()
            for (bl in blocks) if (bl is Para) for (s in bl.segs) {
                if (s.verse == 0) continue
                val sb = out.getOrPut(s.verse) { StringBuilder() }
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(s.text)
            }
            return out.map { it.key to it.value.toString() }
        }

        /**
         * Where [query]'s words occur in [text], matched as [search] matches them (folded, so
         * "lords" finds "Lord's"). Ranges index into [text], sorted, overlaps merged.
         */
        fun matches(text: String, query: String): List<IntRange> {
            val words = fold(query).split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList()
            // Fold [text] the way [fold] does, remembering which character each folded one came from.
            val folded = StringBuilder(text.length)
            val from = ArrayList<Int>(text.length)
            var space = true
            for (i in text.indices) {
                for (ch in Normalizer.normalize(text[i].lowercase(), Normalizer.Form.NFD)) {
                    when {
                        Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> {}
                        ch == '’' || ch == '\'' -> {}
                        ch.isLetterOrDigit() -> { folded.append(ch); from += i; space = false }
                        !space -> { folded.append(' '); from += i; space = true }
                    }
                }
            }
            val found = ArrayList<IntRange>()
            for (w in words) {
                var at = folded.indexOf(w)
                while (at >= 0) {
                    found += from[at]..from[at + w.length - 1]
                    at = folded.indexOf(w, at + 1)
                }
            }
            found.sortBy { it.first }
            val merged = ArrayList<IntRange>()
            for (r in found) {
                val last = merged.lastOrNull()
                if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
                else merged += r
            }
            return merged
        }

        /** Lower case, accents and punctuation removed, so "Lord's" matches "lords". */
        fun fold(s: String): String {
            val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            val sb = StringBuilder(d.length)
            var space = true
            for (ch in d) {
                when {
                    Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> {}
                    ch == '’' || ch == '\'' -> {}
                    ch.isLetterOrDigit() -> { sb.append(ch); space = false }
                    !space -> { sb.append(' '); space = true }
                }
            }
            return sb.toString().trim()
        }
    }
}
