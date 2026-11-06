package com.tbce.calc.vault

import com.tbce.calc.Config
import java.io.File
import java.nio.ByteBuffer

/**
 * Key hierarchy (brief section 5):
 *   KEK_code   = Argon2id(code, salt)
 *   KEK_device = Android Keystore key
 *   stored     = GCM_KEK_device( GCM_KEK_code( DEK ) )
 *
 * The code is never stored. A right code is recognised only because the inner GCM tag verifies,
 * and right and wrong codes run the same derivation.
 *
 * Without a code (an option from 1.11) the same hierarchy is kept with an empty code,
 * so KEK_device alone protects the data, and the guard file records that no code is asked for.
 */
class KeyVault(
    private val dir: File,
    private val device: DeviceKey,
    private val memoryKb: Int = Config.KDF_MEMORY_KB,
    private val iterations: Int = Config.KDF_ITERATIONS,
    private val parallelism: Int = Config.KDF_PARALLELISM,
) {
    private val keyFile = File(dir, KEY_FILE)
    /**
     * Wipe setting, failed-attempt count and whether a code is asked for, sealed with the device
     * key: [wipe byte][count int][no-code byte]. Files written before 1.11 have no last byte.
     */
    private val guardFile = File(dir, "c1")
    private val dataDir = File(dir, "d")

    fun exists(): Boolean = keyFile.exists()

    /** First-run setup. An empty [code] sets the vault up without a code. The caller zeros [code]. */
    fun create(code: ByteArray): VaultSession {
        erase()
        val dek = Crypto.randomBytes(32)
        val salt = Crypto.randomBytes(16)
        val kek = Crypto.argon2id(code, salt, memoryKb, iterations, parallelism)
        val inner = try { Crypto.seal(kek, dek) } finally { kek.fill(0) }
        device.create()
        writeKey(salt, device.encrypt(inner))
        writeGuard(Guard(wipe = false, count = 0, noCode = code.isEmpty()))
        return VaultSession(dek, dataDir)
    }

    /**
     * Re-wraps the open vault's key under [newCode]; nothing else is re-encrypted. An empty
     * [newCode] removes the code. The caller zeros [newCode].
     */
    fun changeCode(session: VaultSession, newCode: ByteArray) {
        check(!session.wiped)
        val salt = Crypto.randomBytes(16)
        val kek = Crypto.argon2id(newCode, salt, memoryKb, iterations, parallelism)
        val inner = try { Crypto.seal(kek, session.key()) } finally { kek.fill(0) }
        writeKey(salt, device.encrypt(inner))
        writeGuard(readGuard().copy(count = 0, noCode = newCode.isEmpty()))
    }

    private fun writeKey(salt: ByteArray, outer: ByteArray) {
        val header = ByteBuffer.allocate(HEADER).put(1).put(salt)
            .putInt(memoryKb).putInt(iterations).putInt(parallelism).array()
        writeAtomic(keyFile, header + outer)
    }

    /** Whether the vault erases itself after [Config.WIPE_AFTER] wrong codes in a row. */
    fun wipeEnabled(): Boolean = readGuard().wipe

    fun setWipeEnabled(enabled: Boolean) {
        writeGuard(readGuard().copy(wipe = enabled))
    }

    /** False when the vault was set up, or changed, to open without a code. */
    fun hasCode(): Boolean = exists() && !readGuard().noCode

    /** Opens a vault that has no code; null if it has one (no attempt is counted then). */
    fun unlockWithoutCode(): VaultSession? = if (exists() && readGuard().noCode) unlock(ByteArray(0)) else null

    private data class Guard(val wipe: Boolean, val count: Int, val noCode: Boolean)

    private fun readGuard(): Guard = try {
        val b = ByteBuffer.wrap(device.decrypt(guardFile.readBytes()))
        Guard(b.get().toInt() == 1, b.int, b.hasRemaining() && b.get().toInt() == 1)
    } catch (e: Exception) {
        Guard(wipe = false, count = 0, noCode = false)
    }

    private fun writeGuard(g: Guard) {
        try {
            val b = ByteBuffer.allocate(6).put(if (g.wipe) 1 else 0).putInt(g.count).put(if (g.noCode) 1 else 0)
            writeAtomic(guardFile, device.encrypt(b.array()))
        } catch (_: Exception) {
        }
    }

    /** Returns a session if [code] is right, null otherwise. The caller zeros [code]. */
    fun unlock(code: ByteArray): VaultSession? {
        val blob = try { keyFile.readBytes() } catch (e: Exception) { return null }
        if (blob.size <= HEADER) return null
        val b = ByteBuffer.wrap(blob)
        b.get()
        val salt = ByteArray(16).also { b.get(it) }
        val m = b.int
        val t = b.int
        val p = b.int
        val kek = Crypto.argon2id(code, salt, m, t, p)
        val session = try {
            val inner = device.decrypt(blob.copyOfRange(HEADER, blob.size))
            VaultSession(Crypto.open(kek, inner), dataDir)
        } catch (e: Exception) {
            null
        } finally {
            kek.fill(0)
        }
        // The guard file is rewritten on both paths, so a wrong code makes no extra disk activity.
        val guard = readGuard()
        val failures = if (session != null) 0 else guard.count + 1
        if (session == null && guard.wipe && failures >= Config.WIPE_AFTER) {
            erase()
            return null
        }
        writeGuard(guard.copy(count = failures))
        return session
    }

    /** Wrong codes since the last right one (for tests). */
    internal fun failures(): Int = readGuard().count

    /** Crypto-erase: the wrapped key and the hardware key go first, then the data, best effort. */
    fun erase() {
        keyFile.delete()
        guardFile.delete()
        try { device.destroy() } catch (_: Exception) {}
        dataDir.deleteRecursively()
    }

    companion object {
        private const val HEADER = 1 + 16 + 4 + 4 + 4
        private const val KEY_FILE = "c0"

        /** Whether a vault has been set up in [dir], without needing the device key. */
        fun existsIn(dir: File): Boolean = File(dir, KEY_FILE).exists()
    }
}

internal fun writeAtomic(target: File, bytes: ByteArray) {
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, target.name + ".t")
    tmp.outputStream().use { it.write(bytes); it.fd.sync() }
    if (!tmp.renameTo(target)) {
        target.delete()
        tmp.renameTo(target)
    }
}
