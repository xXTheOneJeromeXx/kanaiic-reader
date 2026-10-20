package com.tbce.calc.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Highlights, notes, chapter footnotes, Saved items and recent passages. Everything is
 * anchored to (book, chapter, verse) so it shows in every translation (brief section 6).
 * Kept as one encrypted document in the vault; [persist] is called after every change.
 */
class Annotations(private val persist: (ByteArray) -> Unit = {}) {

    /** A note on one or more verses of a chapter; [verses] is sorted and need not be contiguous. */
    class Note(val id: String, val book: String, val chapter: Int, val verses: List<Int>,
               var body: String, val created: Long, var updated: Long) {
        val vStart get() = verses.first()
        val vEnd get() = verses.last()
    }

    class Footnote(val id: String, val book: String, val chapter: Int, var verse: Int,
                   var body: String, val created: Long, var updated: Long)

    enum class Kind { VERSE, RANGE, CHAPTER }

    class SavedNote(val id: String, var body: String, val created: Long, var updated: Long)

    /** [verses] is empty for a whole chapter, otherwise sorted and need not be contiguous. */
    class Saved(val id: String, val kind: Kind, val book: String, val chapter: Int, val verses: List<Int>,
                var label: String, val translation: String, val created: Long, val notes: MutableList<SavedNote>) {
        val vStart get() = verses.firstOrNull() ?: 0
    }

    class Recent(val book: String, val chapter: Int)

    /** Bumped on every change so the UI redraws. */
    var version by mutableIntStateOf(0)
        private set

    private val highlights = HashMap<String, Int>()
    private val notes = ArrayList<Note>()
    private val footnotes = ArrayList<Footnote>()
    val saved = ArrayList<Saved>()
    val recent = ArrayList<Recent>()

    // ---- highlights (one per verse, global across translations)

    fun highlight(book: String, chapter: Int, verse: Int): Int? = highlights[key(book, chapter, verse)]

    /** Sets [color] (0..4) on each of [verses], or removes their highlights when [color] is null. */
    fun setHighlight(book: String, chapter: Int, verses: Collection<Int>, color: Int?) {
        for (v in verses) {
            if (color == null) highlights.remove(key(book, chapter, v)) else highlights[key(book, chapter, v)] = color
        }
        changed()
    }

    // ---- verse and range notes

    val allNotes: List<Note> get() = notes.toList()
    val allFootnotes: List<Footnote> get() = footnotes.toList()

    fun notesIn(book: String, chapter: Int) = notes.filter { it.book == book && it.chapter == chapter }

    fun note(id: String) = notes.firstOrNull { it.id == id }

    fun addNote(book: String, chapter: Int, verses: Collection<Int>, body: String): Note {
        val now = now()
        return Note(newId(), book, chapter, verses.distinct().sorted(), body, now, now).also { notes += it; changed() }
    }

    fun updateNote(id: String, body: String) {
        note(id)?.let { it.body = body; it.updated = now(); changed() }
    }

    fun deleteNote(id: String) {
        if (notes.removeAll { it.id == id }) changed()
    }

    // ---- chapter footnotes (several per chapter, oldest first)

    fun footnotesIn(book: String, chapter: Int) =
        footnotes.filter { it.book == book && it.chapter == chapter }.sortedBy { it.created }

    fun footnote(id: String) = footnotes.firstOrNull { it.id == id }

    fun addFootnote(book: String, chapter: Int, verse: Int, body: String): Footnote {
        val now = now()
        return Footnote(newId(), book, chapter, verse, body, now, now).also { footnotes += it; changed() }
    }

    fun updateFootnote(id: String, verse: Int, body: String) {
        footnote(id)?.let { it.verse = verse; it.body = body; it.updated = now(); changed() }
    }

    fun deleteFootnote(id: String) {
        if (footnotes.removeAll { it.id == id }) changed()
    }

    // ---- Saved

    fun savedItem(id: String) = saved.firstOrNull { it.id == id }

    fun save(kind: Kind, book: String, chapter: Int, verses: Collection<Int>, translation: String): Saved {
        val vs = if (kind == Kind.CHAPTER) emptyList() else verses.distinct().sorted()
        saved.firstOrNull { it.kind == kind && it.book == book && it.chapter == chapter && it.verses == vs }
            ?.let { return it }
        return Saved(newId(), kind, book, chapter, vs, "", translation, now(), ArrayList()).also { saved += it; changed() }
    }

    fun setLabel(id: String, label: String) {
        savedItem(id)?.let { it.label = label; changed() }
    }

    fun deleteSaved(id: String) {
        if (saved.removeAll { it.id == id }) changed()
    }

    fun addSavedNote(id: String, body: String) {
        val now = now()
        savedItem(id)?.let { it.notes += SavedNote(newId(), body, now, now); changed() }
    }

    fun updateSavedNote(id: String, noteId: String, body: String) {
        savedItem(id)?.notes?.firstOrNull { it.id == noteId }?.let { it.body = body; it.updated = now(); changed() }
    }

    fun deleteSavedNote(id: String, noteId: String) {
        if (savedItem(id)?.notes?.removeAll { it.id == noteId } == true) changed()
    }

    /** Reader notes on [s]: every verse note in a saved chapter, or those touching one of the saved verses. */
    fun notesFor(s: Saved): List<Note> =
        notesIn(s.book, s.chapter).filter { n -> s.kind == Kind.CHAPTER || n.verses.any { it in s.verses } }.sortedBy { it.vStart }

