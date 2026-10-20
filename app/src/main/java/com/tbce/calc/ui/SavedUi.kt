package com.tbce.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbce.calc.reader.Annotations
import com.tbce.calc.reader.Overlay
import com.tbce.calc.reader.ReaderState
import com.tbce.calc.reader.Ref
import com.tbce.calc.reader.SearchIndex

private enum class Sort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), BOOK("Book order") }

private fun ReaderState.refOf(s: Annotations.Saved) = label(s.book, s.chapter, s.verses)

private fun ReaderState.textOf(s: Annotations.Saved, tid: String = translationId) =
    verseText(tid, s.book, s.chapter, s.verses)

/** Note bodies on a saved item: its own notes, then reader notes on the same verses or chapter. */
private fun Annotations.noteBodies(s: Annotations.Saved) =
    s.notes.map { it.body } + notesFor(s).map { it.body } + footnotesFor(s).map { it.body }

private enum class Section(val label: String) { VERSES("Verses"), NOTES("Notes") }

/** Every verse note and chapter note, so the Notes section lists them without saving anything. */
private class NoteRow(
    val id: String, val chapterNote: Boolean, val book: String, val chapter: Int,
    val verses: List<Int>, val body: String, val created: Long,
)

/** The Saved tab: a Verses section (saved items) and a Notes section (every note), with filter and sort. */
@Composable
fun SavedPane(state: ReaderState, c: ReaderColors, touch: () -> Unit) {
    val a = state.annotations
    val version = a.version
    var section by remember { mutableStateOf(Section.VERSES) }
    var filter by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Sort.NEWEST) }
    val family = state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif
    val order = remember(state.translationId) { state.translation.books.mapIndexed { i, b -> b.code to i }.toMap() }
    val q = SearchIndex.fold(filter)
    val items = remember(version, q, sort, state.translationId) {
        a.saved
            .filter { s ->
                q.isEmpty() || SearchIndex.fold(state.refOf(s) + " " + s.label + " " + a.noteBodies(s).joinToString(" ") + " " + state.textOf(s))
                    .contains(q)
            }
            .let { list ->
                when (sort) {
                    Sort.NEWEST -> list.sortedByDescending { it.created }
                    Sort.OLDEST -> list.sortedBy { it.created }
                    Sort.BOOK -> list.sortedWith(compareBy({ order[it.book] ?: 99 }, { it.chapter }, { it.vStart }))
                }
            }
    }
    val notes = remember(version, q, sort, state.translationId) {
        (a.allNotes.map { NoteRow(it.id, false, it.book, it.chapter, it.verses, it.body, it.created) } +
            a.allFootnotes.map { NoteRow(it.id, true, it.book, it.chapter, if (it.verse > 0) listOf(it.verse) else emptyList(), it.body, it.created) })
            .filter { n -> q.isEmpty() || SearchIndex.fold(state.label(n.book, n.chapter, n.verses) + " " + n.body).contains(q) }
            .let { list ->
                when (sort) {
                    Sort.NEWEST -> list.sortedByDescending { it.created }
                    Sort.OLDEST -> list.sortedBy { it.created }
                    Sort.BOOK -> list.sortedWith(compareBy({ order[it.book] ?: 99 }, { it.chapter }, { it.verses.firstOrNull() ?: 0 }))
                }
            }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)) {
            for (sec in Section.entries) {
                val on = section == sec
                val count = if (sec == Section.VERSES) a.saved.size else a.allNotes.size + a.allFootnotes.size
                Text(
                    "${sec.label}  $count",
                    color = if (on) c.bg else c.text,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) c.accent else Color.Transparent)
                        .clickable { section = sec }.padding(vertical = 10.dp),
                )
            }
        }
        val empty = if (section == Section.VERSES) a.saved.isEmpty() else a.allNotes.isEmpty() && a.allFootnotes.isEmpty()
        if (empty) {
            Text(
                if (section == Section.VERSES) "Nothing saved yet. Tap verses in the reader and choose Save, or use Save at the end of a ${state.meta.ui("chapter").lowercase()}."
                else "No notes yet. Tap verses in the reader and choose Note, or add a note at the end of a ${state.meta.ui("chapter").lowercase()}. They show up here on their own.",
                color = c.dim, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(24.dp),
            )
            return
        }
        PrivateField(
            hint = if (section == Section.VERSES) "Filter saved verses" else "Filter notes",
            c = c,
            onChange = { filter = it; touch() },
            autoFocus = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.padding(horizontal = 12.dp)) {
            for (s in Sort.entries) {
                TextButton(onClick = { sort = s }) {
                    Text(s.label, color = if (sort == s) c.accent else c.dim, fontWeight = if (sort == s) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (section == Section.VERSES) items(items, key = { it.id }) { s ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .clickable { state.overlay = Overlay.SavedDetail(s.id) }
                        .padding(16.dp),
                ) {
                    Text(state.refOf(s), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (s.label.isNotBlank()) Text(s.label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        state.textOf(s), color = c.text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = family,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                    val noteCount = a.noteBodies(s).size
                    if (noteCount > 0) {
                        Text(if (noteCount == 1) "1 note" else "$noteCount notes", color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else items(notes, key = { it.id }) { n ->
                val ref = state.label(n.book, n.chapter, n.verses)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .clickable { state.goTo(Ref(n.book, n.chapter, n.verses.firstOrNull() ?: 0)) }
                        .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp),
                ) {
                    Text(
                        if (n.chapterNote) "$ref · ${state.meta.ui("chapter").lowercase()} note" else ref,
                        color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                    if (n.verses.isNotEmpty()) {
                        Text(
                            state.verseText(state.translationId, n.book, n.chapter, n.verses), color = c.dim, fontSize = 14.sp, lineHeight = 19.sp,
                            fontFamily = family, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(n.body, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { if (n.chapterNote) editChapterNote(state, n.book, n.chapter, n.id) else openNote(state, n.id) }) {
                            Text("Edit", color = c.dim)
                        }
                        TextButton(onClick = { state.goTo(Ref(n.book, n.chapter, n.verses.firstOrNull() ?: 0)) }) {
                            Text("Open in reader ›", color = c.accent)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** One saved item: its text, label, notes, and actions. */
@Composable
fun SavedDetailOverlay(state: ReaderState, d: Overlay.SavedDetail, c: ReaderColors, touch: () -> Unit) {
    val a = state.annotations
    val version = a.version
    val s = remember(version) { a.savedItem(d.id) }
    val close = { state.overlay = null }
    BackHandler(onBack = close)
    if (s == null) { close(); return }
    var original by remember { mutableStateOf(false) }
    val tid = if (original) s.translation else state.translationId
    val abbr = state.meta.translations.firstOrNull { it.id == tid }?.abbr ?: tid
    val family = state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("‹ Saved", color = c.accent) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                state.goTo(Ref(s.book, s.chapter, s.vStart))
            }) { Text("Open in reader", color = c.accent) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(state.refOf(s), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(abbr, color = c.dim, fontSize = 13.sp)
                if (s.translation != state.translationId) {
                    val other = if (original) state.translation.abbr else (state.meta.translations.firstOrNull { it.id == s.translation }?.abbr ?: s.translation)
                    TextButton(onClick = { original = !original }) {
                        Text(if (original) "Show in $other" else "Show in $other (as saved)", color = c.accent, fontSize = 13.sp)
                    }
                }
            }
            Text(
                state.textOf(s, tid), color = c.text, fontFamily = family,
                fontSize = state.fontSize.sp, lineHeight = (state.fontSize * state.lineSpacing).sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("LABEL", color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp)
            PrivateField(
                hint = "Add a label",
                c = c,
                onChange = { a.setLabel(s.id, it); touch() },
                initial = s.label,
                autoFocus = false,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Text("NOTES", color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 12.dp))
            for (n in s.notes.sortedBy { it.created }) {
                Text(
                    n.body, color = c.text, fontSize = 16.sp, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        state.overlay = Overlay.Editor(
                            title = state.refOf(s), initial = n.body, verse = null, maxVerse = 0,
                            onSave = { body, _ -> if (body.isBlank()) a.deleteSavedNote(s.id, n.id) else a.updateSavedNote(s.id, n.id, body) },
                            onDelete = { a.deleteSavedNote(s.id, n.id) },
                            back = d,
                        )
                    }.padding(vertical = 10.dp),
                )
                HorizontalDivider(color = c.dim.copy(alpha = 0.15f))
            }
            // Notes made in the reader on these verses or this chapter, so they show here too.
            val chapterWord = state.meta.ui("chapter").lowercase()
            for (n in remember(version) { a.notesFor(s) }) {
                ReaderNoteRow(state.label(n.book, n.chapter, n.verses), n.body, c) { openNote(state, n.id, back = d) }
                HorizontalDivider(color = c.dim.copy(alpha = 0.15f))
            }
            for (f in remember(version) { a.footnotesFor(s) }) {
                val at = if (f.verse > 0) state.label(f.book, f.chapter, listOf(f.verse)) else state.label(f.book, f.chapter)
                ReaderNoteRow("$at · $chapterWord note", f.body, c) { editChapterNote(state, f.book, f.chapter, f.id, back = d) }
                HorizontalDivider(color = c.dim.copy(alpha = 0.15f))
            }
            TextButton(onClick = {
                state.overlay = Overlay.Editor(
                    title = state.refOf(s), initial = "", verse = null, maxVerse = 0,
                    onSave = { body, _ -> if (body.isNotBlank()) a.addSavedNote(s.id, body) },
                    onDelete = null,
                    back = d,
                )
            }) { Text("+ Add a note", color = c.accent) }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { a.deleteSaved(s.id); close() }) { Text("Remove from Saved", color = Color(0xFFC0392B)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A note made in the reader, shown on a saved item with the verses it is on. */
@Composable
private fun ReaderNoteRow(ref: String, body: String, c: ReaderColors, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(ref, color = c.dim, fontSize = 13.sp)
        Text(body, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
