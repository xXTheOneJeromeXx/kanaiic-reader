package com.tbce.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbce.calc.AppController
import com.tbce.calc.Config
import com.tbce.calc.Disguise
import kotlinx.coroutines.launch

/** Holds a code being typed, in a byte array that is zeroed when done. */
internal class CodeBuffer {
    val bytes = ByteArray(Config.MAX_CODE_DIGITS)
    var length = 0
    fun add(d: Int) { if (length < bytes.size) bytes[length++] = ('0'.code + d).toByte() }
    fun back() { if (length > 0) bytes[--length] = 0 }
    fun wipe() { bytes.fill(0); length = 0 }
    fun sameAs(o: CodeBuffer) = length == o.length && (0 until length).all { bytes[it] == o.bytes[it] }
    fun copy() = bytes.copyOf(length)

    /** True for codes like 111111 or 123456 that are easy to guess. */
    fun isWeak(): Boolean {
        if (length == 0) return false
        val same = (1 until length).all { bytes[it] == bytes[0] }
        val up = (1 until length).all { bytes[it] - bytes[it - 1] == 1 }
        val down = (1 until length).all { bytes[it - 1] - bytes[it] == 1 }
        return same || up || down
    }
}

private enum class Stage { ENTER, CONFIRM, WORKING }

/** First-run setup, reached by the active face's way in on a fresh install. */
@Composable
fun SetupScreen(app: AppController) {
    CodeEntry(
        ui = app.ui,
        colors = LocalCalcColors.current.let { CodeColors(it.background, it.text, it.dim) },
        changing = false,
        face = app.disguise,
        onDone = { code -> app.finishSetup(code) },
        onCancel = { app.cancelSetup() },
        onNoCode = { app.finishSetup(ByteArray(0)) },
    )
}

class CodeColors(val bg: androidx.compose.ui.graphics.Color, val text: androidx.compose.ui.graphics.Color, val dim: androidx.compose.ui.graphics.Color)

/**
 * Enter a new code twice on a number pad. Used for first-run setup and for changing the code.
 * Wording comes from the encrypted pack ([ui]); the "how to open" hint is the one for [face].
 * [onDone] gets the code and must not keep it. With [onNoCode], the first step also offers to
 * go on without a code.
 */
@Composable
fun CodeEntry(
    ui: Map<String, String>,
    colors: CodeColors,
    changing: Boolean,
    face: Disguise,
    onDone: suspend (ByteArray) -> Unit,
    onCancel: () -> Unit,
    onNoCode: (suspend () -> Unit)? = null,
) {
    fun t(k: String) = (ui[k] ?: "").replace("{min}", "${Config.MIN_CODE_DIGITS}").replace("{good}", "${Config.GOOD_CODE_DIGITS}")
    val scope = rememberCoroutineScope()
    val first = remember { CodeBuffer() }
    val second = remember { CodeBuffer() }
    var stage by remember { mutableStateOf(Stage.ENTER) }
    var count by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var askNoCode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { first.wipe(); second.wipe() } }
    BackHandler(onBack = onCancel)

    val current = if (stage == Stage.ENTER) first else second

    fun next() {
        when (stage) {
            Stage.ENTER -> {
                if (first.length < Config.MIN_CODE_DIGITS) return
                stage = Stage.CONFIRM; message = ""
            }
            Stage.CONFIRM -> {
                if (!first.sameAs(second)) {
                    first.wipe(); second.wipe()
                    stage = Stage.ENTER
                    message = t("code_mismatch")
                } else {
                    stage = Stage.WORKING
                    val code = first.copy()
                    first.wipe(); second.wipe()
                    scope.launch {
                        try { onDone(code) } finally { code.fill(0) }
                    }
                }
            }
            Stage.WORKING -> {}
        }
        count++
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                t(if (stage == Stage.CONFIRM) "code_again" else if (changing) "code_change" else "code_new"),
                color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            if (stage == Stage.ENTER) {
                Hint(t("code_min"), colors)
                Hint(t(howKey(face)), colors)
                Hint(t("code_lost"), colors)
                // The undisguised face shows dots only; every disguise shows the digits.
                if (face != Disguise.READER) Hint(t("code_watch"), colors)
            }
            if (message.isNotEmpty()) Hint(message, colors, strong = true)
            Spacer(Modifier.height(16.dp))
            // count is read so the dots redraw on every key.
            val shown = count.let { current.length }
            Text(
                if (shown == 0) " " else "●".repeat(shown),
                color = colors.text, fontSize = 28.sp, letterSpacing = 4.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (stage == Stage.ENTER && shown > 0) {
                val strength = when {
                    first.isWeak() -> t("code_weak")
                    shown < Config.MIN_CODE_DIGITS -> t("code_short")
                    shown < Config.GOOD_CODE_DIGITS -> t("code_ok")
                    else -> t("code_good")
                }
                Text(strength, color = colors.dim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (stage == Stage.WORKING) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }

        if (stage != Stage.WORKING) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
                for (r in rows) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                        for (d in r) Key("$d", KeyStyle.NUMBER, 26.sp, Modifier.weight(1f).fillMaxSize()) { current.add(d); count++ }
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    Key("⌫", KeyStyle.FUNCTION, 24.sp, Modifier.weight(1f).fillMaxSize(), description = "backspace") { current.back(); count++ }
                    Key("0", KeyStyle.NUMBER, 26.sp, Modifier.weight(1f).fillMaxSize()) { current.add(0); count++ }
                    Key("Next", KeyStyle.EQUALS, 20.sp, Modifier.weight(1f).fillMaxSize()) { next() }
                }
                Row(Modifier.align(Alignment.CenterHorizontally)) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = colors.dim) }
                    if (onNoCode != null && stage == Stage.ENTER) {
                        TextButton(onClick = { askNoCode = true }) { Text("Use without a code", color = colors.dim) }
                    }
                }
            }
        }
    }
    if (askNoCode && onNoCode != null) {
        AlertDialog(
            onDismissRequest = { askNoCode = false },
            title = { Text("Use without a code?") },
            text = { Text(NO_CODE_WARNING) },
            confirmButton = {
                TextButton(onClick = {
                    askNoCode = false
                    first.wipe(); second.wipe()
                    stage = Stage.WORKING
                    scope.launch { onNoCode() }
                }) { Text("No code") }
            },
            dismissButton = { TextButton(onClick = { askNoCode = false }) { Text("Choose a code") } },
        )
    }
}

/** What going without a code means, shown before it is chosen at setup or in Settings. */
const val NO_CODE_WARNING =
    "Anyone who can use your phone will be able to open it, with nothing to type. " +
        "Your notes stay encrypted on this phone, and you can set a code at any time in Settings > Security."


/** Pack key for how to open the reader from [face], e.g. "code_how_dictionary". */
fun howKey(face: Disguise) = "code_how_" + face.name.lowercase()

@Composable
private fun Hint(text: String, colors: CodeColors, strong: Boolean = false) {
    Text(
        text,
        color = if (strong) colors.text else colors.dim,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
