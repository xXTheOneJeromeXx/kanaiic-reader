package com.tbce.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tbce.calc.reader.Annotations
import com.tbce.calc.reader.Overlay
import com.tbce.calc.reader.Para
import com.tbce.calc.reader.ReaderState
import com.tbce.calc.reader.Selection

/** The five highlight tints, softer on dark themes. */
fun highlightColor(i: Int, dark: Boolean): Color {
    val base = listOf(0xFFF2D65C, 0xFF8FD18A, 0xFF8EC1F0, 0xFFF0A3C4, 0xFFF4B26B)[i.coerceIn(0, 4)]
    return Color(base).copy(alpha = if (dark) 0.32f else 0.45f)
}

private const val TAG_VERSE = "v"
private const val TAG_NOTE = "n"

/**
 * One paragraph of text. Tapping a verse selects it (or extends the selection); tapping a
 * note marker opens that note. Highlights and the selection are drawn behind the text.
 */
@Composable
fun ParaText(p: Para, book: String, chapter: Int, base: TextStyle, c: ReaderColors, size: Float, state: ReaderState) {
    val a = state.annotations
    val version = a.version
    val sel = state.selection?.takeIf { it.book == book && it.chapter == chapter }
    val notes = remember(version, book, chapter) { a.notesIn(book, chapter) }
    val indent = when (p.style) {
        "p" -> TextIndent(firstLine = 1.2.em)
        "pi" -> TextIndent(firstLine = 2.2.em, restLine = 1.2.em)
        "q1" -> TextIndent(firstLine = 0.em, restLine = 2.em)
        "q2" -> TextIndent(firstLine = 1.2.em, restLine = 2.4.em)
        "q3" -> TextIndent(firstLine = 2.2.em, restLine = 3.em)
        "l1" -> TextIndent(firstLine = 0.em, restLine = 1.2.em)
        "l2" -> TextIndent(firstLine = 1.2.em, restLine = 2.4.em)
        else -> TextIndent.None
    }
    val align = when (p.style) {
        "qr" -> TextAlign.End
        "pc" -> TextAlign.Center
        else -> TextAlign.Start
    }
    val text = remember(p, c, version, sel) {
        buildAnnotatedString {
            withStyle(ParagraphStyle(textIndent = indent, textAlign = align)) {
                p.segs.forEachIndexed { i, s ->
                    val hl = a.highlight(book, chapter, s.verse)
                    val hlBg = hl?.let { highlightColor(it, c.dark) } ?: Color.Unspecified
                    if (i > 0) {
                        // The gap between two verses with the same highlight is filled too.
                        val prev = a.highlight(book, chapter, p.segs[i - 1].verse)
                        withStyle(SpanStyle(background = if (hl != null && hl == prev) hlBg else Color.Unspecified)) { append(' ') }
                    }
                    val start = length
                    if (s.start) {
                        withStyle(SpanStyle(fontSize = 0.62.em, baselineShift = BaselineShift(0.35f), color = c.accent, fontFamily = FontFamily.SansSerif, background = hlBg)) {
                            append(s.verse.toString())
                        }
                        withStyle(SpanStyle(background = hlBg)) { append('\u2009') }
                    }
                    val selected = sel != null && s.verse in sel.verses
                    val style = SpanStyle(
                        background = hlBg,
                        textDecoration = if (selected) TextDecoration.Underline else null,
                    )
                    withStyle(style) { append(s.text) }
                    if (s.verse > 0) addStringAnnotation(TAG_VERSE, s.verse.toString(), start, length)
                    // Note markers sit after the last verse a note covers.
                    val isLastSeg = p.segs.drop(i + 1).none { it.verse == s.verse }
                    if (isLastSeg) for (n in notes.filter { it.vEnd == s.verse }) {
                        val m = length
                        append('\u2009')
                        withStyle(SpanStyle(color = c.accent, fontSize = 0.8.em, fontFamily = FontFamily.SansSerif)) { append("●") }
                        addStringAnnotation(TAG_NOTE, n.id, m, length)
                    }
                }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val top = if (p.style == "p" || p.style == "m" || p.style == "pi") (size * 0.35f).dp else 0.dp
    Text(
        text,
        style = base.copy(fontStyle = if (p.style == "d") FontStyle.Italic else FontStyle.Normal),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = top)
            .pointerInput(p, book, chapter) {
                fun at(pos: androidx.compose.ui.geometry.Offset) = layout?.let { l ->
                    val off = l.getOffsetForPosition(pos)
                    text.getStringAnnotations(maxOf(0, off - 1), minOf(text.length, off + 1))
                } ?: emptyList()
                detectTapGestures(
                    onTap = { pos ->
                        val all = at(pos)
                        all.firstOrNull { it.tag == TAG_NOTE }?.let { openNote(state, it.item); return@detectTapGestures }
                        all.firstOrNull { it.tag == TAG_VERSE }?.item?.toIntOrNull()?.let { tapVerse(state, book, chapter, it) }
                    },
                    onLongPress = { pos ->
                        at(pos).firstOrNull { it.tag == TAG_VERSE }?.item?.toIntOrNull()?.let { holdVerse(state, book, chapter, it) }
                    },
                )
            },
    )
}

/** A tap adds a verse to the selection, or takes it out if it is already in. */
private fun tapVerse(state: ReaderState, book: String, chapter: Int, v: Int) {
    val s = state.selection?.takeIf { it.book == book && it.chapter == chapter }
    state.selection = when {
        s == null -> Selection(book, chapter, listOf(v), v)
        v in s.verses -> (s.verses - v).takeIf { it.isNotEmpty() }?.let { s.copy(verses = it) }
        else -> Selection(book, chapter, (s.verses + v).sorted(), v)
    }
}

/** A long press adds every verse from the last one tapped through this one. */
private fun holdVerse(state: ReaderState, book: String, chapter: Int, v: Int) {
    val s = state.selection?.takeIf { it.book == book && it.chapter == chapter }
    state.selection = if (s == null) Selection(book, chapter, listOf(v), v)
    else Selection(book, chapter, (s.verses + (minOf(s.last, v)..maxOf(s.last, v))).distinct().sorted(), v)
}

fun openNote(state: ReaderState, id: String, back: Overlay? = null) {
    val n = state.annotations.note(id) ?: return
    state.overlay = Overlay.Editor(
        title = state.label(n.book, n.chapter, n.verses),
        initial = n.body,
        verse = null,
        maxVerse = 0,
        onSave = { body, _ -> if (body.isBlank()) state.annotations.deleteNote(id) else state.annotations.updateNote(id, body) },
        onDelete = { state.annotations.deleteNote(id) },
        back = back,
    )
}

/** Shown instead of the tab bar while verses are selected. */
@Composable
fun SelectionBar(state: ReaderState, c: ReaderColors) {
    val s = state.selection ?: return
    val a = state.annotations
    HorizontalDivider(color = c.dim.copy(alpha = 0.2f))
    Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.label(s.book, s.chapter, s.verses), maxLines = 2, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            for (i in 0 until Annotations.COLORS) {
                Box(
                    Modifier.padding(horizontal = 4.dp).size(30.dp).clip(CircleShape)
                        .background(highlightColor(i, c.dark).copy(alpha = 0.9f))
                        .clickable { a.setHighlight(s.book, s.chapter, s.verses, i); state.selection = null },
                )
            }
            Box(
                Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).border(1.dp, c.dim, CircleShape)
                    .clickable { a.setHighlight(s.book, s.chapter, s.verses, null); state.selection = null },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = c.dim, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                state.overlay = Overlay.Editor(
                    title = state.label(s.book, s.chapter, s.verses),
                    initial = "", verse = null, maxVerse = 0,
                    onSave = { body, _ -> if (body.isNotBlank()) a.addNote(s.book, s.chapter, s.verses, body) },
                    onDelete = null,
                )
                state.selection = null
            }) { Text("Note", color = c.accent, fontSize = 16.sp) }
            TextButton(onClick = {
                val kind = if (s.verses.size == 1) Annotations.Kind.VERSE else Annotations.Kind.RANGE
                a.save(kind, s.book, s.chapter, s.verses, state.translationId)
                state.selection = null
            }) { Text("Save", color = c.accent, fontSize = 16.sp) }
            TextButton(onClick = {
                state.overlay = Overlay.Words(s.book, s.chapter, s.verses.sorted())
                state.selection = null
            }) { Text(state.meta.ui("orig"), color = c.accent, fontSize = 16.sp) }
            TextButton(onClick = { state.selection = null }) { Text("Done", color = c.dim, fontSize = 16.sp) }
        }
    }
}

