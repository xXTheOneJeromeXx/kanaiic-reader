package com.tbce.calc.ui

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tbce.calc.AppController
import com.tbce.calc.Config
import com.tbce.calc.dictionary.Dictionary
import com.tbce.calc.dictionary.DictionaryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class DictColors(val bg: Color, val field: Color, val text: Color, val dim: Color, val accent: Color, val line: Color, val onAccent: Color = Color.White)

private fun dictColors(dark: Boolean) = if (dark)
    DictColors(Color(0xFF111316), Color(0xFF1D2025), Color(0xFFE8EAED), Color(0xFF8F949B), Color(0xFF7FA6E8), Color(0xFF2A2E34), Color(0xFF0E1A2E))
else
    DictColors(Color(0xFFFCFCFD), Color(0xFFF0F1F4), Color(0xFF1B1D21), Color(0xFF6B7078), Color(0xFF2F5FB3), Color(0xFFE3E5E9))

private enum class RefTab(val title: String) { DICTIONARY("Dictionary"), THESAURUS("Thesaurus"), NOTES("Notes"), PDF("PDF") }

/**
 * The reference disguise: a working offline dictionary and thesaurus (WordNet 3.0), a notepad and
 * a PDF viewer.
 * Behind it is the same reader and code. To open the reader, hold Search for three seconds; a
 * keypad appears for the code. A normal tap of Search just looks the word up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionaryScreen(app: AppController) {
    val context = LocalContext.current
    val dict = remember { Dictionary { name -> try { context.assets.open("w/$name").use { String(it.readBytes()) } } catch (_: Exception) { null } } }
    val store = remember { DictionaryStore(context) }
    val c = dictColors(isSystemInDarkTheme())
    var tab by remember { mutableStateOf(RefTab.DICTIONARY) }
    var field by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf<Dictionary.Entry?>(null) }
    var notFound by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var recent by remember { mutableStateOf(store.recent()) }
    var daily by remember { mutableStateOf<Dictionary.Entry?>(null) }
    var about by remember { mutableStateOf(false) }
    val editor = remember { arrayOfNulls<EditText>(1) }
    val pdf = remember { PdfHolder() }
    // Until a code exists, each launch explains how to set one up and how to get in later.
    var welcome by remember { mutableStateOf(!app.hasVault()) }
    DisposableEffect(pdf) { onDispose { pdf.close() } }

    fun setField(text: String) {
        field = text
        editor[0]?.let { it.setText(text); it.setSelection(text.length) }
    }

    fun hideKeyboard() {
        editor[0]?.let { e ->
            e.clearFocus()
            context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(e.windowToken, 0)
        }
    }

    fun show(e: Dictionary.Entry) {
        entry = e
        notFound = null
        store.add(e.display)
        recent = store.recent()
        hideKeyboard()
    }

    fun open(word: String) {
        dict.lookup(word)?.let { setField(""); show(it) }
    }

    fun search() {
        val q = field.trim()
        if (q.isEmpty()) return
        val e = dict.lookup(q)
        if (e != null) show(e) else { entry = null; notFound = q }
    }

    // The keypad covers the screen; the keyboard goes away so it doesn't sit on top.
    LaunchedEffect(app.keypadOpen) { if (app.keypadOpen) hideKeyboard() }

    LaunchedEffect(field) {
        val q = field
        suggestions = if (q.isBlank()) emptyList() else withContext(Dispatchers.Default) { dict.suggest(q) }
    }
    LaunchedEffect(Unit) {
        daily = withContext(Dispatchers.Default) { wordOfTheDay(dict) }
    }

    val searching = tab != RefTab.NOTES && tab != RefTab.PDF
    BackHandler(enabled = searching && (entry != null || notFound != null || field.isNotEmpty())) {
        when {
            entry != null -> entry = null
            else -> { notFound = null; setField("") }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding()) {
        if (searching) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (entry != null) {
                    Text("‹", color = c.accent, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { entry = null }.padding(horizontal = 14.dp, vertical = 2.dp))
                } else {
                    Text(tab.title, color = c.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("About", color = c.dim, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { about = true }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(c.field).padding(horizontal = 18.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        factory = { ctx ->
                            EditText(ctx).apply {
                                // No keyboard learning, so a code typed here can't land in the keyboard's dictionary.
                                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                                isSaveEnabled = false
                                isSingleLine = true
                                background = null
                                setPadding(0, 0, 0, 0)
                                setTextColor(c.text.toArgb())
                                setHintTextColor(c.dim.toArgb())
                                hint = "Search for a word"
                                textSize = 17f
                                setText(field)
                                setOnEditorActionListener { _, action, _ ->
                                    if (action == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
                                }
                                addTextChangedListener(object : TextWatcher {
                                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                                    override fun afterTextChanged(s: Editable?) {
                                        val t = s?.toString() ?: ""
                                        if (t == field) return
                                        field = t
                                        notFound = null
                                        if (t.isNotEmpty()) entry = null
                                    }
                                })
                                editor[0] = this
                            }
                        },
                        onRelease = { editor[0] = null },
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Tap looks the word up; a long hold is the hidden gesture (opens the keypad).
                // A filled button, so it reads as something to press and hold, not a label.
                HoldFor(Config.HOLD_TO_UNLOCK_MS) {
                    Box(
                        Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.accent)
                            .faceHoldable(onTap = { search() }, onHold = { app.faceHold() })
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Search", color = c.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            val shown = entry
            when {
                tab == RefTab.NOTES -> NotesScreen()
                tab == RefTab.PDF -> PdfScreen(pdf, PdfColors(c.bg, c.text, c.dim, c.accent, c.line))
                shown != null && tab == RefTab.THESAURUS -> ThesaurusView(shown, c, onWord = ::open)
                shown != null -> EntryView(shown, c, onWord = ::open)
                notFound != null -> NotFound(notFound!!, suggestions, c, onWord = ::open)
                field.isNotBlank() -> WordList(suggestions, c, onWord = ::open)
                else -> Home(daily, recent, c, onWord = { w -> dict.lookup(w)?.let { show(it) } }, onClear = { store.clear(); recent = emptyList() })
            }
        }

        // The tab bar steps aside while typing, as in most apps.
        if (!WindowInsets.isImeVisible) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for (t in RefTab.entries) {
                    val on = t == tab
                    Text(
                        t.title,
                        color = if (on) c.accent else c.dim,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { tab = t }.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (app.keypadOpen) Keypad(c, onCode = { app.keypadSubmit(it) }, onClose = { app.closeKeypad() })

    if (welcome && !app.hasVault()) {
        AlertDialog(
            onDismissRequest = { welcome = false },
            title = { Text(app.ui["welcome_title"] ?: "Welcome") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(app.ui["welcome_text"] ?: "") } },
            confirmButton = { TextButton(onClick = { welcome = false; app.startSetup() }) { Text(app.ui["welcome_setup"] ?: "Set up now") } },
            dismissButton = { TextButton(onClick = { welcome = false }) { Text(app.ui["welcome_later"] ?: "Later") } },
        )
    }

    if (about) {
        val notice = remember { try { context.assets.open("w/2").use { String(it.readBytes()) } } catch (_: Exception) { "" } }
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("About") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("An offline English dictionary and thesaurus. Definitions and synonyms come from WordNet, a lexical database of English from Princeton University.\n")
                    Text(notice.trim(), fontSize = 12.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
        )
    }
}

/**
 * The hidden keypad: dots for the digits typed, a number pad, backspace and OK. Back or a tap
 * outside closes it. It closes on OK too, whatever the code, so a wrong code shows nothing.
 */
