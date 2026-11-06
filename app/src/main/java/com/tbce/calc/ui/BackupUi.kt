package com.tbce.calc.ui

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tbce.calc.AppController
import com.tbce.calc.Config
import com.tbce.calc.reader.Annotations
import com.tbce.calc.reader.Overlay
import com.tbce.calc.reader.ReaderState
import com.tbce.calc.vault.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backup and security rows in Settings. */
@Composable
fun BackupAndSecurity(app: AppController, state: ReaderState, c: ReaderColors, onImport: () -> Unit) {
    var wipe by remember { mutableStateOf(app.wipeEnabled()) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmNoCode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("BACKUP", color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
    Row_(text = "Export notes, highlights and saved", c = c) { state.overlay = Overlay.Passphrase(export = true) }
    Row_(text = "Import from a backup file", c = c, onClick = onImport)
    Text(
        "The file is locked with a passphrase you choose and looks like random data. Keep it somewhere safe; " +
            "it is the only way to move your notes to another phone.",
        color = c.dim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 8.dp),
    )
    Text("SECURITY", color = c.dim, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ask for a code", color = c.text, fontSize = 16.sp)
            Text(
                if (app.hasCode) "Your code is needed to open it." else "Off: it opens without a code.",
                color = c.dim, fontSize = 13.sp,
            )
        }
        Switch(
            checked = app.hasCode,
            onCheckedChange = { on -> if (on) state.overlay = Overlay.ChangeCode else confirmNoCode = true },
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.bg),
        )
    }
    if (!app.hasCode) return
    Row_(text = "Change code", c = c) { state.overlay = Overlay.ChangeCode }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Erase after ${Config.WIPE_AFTER} wrong codes", color = c.text, fontSize = 16.sp)
            Text("Off unless you turn it on. Cannot be undone.", color = c.dim, fontSize = 13.sp)
        }
        Switch(
            checked = wipe,
            onCheckedChange = { on -> if (on) confirmWipe = true else { app.setWipeEnabled(false); wipe = false } },
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.bg),
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Erase after ${Config.WIPE_AFTER} wrong codes?") },
            text = {
                Text(
                    "If ${Config.WIPE_AFTER} wrong codes are entered in a row, everything here is " +
                        "erased for good, including your notes. A right code resets the count. There is no undo, so keep a backup.",
                )
            },
            confirmButton = { TextButton(onClick = { app.setWipeEnabled(true); wipe = true; confirmWipe = false }) { Text("Turn on") } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
    if (confirmNoCode) {
        AlertDialog(
            onDismissRequest = { confirmNoCode = false },
            title = { Text("Stop asking for a code?") },
            text = { Text(NO_CODE_WARNING) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNoCode = false
                    scope.launch {
                        app.changeCode(ByteArray(0))
                        state.notice = "It now opens without a code."
                    }
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmNoCode = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Row_(text: String, c: ReaderColors, onClick: () -> Unit) {
    Text(text, color = c.text, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

/** Passphrase screen for export (entered twice) or import (once). */
@Composable
fun PassphraseOverlay(state: ReaderState, o: Overlay.Passphrase, c: ReaderColors, touch: () -> Unit, onExportReady: (ByteArray) -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = { state.overlay = null }
    BackHandler { if (!busy) close() }

    fun go() {
        error = ""
        if (o.export) {
            if (first.length < Config.MIN_PASSPHRASE) { error = "Use at least ${Config.MIN_PASSPHRASE} characters."; return }
            if (first != second) { error = "The two passphrases don't match."; return }
        }
        val pass = first.toCharArray()
        busy = true
        scope.launch {
            try {
                if (o.export) {
                    val data = state.annotations.toBytes()
                    val blob = withContext(Dispatchers.Default) { try { Backup.seal(pass, data) } finally { data.fill(0) } }
                    close()
                    onExportReady(blob)
                } else {
                    val plain = withContext(Dispatchers.Default) { Backup.open(pass, o.file ?: ByteArray(0)) }
                    if (plain == null) {
                        error = "Wrong passphrase, or this isn't a backup file."
                    } else {
                        val added = try {
                            state.annotations.merge(Annotations().apply { load(plain) })
                        } catch (e: Exception) {
                            -1
                        } finally {
                            plain.fill(0)
                        }
                        close()
                        state.notice = if (added < 0) "That file couldn't be read." else if (added == 0) "Nothing new: everything in that backup is already here." else "Added $added items from the backup."
                    }
                }
            } finally {
                pass.fill(' ')
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (!busy) close() }) { Text("Cancel", color = c.dim) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (!busy) go() }) { Text(if (o.export) "Export" else "Import", color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(if (o.export) "Choose a passphrase" else "Passphrase for this backup", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(
                if (o.export) "You'll need it to import the file. It can't be recovered. Use at least ${Config.MIN_PASSPHRASE} characters; a few unrelated words work well."
                else "Notes, highlights and saved items in the backup are added to what's here. Nothing here is replaced.",
                color = c.dim, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 10.dp),
            )
            SecretField("Passphrase", c, autoFocus = true) { first = it; touch() }
            if (o.export) {
                Spacer(Modifier.height(10.dp))
                SecretField("Same passphrase again", c, autoFocus = false) { second = it; touch() }
            }
            if (error.isNotEmpty()) Text(error, color = Color(0xFFC0392B), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            if (busy) CircularProgressIndicator(Modifier.padding(top = 20.dp).align(Alignment.CenterHorizontally), color = c.accent)
        }
    }
}

/** Password-style field: hidden text, no suggestions, no learning, no autofill. */
@Composable
private fun SecretField(hint: String, c: ReaderColors, autoFocus: Boolean, onChange: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface),
        factory = { ctx ->
            EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                isSaveEnabled = false
                isSingleLine = true
                background = null
                setPadding(36, 28, 36, 28)
                this.hint = hint
                setHintTextColor(c.dim.toArgb())
                setTextColor(c.text.toArgb())
                textSize = 17f
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString() ?: "") }
                })
                if (autoFocus) post {
                    requestFocus()
                    (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(this, 0)
                }
            }
        },
        onRelease = { it.text.clear() },
    )
}