/** End of a chapter: its footnotes, add one, save the chapter. */
@Composable
fun ChapterFooter(state: ReaderState, c: ReaderColors, book: String, chapter: Int, family: FontFamily) {
    val a = state.annotations
    val version = a.version
    val list = remember(version, book, chapter) { a.footnotesIn(book, chapter) }
    val isSaved = remember(version, book, chapter) { a.saved.any { it.kind == Annotations.Kind.CHAPTER && it.book == book && it.chapter == chapter } }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        HorizontalDivider(color = c.dim.copy(alpha = 0.25f))
        if (list.isNotEmpty()) {
            Text("NOTES ON THIS ${state.meta.ui("chapter").uppercase()}", color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            for (f in list) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editChapterNote(state, book, chapter, f.id) }
                        .padding(vertical = 8.dp),
                ) {
                    if (f.verse > 0) Text("v. ${f.verse}", color = c.accent, fontSize = 13.sp)
                    Text(f.body, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = family)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { editChapterNote(state, book, chapter, null) }) { Text("+ Note on this ${state.meta.ui("chapter").lowercase()}", color = c.accent) }
            TextButton(onClick = {
                val s = a.saved.firstOrNull { it.kind == Annotations.Kind.CHAPTER && it.book == book && it.chapter == chapter }
                if (s != null) a.deleteSaved(s.id) else a.save(Annotations.Kind.CHAPTER, book, chapter, emptyList(), state.translationId)
            }) { Text(if (isSaved) "Saved ✓" else "Save", color = c.accent) }
        }
    }
}