    /** Chapter notes on [s]: all of them for a saved chapter, otherwise those set at one of the saved verses. */
    fun footnotesFor(s: Saved): List<Footnote> =
        footnotesIn(s.book, s.chapter).filter { s.kind == Kind.CHAPTER || it.verse in s.verses }

    // ---- recent passages (most recent first, no repeats)

    fun visit(book: String, chapter: Int) {
        if (recent.firstOrNull()?.let { it.book == book && it.chapter == chapter } == true) return
        recent.removeAll { it.book == book && it.chapter == chapter }
        recent.add(0, Recent(book, chapter))
        while (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex)
        changed()
    }

    // ---- storage

    private fun changed() {
        version++
        persist(toBytes())
    }

    fun toBytes(): ByteArray {
        val o = JSONObject()
        o.put("v", 1)
        o.put("h", JSONObject(highlights as Map<*, *>))
        o.put("n", JSONArray(notes.map {
            JSONArray(listOf(it.id, it.book, it.chapter, it.vStart, it.vEnd, it.body, it.created, it.updated, JSONArray(it.verses)))
        }))
        o.put("f", JSONArray(footnotes.map {
            JSONArray(listOf(it.id, it.book, it.chapter, it.verse, it.body, it.created, it.updated))
        }))
        o.put("s", JSONArray(saved.map { s ->
            JSONObject()
                .put("id", s.id).put("k", s.kind.name).put("b", s.book).put("c", s.chapter)
                .put("vs", JSONArray(s.verses)).put("l", s.label).put("t", s.translation).put("at", s.created)
                .put("n", JSONArray(s.notes.map { JSONArray(listOf(it.id, it.body, it.created, it.updated)) }))
        }))
        o.put("r", JSONArray(recent.map { JSONArray(listOf(it.book, it.chapter)) }))
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    fun load(bytes: ByteArray) {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        highlights.clear(); notes.clear(); footnotes.clear(); saved.clear(); recent.clear()
        o.optJSONObject("h")?.let { h -> h.keys().forEach { highlights[it] = h.getInt(it) } }
        o.optJSONArray("n")?.let { a ->
            for (i in 0 until a.length()) a.getJSONArray(i).let {
                // 0.3.0 stored only a start and end verse; later versions add the verse list.
                val verses = it.optJSONArray(8)?.let { a -> (0 until a.length()).map(a::getInt) } ?: (it.getInt(3)..it.getInt(4)).toList()
                notes += Note(it.getString(0), it.getString(1), it.getInt(2), verses, it.getString(5), it.getLong(6), it.getLong(7))
            }
        }
        o.optJSONArray("f")?.let { a ->
            for (i in 0 until a.length()) a.getJSONArray(i).let {
                footnotes += Footnote(it.getString(0), it.getString(1), it.getInt(2), it.getInt(3), it.getString(4), it.getLong(5), it.getLong(6))
            }
        }
        o.optJSONArray("s")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { s ->
                val ns = s.getJSONArray("n").let { n ->
                    (0 until n.length()).map { j -> n.getJSONArray(j).let { SavedNote(it.getString(0), it.getString(1), it.getLong(2), it.getLong(3)) } }
                }
                val kind = Kind.valueOf(s.getString("k"))
                val verses = s.optJSONArray("vs")?.let { a -> (0 until a.length()).map(a::getInt) }
                    ?: if (kind == Kind.CHAPTER) emptyList() else (s.getInt("v1")..s.getInt("v2")).toList()
                saved += Saved(s.getString("id"), kind, s.getString("b"), s.getInt("c"),
                    verses, s.getString("l"), s.getString("t"), s.getLong("at"), ns.toMutableList())
            }
        }
        o.optJSONArray("r")?.let { a ->
            for (i in 0 until a.length()) a.getJSONArray(i).let { recent += Recent(it.getString(0), it.getInt(1)) }
        }
        version++
    }

    /**
     * Adds what [other] has that this does not (import from a backup). Nothing here is
     * overwritten: a verse already highlighted keeps its color, and items are matched by id.
     * Returns how many items were added.
     */
    fun merge(other: Annotations): Int {
        var added = 0
        for ((k, v) in other.highlights) if (k !in highlights) { highlights[k] = v; added++ }
        for (n in other.notes) if (notes.none { it.id == n.id }) { notes += n; added++ }
        for (f in other.footnotes) if (footnotes.none { it.id == f.id }) { footnotes += f; added++ }
        for (s in other.saved) {
            val mine = saved.firstOrNull { it.id == s.id }
            if (mine == null) { saved += s; added++ }
            else for (n in s.notes) if (mine.notes.none { it.id == n.id }) { mine.notes += n; added++ }
        }
        if (added > 0) changed()
        return added
    }

    /** Drops everything from memory (on lock). */
    fun clear() {
        highlights.clear(); notes.clear(); footnotes.clear(); saved.clear(); recent.clear()
    }

    companion object {
        const val COLORS = 5
        const val MAX_RECENT = 8
        private val random = SecureRandom()
        private fun key(book: String, chapter: Int, verse: Int) = "$book.$chapter.$verse"
        private fun newId() = java.lang.Long.toHexString(random.nextLong())
        private fun now() = System.currentTimeMillis()
    }
}
