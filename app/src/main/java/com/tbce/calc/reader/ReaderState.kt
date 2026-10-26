package com.tbce.calc.reader

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbce.calc.vault.VaultSession
import org.json.JSONObject
import java.io.File

enum class ReaderTheme { PAPER, SEPIA, DARK, BLACK }
enum class Tab { READ, SAVED, SEARCH, SETTINGS }

/** Verses opened from Search, shown marked in the reader; [query]'s words are picked out in them. */
data class Mark(val book: String, val chapter: Int, val verses: IntRange, val query: String)

/** The last search: matching verses (first [hits], [count] in all) and Strong's [entries]. */
class SearchResults(val query: String, val translation: String, val hits: List<Hit>, val count: Int, val entries: List<Strongs.Match>)

/** Verses picked in the reader, in order; they need not be next to each other. [last] is the latest tap. */
data class Selection(val book: String, val chapter: Int, val verses: List<Int>, val last: Int)

/** Full-screen layers above the tabs. */
sealed interface Overlay {
    /** Text editor for any kind of note. [verse] is non-null for chapter footnotes (0 = no verse tag). */
    class Editor(
        val title: String,
        val initial: String,
        val verse: Int?,
        val maxVerse: Int,
        val onSave: (String, Int) -> Unit,
        val onDelete: (() -> Unit)?,
        /** Layer to return to when the editor closes. */
        val back: Overlay? = null,
    ) : Overlay
    class SavedDetail(val id: String) : Overlay
    /** Passphrase for a backup. [file] holds the picked file's bytes when importing. */
    class Passphrase(val export: Boolean, val file: ByteArray? = null) : Overlay
    data object ChangeCode : Overlay
    /** The original-language words of the selected verses (Strong's numbers, from the KJV). */
    class Words(val book: String, val chapter: Int, val verses: List<Int>) : Overlay
    /** One Strong's entry and every verse that uses it. [back] is the layer to return to. */
    class Lexicon(val number: String, val back: Overlay? = null) : Overlay
}

/**
 * Everything the reader holds while open. Lives only while unlocked: [dispose] drops the
 * decrypted text, the search index and the font file.
 */
class ReaderState(context: Context, private val session: VaultSession, systemDark: Boolean) {
    private val assets = context.assets
    val packs = Packs { name -> assets.open("r/$name").use { it.readBytes() } }
    val meta = packs.meta()
    val references = ReferenceParser(meta.aliases, meta.translations[0].books.associate { it.code to it.chapters })

    private val fontFile = File(context.cacheDir, "f0")
    val typeface: Typeface? = try {
        fontFile.writeBytes(packs.bytes("font"))
        if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(fontFile).setFontVariationSettings("'wght' 400").build()
        else Typeface.createFromFile(fontFile)
    } catch (e: Exception) {
        null
    }
    val boldTypeface: Typeface? = try {
        if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(fontFile).setFontVariationSettings("'wght' 600").build()
        else typeface?.let { Typeface.create(it, Typeface.BOLD) }
    } catch (e: Exception) {
        null
    }

    var tab by mutableStateOf(Tab.READ)
    var selection by mutableStateOf<Selection?>(null)
    var overlay by mutableStateOf<Overlay?>(null)
    /** A short message shown in a dialog (export done, import result, code changed). */
    var notice by mutableStateOf<String?>(null)
    val annotations = Annotations { bytes -> session.put(ANNOTATIONS, bytes) }
    var theme by mutableStateOf(if (systemDark) ReaderTheme.DARK else ReaderTheme.PAPER)
    var fontSize by mutableFloatStateOf(19f)
    var lineSpacing by mutableFloatStateOf(1.6f)
    var translationId by mutableStateOf(meta.translations[0].id)

    /** Global chapter index shown in the reader. */
    var position by mutableIntStateOf(0)
    /** Verse to scroll to once the chapter is on screen; 0 when none. */
    var pendingVerse by mutableIntStateOf(0)
    private val positions = HashMap<String, Int>()

    val translation: Translation get() = meta.translations.first { it.id == translationId }

    private val cache = LinkedHashMap<String, Book>(8, 0.75f, true)

    fun book(translationId: String, code: String): Book = synchronized(cache) {
        cache.getOrPut("$translationId/$code") { packs.book(translationId, code) }.also {
            while (cache.size > 6) cache.remove(cache.keys.first())
        }
    }

    private var strongsData: Strongs? = null

    /** What is typed in the Search tab, and where its results are scrolled, kept while switching tabs. */
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<SearchResults?>(null)
    val searchList = LazyListState()

    /** Verses to mark in the reader after opening a search result; cleared on swiping to another chapter. */
    var mark by mutableStateOf<Mark?>(null)

    /** Screens Back returns to, newest last: left by switching tabs and by jumps to a passage. */
    private class Screen(val tab: Tab, val position: Int, val overlay: Overlay?)
    private val history = mutableStateListOf<Screen>()
    val canGoBack get() = history.isNotEmpty()

    private fun push() {
        history += Screen(tab, position, overlay)
        if (history.size > MAX_HISTORY) history.removeAt(0)
    }

    /** Switches tab, remembering the current screen for Back. */
    fun open(t: Tab) {
        if (t == tab) return
        push()
        selection = null
        tab = t
    }

    /** Returns to the previous screen; false when there is none. */
    fun back(): Boolean {
        if (history.isEmpty()) return false
        val s = history.removeAt(history.lastIndex)
        selection = null
        pendingVerse = 0
        tab = s.tab
        position = s.position
        overlay = s.overlay
        return true
    }