@Composable
private fun Keypad(c: DictColors, onCode: (ByteArray) -> Unit, onClose: () -> Unit) {
    val code = remember { CodeBuffer() }
    var count by remember { mutableIntStateOf(0) }
    // True while the code is checked (about a second); the keys do nothing meanwhile.
    var working by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { code.wipe() } }
    BackHandler { onClose() }

    fun add(d: Int) { if (!working && code.length < Config.MAX_CODE_DIGITS) { code.add(d); count++ } }
    fun submit() {
        if (working) return
        if (code.length == 0) { onClose(); return }
        working = true
        val attempt = code.copy()
        code.wipe(); count++
        try { onCode(attempt) } finally { attempt.fill(0) }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).width(300.dp).clip(RoundedCornerShape(24.dp)).background(c.bg)
                // Taps between the keys stay on the keypad.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shown = count.let { code.length }
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                if (working) CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp)
                else Text(if (shown == 0) " " else "●".repeat(shown), color = c.text, fontSize = 18.sp, letterSpacing = 2.sp, maxLines = 1)
            }
            for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (k in row) {
                        val action = k == "OK"
                        Box(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp))
                                .background(if (action) c.accent else c.field)
                                .clickable {
                                    when (k) {
                                        "⌫" -> if (!working) { code.back(); count++ }
                                        "OK" -> submit()
                                        else -> add(k.toInt())
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(k, color = if (action) c.onAccent else c.text, fontSize = if (action) 18.sp else 24.sp, fontWeight = if (action) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

/** A headword that changes once a day, picked from ordinary single words. */
private fun wordOfTheDay(dict: Dictionary): Dictionary.Entry? {
    val day = (System.currentTimeMillis() / 86_400_000L).toInt()
    val letters = "abcdefghilmnoprstw"
    val letter = letters[Math.floorMod(day, letters.length)]
    val words = dict.suggest("$letter", limit = 4000).filter { w -> w.length in 5..10 && w.all { it in 'a'..'z' } }
    if (words.isEmpty()) return null
    return dict.entry(words[Math.floorMod(day * 7919, words.size)])
}

@Composable
private fun Home(daily: Dictionary.Entry?, recent: List<String>, c: DictColors, onWord: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp)) {
        if (daily != null) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.field)
                        .clickable { onWord(daily.key) }.padding(18.dp),
                ) {
                    Text("Word of the day", color = c.dim, fontSize = 13.sp)
                    Text(daily.display, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    daily.senses.firstOrNull()?.let { s ->
                        Text(Dictionary.posName(s.pos), color = c.accent, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                        Text(s.definition, color = c.text, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", color = c.dim, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("Clear", color = c.dim, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClear() }.padding(8.dp))
                }
            }
            items(recent) { w -> WordRow(w, c) { onWord(w) } }
        }
    }
}

@Composable
private fun WordList(words: List<String>, c: DictColors, onWord: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp)) {
        items(words) { w -> WordRow(w, c) { onWord(w) } }
    }
}

