package com.tbce.calc.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * A control that acts normally on a tap and triggers the hidden gesture on a long press.
 * Used by the Dictionary's Search button, whose hold opens the keypad.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.faceHoldable(onTap: () -> Unit, onHold: () -> Unit): Modifier =
    this.combinedClickable(onClick = onTap, onLongClick = onHold)

/** Makes a long press inside [content] take [millis] instead of the system's usual half second. */
@Composable
fun HoldFor(millis: Long, content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val config = remember(base, millis) { HoldConfig(base, millis) }
    CompositionLocalProvider(LocalViewConfiguration provides config) { content() }
}

private class HoldConfig(base: ViewConfiguration, override val longPressTimeoutMillis: Long) : ViewConfiguration by base