    /** Strong's numbers, loaded on first use; dropped with the rest on lock. */
    fun strongs(): Strongs = synchronized(this) {
        strongsData ?: Strongs(packs, meta.translations.first { it.id == "kjv" }.books.map { it.code }).also { strongsData = it }
    }

    private var index: SearchIndex? = null
    private var indexFor: String? = null

    /** Builds the search index for the current translation if needed (slow; call off the main thread). */
    fun searchIndex(): SearchIndex = synchronized(this) {
        val t = translation
        if (indexFor != t.id) {
            index = null
            index = SearchIndex.build(t) { code -> packs.book(t.id, code) }
            indexFor = t.id
        }
        index!!
    }

    /**
     * Opens [ref] in the reader. With [query] (from Search; "" for a reference or a lexicon verse)
     * its verses are marked and the query's words picked out in them.
     */
    fun goTo(ref: Ref, query: String? = null) {
        val t = translation
        val b = t.bookIndex(ref.book)
        if (b < 0) return
        push()
        mark = if (query != null && ref.verse > 0) Mark(ref.book, ref.chapter, ref.verse..maxOf(ref.verse, ref.verseEnd), query) else null
        position = t.globalIndex(b, ref.chapter.coerceIn(1, t.books[b].chapters))
        pendingVerse = ref.verse
        selection = null
        overlay = null
        tab = Tab.READ
    }

    fun switchTranslation(id: String) {
        if (id == translationId) return
        positions[translationId] = position
        translationId = id
        positions[id] = position
    }

    fun location(global: Int = position): Pair<BookMeta, Int> {
        val (b, c) = translation.locate(global)
        return translation.books[b] to c
    }

    init {
        load()
    }

    private fun load() {
        try {
            session.get(ANNOTATIONS)?.let { annotations.load(it); it.fill(0) }
        } catch (_: Exception) {
        }
        val raw = try { session.get(SETTINGS) } catch (e: Exception) { null } ?: return
        try {
            val o = JSONObject(String(raw, Charsets.UTF_8))
            o.optString("theme").takeIf { it.isNotEmpty() }?.let { theme = ReaderTheme.valueOf(it) }
            fontSize = o.optDouble("fontSize", fontSize.toDouble()).toFloat()
            lineSpacing = o.optDouble("lineSpacing", lineSpacing.toDouble()).toFloat()
            o.optString("translation").takeIf { id -> meta.translations.any { it.id == id } }?.let { translationId = it }
            o.optJSONObject("positions")?.let { p -> p.keys().forEach { positions[it] = p.getInt(it) } }
            position = (positions[translationId] ?: 0).coerceIn(0, translation.totalChapters - 1)
        } catch (_: Exception) {
        } finally {
            raw.fill(0)
        }
    }

    fun save() {
        positions[translationId] = position
        val o = JSONObject()
            .put("theme", theme.name)
            .put("fontSize", fontSize.toDouble())
            .put("lineSpacing", lineSpacing.toDouble())
            .put("translation", translationId)
            .put("positions", JSONObject(positions as Map<*, *>))
        session.put(SETTINGS, o.toString().toByteArray(Charsets.UTF_8))
    }

    /** Text of [verses] of a chapter (empty = whole chapter) in translation [tid]; "…" marks skipped verses. */
    fun verseText(tid: String, book: String, chapter: Int, verses: List<Int>): String = try {
        val all = SearchIndex.verseTexts(book(tid, book).chapters[chapter - 1])
        if (verses.isEmpty()) all.joinToString(" ") { it.second }
        else buildString {
            var prev = -1
            for ((v, t) in all) if (v in verses) {
                if (isNotEmpty()) append(if (prev >= 0 && v != prev + 1) " … " else " ")
                append(t)
                prev = v
            }
        }
    } catch (e: Exception) {
        ""
    }

    fun verseCount(book: String, chapter: Int): Int = try {
        SearchIndex.verseTexts(book(translationId, book).chapters[chapter - 1]).maxOfOrNull { it.first } ?: 1
    } catch (e: Exception) {
        1
    }

    fun bookName(code: String) = translation.books.firstOrNull { it.code == code }?.name ?: code

    /** "John 3", "John 3:16", "John 3:16–18" or "John 3:16, 18, 20–21". */
    fun label(book: String, chapter: Int, verses: List<Int> = emptyList()): String =
        "${bookName(book)} $chapter" + if (verses.isEmpty()) "" else ":" + verseList(verses)

    fun dispose() {
        annotations.clear()
        selection = null
        overlay = null
        mark = null
        searchQuery = ""
        searchResults = null
        history.clear()
        synchronized(cache) { cache.clear() }
        synchronized(this) { index = null; indexFor = null; strongsData = null }
        fontFile.delete()
    }

    companion object {
        /** Sorted verse numbers as compact ranges: [16, 18, 20, 21] -> "16, 18, 20–21". */
        fun verseList(verses: List<Int>): String {
            val v = verses.distinct().sorted()
            val parts = ArrayList<String>()
            var i = 0
            while (i < v.size) {
                var j = i
                while (j + 1 < v.size && v[j + 1] == v[j] + 1) j++
                parts += if (j == i) "${v[i]}" else "${v[i]}–${v[j]}"
                i = j + 1
            }
            return parts.joinToString(", ")
        }

        private const val MAX_HISTORY = 50
        private const val SETTINGS = "settings"
        private const val ANNOTATIONS = "notes"
    }
}
