package com.tbce.calc

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tbce.calc.vault.KeyVault
import com.tbce.calc.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** FRONT is the visible, locked front screen of whichever face is active (the app's own, or the dictionary). */
enum class Screen { FRONT, SETUP, INSIDE }

/** [ui] is the wording from the encrypted pack, used by the setup and code screens. */
class AppController(private val vault: KeyVault, private val scope: CoroutineScope, val ui: Map<String, String>) {
    /** Bumped on every lock, so a slow unlock that finishes after a lock is thrown away. */
    private var generation = 0

    var screen by mutableStateOf(Screen.FRONT)
        private set
    var session: VaultSession? = null
        private set

    /** Bumped when the vault opens, so the vault screen reloads its contents. */
    var vaultOpens by mutableIntStateOf(0)
        private set

    /** Which disguise the launcher shows and which front screen appears when locked. */
    var disguise by mutableStateOf(Disguise.DICTIONARY)
        private set

    fun initDisguise(d: Disguise) { disguise = d }

    /** Switches the launcher icon and name, and the front screen, to [d]. */
    fun setDisguise(context: Context, d: Disguise) {
        Disguises.set(context, d)
        disguise = d
    }

    /**
     * The Dictionary's hidden keypad, opened by holding Search for [Config.HOLD_TO_UNLOCK_MS].
     * Memory only; closed whenever the app leaves the screen.
     */
    var keypadOpen by mutableStateOf(false)
        private set

    /** The hold on Search: the keypad, or setup on a fresh install. */
    fun faceHold() {
        if (!vault.exists()) { screen = Screen.SETUP; return }
        keypadOpen = true
    }

    fun closeKeypad() { keypadOpen = false }

    /**
     * The keypad's code. The keypad stays up while the code is checked, then closes: a wrong
     * code just leaves the Dictionary as it was. The caller zeros [code].
     */
    fun keypadSubmit(code: ByteArray) {
        tryCode(code) { keypadOpen = false }
    }

    fun hasVault() = vault.exists()

    fun startSetup() { screen = Screen.SETUP }

    /**
     * Plain code entry: the undisguised face's number pad and the Dictionary's keypad.
     * [onWrong] runs on the main thread if the code is wrong. The caller zeros [code] afterwards;
     * this works on its own copy.
     */
    fun tryCode(code: ByteArray, onWrong: () -> Unit) {
        if (!vault.exists()) { screen = Screen.SETUP; return }
        val copy = code.copyOf()
        val gen = generation
        scope.launch {
            val s = withContext(Dispatchers.Default) { try { vault.unlock(copy) } finally { copy.fill(0) } }
            if (s == null) { if (gen == generation) onWrong(); return@launch }
            if (gen != generation) { s.wipe(); return@launch }
            open(s)
        }
    }

    /** Setup finished: the new vault is open. The caller zeros [code]. */
    suspend fun finishSetup(code: ByteArray): Boolean {
        val gen = generation
        val s = withContext(Dispatchers.Default) { vault.create(code) }
        if (gen != generation) { s.wipe(); return false }
        // A first code set on the app's own face: the reader offers to disguise the app now.
        offerDisguise = disguise == Disguise.READER
        open(s)
        return true
    }

    /** True right after the first code is set on the app's own face, until answered. Memory only. */
    var offerDisguise by mutableStateOf(false)

    fun cancelSetup() {
        screen = Screen.FRONT
    }

    private fun open(s: VaultSession) {
        session = s
        keypadOpen = false
        vaultOpens++
        screen = Screen.INSIDE
    }

    /**
     * True while the system file picker is open for an export or import. Leaving the app for the
     * picker then does not lock straight away (see MainActivity), only after a grace period.
     */
    var pickerOpen = false

    fun wipeEnabled() = vault.wipeEnabled()

    fun setWipeEnabled(enabled: Boolean) = vault.setWipeEnabled(enabled)

    /** Re-wraps the vault key under [code]. The caller zeros [code]. */
    suspend fun changeCode(code: ByteArray) {
        val s = session ?: return
        withContext(Dispatchers.Default) { vault.changeCode(s, code) }
    }

    /** Before-lock hook so the vault screen can save what is on it. */
    var beforeLock: ((VaultSession) -> Unit)? = null

    /** Panic button, corner double-tap, back, idle timeout, background or screen off. */
    fun lock() {
        generation++
        session?.let { s ->
            try { beforeLock?.invoke(s) } catch (_: Exception) {}
            s.wipe()
        }
        session = null
        keypadOpen = false
        screen = Screen.FRONT
    }

    fun eraseEverything() {
        lock()
        vault.erase()
    }

    fun onBackground() {
        pickerOpen = false
        lock()
    }
}