fun editChapterNote(state: ReaderState, book: String, chapter: Int, id: String?, back: Overlay? = null) {
    val a = state.annotations
    val f = id?.let { a.footnote(it) }
    state.overlay = Overlay.Editor(
        title = "${state.label(book, chapter)} · ${state.meta.ui("chapter").lowercase()} note",
        initial = f?.body ?: "",
        verse = f?.verse ?: 0,
        maxVerse = state.verseCount(book, chapter),
        onSave = { body, verse ->
            when {
                f == null -> if (body.isNotBlank()) a.addFootnote(book, chapter, verse, body)
                body.isBlank() -> a.deleteFootnote(f.id)
                else -> a.updateFootnote(f.id, verse, body)
            }
        },
        onDelete = f?.let { { a.deleteFootnote(it.id) } },
        back = back,
    )
}

/** Full-screen note editor. Saves on Done or Back; Delete removes the note. */
@Composable
fun EditorOverlay(state: ReaderState, e: Overlay.Editor, c: ReaderColors, touch: () -> Unit) {
    var body by remember(e) { mutableStateOf(e.initial) }
    var verse by remember(e) { mutableIntStateOf(e.verse ?: 0) }
    val close = { state.overlay = e.back }
    val done = { e.onSave(body, verse); close() }
    BackHandler(onBack = done)
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Cancel", color = c.dim) }
            Text(e.title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TextButton(onClick = done) { Text("Done", color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        if (e.verse != null) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Verse tag", color = c.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { verse = (verse - 1).coerceAtLeast(0) }) { Text("−", color = c.accent, fontSize = 20.sp) }
                Text(if (verse == 0) "none" else "v. $verse", color = c.text, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
                TextButton(onClick = { verse = (verse + 1).coerceAtMost(e.maxVerse) }) { Text("+", color = c.accent, fontSize = 20.sp) }
            }
        }
        PrivateField(
            hint = "Write a note",
            c = c,
            onChange = { body = it; touch() },
            initial = e.initial,
            multiline = true,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (e.onDelete != null) {
            TextButton(onClick = { e.onDelete.invoke(); close() }, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                Text("Delete note", color = Color(0xFFC0392B))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
