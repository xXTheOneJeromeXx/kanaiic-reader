package com.tbce.calc.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbce.calc.AppController
import com.tbce.calc.Config
import com.tbce.calc.Disguise
import com.tbce.calc.Disguises

/**
 * The app's own (undisguised) front screen: the app's own name, and a plain code pad to open
 * it. No hidden gesture. On a fresh install it offers to choose a code first. Without a code
 * it shows an Open button instead of the pad (and opens by itself when the app comes back).
 */
@Composable
fun ReaderLockScreen(app: AppController) {
    val context = LocalContext.current
    val colors = LocalCalcColors.current
    val name = remember { Disguises.label(context, Disguise.READER) }
    val code = remember { CodeBuffer() }
    var count by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { code.wipe() } }

    fun open() {
        if (code.length == 0 || working) return
        working = true
        wrong = false
        val attempt = code.copy()
        code.wipe(); count++
        try {
            app.tryCode(attempt) { working = false; wrong = true }
        } finally {
            attempt.fill(0)
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(name, color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(24.dp))
            if (!app.hasVault()) {
                Text(app.ui["reader_welcome"] ?: "", color = colors.dim, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { app.startSetup() }) { Text(app.ui["reader_start"] ?: "Start", fontSize = 18.sp) }
                return@Column
            }
            if (!app.hasCode) {
                if (app.opening) CircularProgressIndicator()
                else TextButton(onClick = { app.openWithoutCode() }) { Text(app.ui["reader_open"] ?: "Open", fontSize = 18.sp) }
                return@Column
            }
            Text(if (wrong) app.ui["reader_wrong"] ?: "" else app.ui["reader_enter"] ?: "", color = colors.dim, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            val shown = count.let { code.length }
            Text(if (shown == 0) " " else "●".repeat(shown), color = colors.text, fontSize = 28.sp, letterSpacing = 4.sp)
            if (working) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
        if (app.hasVault() && app.hasCode) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                for (r in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                        for (d in r) Key("$d", KeyStyle.NUMBER, 26.sp, Modifier.weight(1f).fillMaxSize()) {
                            if (code.length < Config.MAX_CODE_DIGITS) { code.add(d); wrong = false; count++ }
                        }
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    Key("⌫", KeyStyle.FUNCTION, 24.sp, Modifier.weight(1f).fillMaxSize(), description = "backspace") { code.back(); count++ }
                    Key("0", KeyStyle.NUMBER, 26.sp, Modifier.weight(1f).fillMaxSize()) {
                        if (code.length < Config.MAX_CODE_DIGITS) { code.add(0); wrong = false; count++ }
                    }
                    Key(app.ui["reader_open"] ?: "Open", KeyStyle.EQUALS, 20.sp, Modifier.weight(1f).fillMaxSize()) { open() }
                }
            }
        }
    }
}
