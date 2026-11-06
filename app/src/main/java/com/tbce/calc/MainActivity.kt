package com.tbce.calc

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tbce.calc.ui.AppTheme
import com.tbce.calc.ui.DictionaryScreen
import com.tbce.calc.ui.ReaderLockScreen
import com.tbce.calc.ui.SetupScreen
import com.tbce.calc.ui.ReaderScreen
import com.tbce.calc.reader.Packs
import com.tbce.calc.vault.KeystoreDeviceKey
import com.tbce.calc.vault.KeyVault

class MainActivity : ComponentActivity() {
    private lateinit var app: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Never restore anything: a cold start always begins at the front screen.
        super.onCreate(null)
        // Release builds die quietly on a crash, so no stack trace (which can carry text) reaches the log.
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Thread.setDefaultUncaughtExceptionHandler { _, _ ->
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
        enableEdgeToEdge()
        // Left behind if the process died while open; it is only ever written after unlock.
        java.io.File(cacheDir, "f0").delete()
        val ui = try { Packs { name -> assets.open("r/$name").use { it.readBytes() } }.meta().ui } catch (e: Exception) { emptyMap() }
        app = AppController(KeyVault(filesDir, KeystoreDeviceKey(this)), lifecycleScope, ui)
        app.initDisguise(Disguises.current(this))
        setContent {
            AppTheme {
                LaunchedEffect(app.screen) { setSecure(app.screen != Screen.FRONT) }
                when (app.screen) {
                    Screen.FRONT -> when (app.disguise) {
                        Disguise.READER -> ReaderLockScreen(app)
                        Disguise.DICTIONARY -> DictionaryScreen(app)
                    }
                    Screen.SETUP -> SetupScreen(app)
                    Screen.INSIDE -> ReaderScreen(app)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Deliberately save nothing.
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val graceLock = Runnable { app.onBackground() }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(graceLock)
        // Without a code, the app's own face opens straight away.
        app.onForeground()
    }

    /**
     * Leaving the foreground for any reason (home, recents, screen off) locks. The one exception
     * is the system file picker for export/import, which gets [Config.PICKER_GRACE_MS] first.
     */
    override fun onStop() {
        super.onStop()
        if (app.pickerOpen) handler.postDelayed(graceLock, Config.PICKER_GRACE_MS) else app.onBackground()
    }

    /** Blocks screenshots, screen recording and the recents thumbnail outside the front screen. */
    private fun setSecure(on: Boolean) {
        // Debug builds skip the flag so the screens can be checked with screenshots; release never does.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (on && !debuggable) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!on)
        if (!on) {
            currentFocus?.clearFocus()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.ime())
                val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                isAppearanceLightStatusBars = !night
                isAppearanceLightNavigationBars = !night
            }
        }
    }
}
