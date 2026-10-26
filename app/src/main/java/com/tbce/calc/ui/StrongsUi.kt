package com.tbce.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbce.calc.reader.Overlay
import com.tbce.calc.reader.ReaderState
import com.tbce.calc.reader.Strongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The original-language words of the selected verses, each opening its Strong's entry. */
@Composable
fun WordsOverlay(state: ReaderState, o: Overlay.Words, c: ReaderColors) {
    val close = { state.overlay = null }
    BackHandler(onBack = close)
    // Per verse: the KJV text, and its tagged words with their entries. Loaded off the main thread.
    var rows by remember(o) { mutableStateOf<List<Triple<Int, String, List<WordRow>>>?>(null) }
    LaunchedEffect(o) {
        rows = withContext(Dispatchers.Default) {
            val s = state.strongs()
            o.verses.map { v ->
                Triple(v, state.verseText("kjv", o.book, o.chapter, listOf(v)), s.words(o.book, o.chapter, v).map { WordRow(it, s.entry(it.number), s.renderings(it.number)) })
            }
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        TopRow(state.label(o.book, o.chapter, o.verses), c, close)
        val list = rows
        if (list == null) {
            Text(state.meta.ui("loading"), color = c.dim, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(state.meta.ui("orig_note"), color = c.dim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            for ((verse, kjv, words) in list) {
                item(key = "v$verse") {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 6.dp)) {
                        if (list.size > 1) Text("${o.chapter}:$verse", color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(kjv, color = c.dim, fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic,
                            fontFamily = state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif)
                        if (words.isEmpty()) Text(state.meta.ui("orig_none"), color = c.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                items(words.size, key = { "v$verse.$it" }) { i ->
                    val (w, e, renders) = words[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { state.overlay = Overlay.Lexicon(w.number, back = o) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(w.text, color = c.text, fontSize = 16.sp, modifier = Modifier.width(120.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e?.lemma ?: "", color = c.text, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(listOfNotNull(e?.transliteration?.ifEmpty { null }, w.number).joinToString(" · "), color = c.dim, fontSize = 13.sp)
                            }
                            if (renders.isNotEmpty()) {
                                Text(renders.joinToString(", "), color = c.dim, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        Text("›", color = c.dim, fontSize = 20.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/** A tagged word, its entry, and the KJV's usual renderings of it (the one-line summary). */
private data class WordRow(val word: Strongs.Word, val entry: Strongs.Entry?, val renderings: List<String>)

/** One Strong's entry: the word, its meaning, origin and KJV renderings, then every verse using it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LexiconOverlay(state: ReaderState, o: Overlay.Lexicon, c: ReaderColors) {
    val close = { state.overlay = o.back }
    BackHandler(onBack = close)
    var data by remember(o.number) { mutableStateOf<Pair<Strongs.Entry?, Strongs.Usage>?>(null) }
    LaunchedEffect(o.number) {
        data = withContext(Dispatchers.Default) { state.strongs().let { it.entry(o.number) to it.usage(o.number) } }
    }
    val tid = state.translationId
    val family = state.typeface?.let { FontFamily(it) } ?: FontFamily.Serif
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        TopRow("${state.meta.ui("lex_strongs")} ${o.number}", c, close)
        val d = data
        if (d == null) {
            Text(state.meta.ui("loading"), color = c.dim, modifier = Modifier.padding(20.dp))
            return@Column
        }
        val (e, usage) = d
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (e == null) {
                        Text(state.meta.ui("orig_none"), color = c.dim, modifier = Modifier.padding(vertical = 12.dp))
                        return@Column
                    }
                    Text(e.lemma, color = c.text, fontSize = 36.sp)
                    Text(
                        listOf(e.transliteration, e.pronunciation).filter { it.isNotEmpty() }.joinToString("  ·  "),
                        color = c.text, fontSize = 17.sp, fontStyle = FontStyle.Italic,
                    )
                    Text(state.meta.ui(if (e.hebrew) "lex_hebrew" else "lex_greek") + " · " + e.number, color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Section(state.meta.ui("lex_meaning"), e.meaning, state, o, c)
                    Section(state.meta.ui("lex_kjv"), e.usage, state, o, c)
                    if (usage.renderings.isNotEmpty()) {
                        Label(state.meta.ui("lex_renders"), c)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((word, n) in usage.renderings.take(12)) {
                                Text(
                                    "$word  $n", color = c.text, fontSize = 14.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    Label(state.meta.ui("lex_verses").replace("{n}", "${usage.verses.size}"), c)
                }
            }
            items(usage.verses, key = { "${it.book}.${it.chapter}.${it.verse}" }) { r ->
                // Verse text in the reader's current translation; loads book by book as you scroll.
                val text = remember(r, tid) { state.verseText(tid, r.book, r.chapter, listOf(r.verse)) }
                Column(Modifier.fillMaxWidth().clickable { state.goTo(r, "") }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(state.label(r.book, r.chapter, listOf(r.verse)), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(text, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = family)
                }
                HorizontalDivider(color = c.dim.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 20.dp))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun TopRow(title: String, c: ReaderColors, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹", color = c.accent, fontSize = 24.sp) }
        Text(title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun Label(text: String, c: ReaderColors) {
    Text(text.uppercase(), color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

/** A titled paragraph of entry text, with any Strong's numbers in it linked to their entries. */
@Composable
private fun Section(title: String, body: String, state: ReaderState, from: Overlay.Lexicon, c: ReaderColors) {
    if (body.isBlank()) return
    Label(title, c)
    Text(linked(body, c) { n -> state.overlay = Overlay.Lexicon(n, back = from) }, color = c.text, fontSize = 16.sp, lineHeight = 23.sp)
}

private fun linked(text: String, c: ReaderColors, open: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var last = 0
    val style = TextLinkStyles(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium))
    for (m in Strongs.MENTION.findAll(text)) {
        append(text, last, m.range.first)
        withLink(LinkAnnotation.Clickable(m.value, style) { open(m.value) }) { append(m.value) }
        last = m.range.last + 1
    }
    append(text, last, text.length)
}

/** A Strong's entry in the Search results; tapping it opens the entry. */
@Composable
fun LexiconMatchRow(state: ReaderState, m: Strongs.Match, c: ReaderColors) {
    Column(Modifier.fillMaxWidth().clickable { state.overlay = Overlay.Lexicon(m.number) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.lemma, color = c.text, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(listOf(m.transliteration, m.number).filter { it.isNotEmpty() }.joinToString(" · "), color = c.dim, fontSize = 13.sp)
        }
        if (m.renderings.isNotEmpty()) {
            Text(m.renderings.joinToString(", "), color = c.dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = c.dim.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 20.dp))
}