@Composable
private fun WordRow(word: String, c: DictColors, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(word, color = c.text, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 13.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun NotFound(query: String, near: List<String>, c: DictColors, onWord: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("No entry for “$query”.", color = c.text, fontSize = 17.sp)
        Text("Check the spelling, or try the base form of the word.", color = c.dim, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        if (near.isNotEmpty()) WordList(near, c, onWord)
    }
}

@Composable
private fun Headword(e: Dictionary.Entry, c: DictColors) {
    Text(e.display, color = c.text, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
    e.from?.let { Text("“$it” is a form of ${e.display}", color = c.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
}

/** Words to tap, each opening its own entry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(words: List<String>, c: DictColors, onWord: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (w in words) {
            Text(
                w, color = c.accent, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.field).clickable { onWord(w) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EntryView(e: Dictionary.Entry, c: DictColors, onWord: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Headword(e, c)
        for ((pos, senses) in e.senses.groupBy { it.pos }) {
            Text(Dictionary.posName(pos), color = c.accent, fontSize = 16.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
            senses.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("${i + 1}.", color = c.dim, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.definition, color = c.text, fontSize = 16.sp, lineHeight = 23.sp)
                        for (ex in s.examples.take(2)) {
                            Text("“$ex”", color = c.dim, fontSize = 15.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 3.dp))
                        }
                        if (s.synonyms.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            WordChips(s.synonyms, c, onWord)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ThesaurusView(e: Dictionary.Entry, c: DictColors, onWord: (String) -> Unit) {
    val senses = e.senses.filter { it.synonyms.isNotEmpty() || it.antonyms.isNotEmpty() || it.related.isNotEmpty() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Headword(e, c)
        if (senses.isEmpty()) {
            Text("No synonyms listed for ${e.display}.", color = c.dim, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
        }
        for (s in senses) {
            Column(Modifier.padding(top = 20.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.accent, fontStyle = FontStyle.Italic)) { append(Dictionary.posName(s.pos)) }
                        append("  ·  " + s.definition)
                    },
                    color = c.dim, fontSize = 15.sp, lineHeight = 21.sp,
                )
                if (s.synonyms.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    WordChips(s.synonyms, c, onWord)
                }
                if (s.related.isNotEmpty()) {
                    Text("Related", color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    WordChips(s.related, c, onWord)
                }
                if (s.antonyms.isNotEmpty()) {
                    Text("Opposite", color = c.dim, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    WordChips(s.antonyms, c, onWord)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
