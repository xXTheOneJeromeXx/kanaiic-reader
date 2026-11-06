package com.tbce.calc

import com.tbce.calc.vault.Crypto
import com.tbce.calc.vault.DeviceKey
import com.tbce.calc.vault.KeyVault
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Crypto tests from the brief's section 12, with a software stand-in for the Keystore key. */
class KeyVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeDeviceKey : DeviceKey {
        var key: ByteArray? = null
        override fun create() { key = Crypto.randomBytes(32) }
        override fun encrypt(plain: ByteArray) = Crypto.seal(key!!, plain)
        override fun decrypt(sealed: ByteArray) = Crypto.open(key!!, sealed)
        override fun destroy() { key = null }
    }

    private val device = FakeDeviceKey()
    private fun vault() = KeyVault(tmp.root, device, memoryKb = 1024, iterations = 1, parallelism = 1)
    private fun code(s: String) = s.toByteArray()

    @Test fun argon2idKnownAnswer() {
        // RFC 9106 section 5.3 test vector for Argon2id.
        val params = org.bouncycastle.crypto.params.Argon2Parameters.Builder(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
            .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(ByteArray(16) { 2 })
            .withSecret(ByteArray(8) { 3 })
            .withAdditional(ByteArray(12) { 4 })
            .withMemoryAsKB(32).withIterations(3).withParallelism(4).build()
        val out = ByteArray(32)
        org.bouncycastle.crypto.generators.Argon2BytesGenerator().apply { init(params) }.generateBytes(ByteArray(32) { 1 }, out)
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", Hex.toHexString(out))
    }

    @Test fun rightCodeOpensWrongCodeDoesNot() {
        val v = vault()
        assertFalse(v.exists())
        v.create(code("12345678")).apply { put("x", "hello".toByteArray()); wipe() }
        assertTrue(v.exists())
        assertNull(v.unlock(code("12345679")))
        assertNull(v.unlock(code("")))
        val s = v.unlock(code("12345678"))
        assertNotNull(s)
        assertArrayEquals("hello".toByteArray(), s!!.get("x"))
    }

    @Test fun wipeZerosKeys() {
        val s = vault().create(code("123456"))
        s.wipe()
        assertTrue(s.wiped)
        assertTrue(s.keysAreZero())
    }

    @Test fun tamperedKeyFileFails() {
        val v = vault()
        v.create(code("123456")).wipe()
        val f = File(tmp.root, "c0")
        val b = f.readBytes()
        b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte()
        f.writeBytes(b)
        assertNull(v.unlock(code("123456")))
    }

    @Test fun tamperedItemFails() {
        val v = vault()
        v.create(code("123456")).apply { put("x", "hello".toByteArray()); wipe() }
        val item = File(tmp.root, "d").listFiles()!!.single()
        val b = item.readBytes()
        b[20] = (b[20].toInt() xor 1).toByte()
        item.writeBytes(b)
        val s = v.unlock(code("123456"))!!
        assertTrue(runCatching { s.get("x") }.isFailure)
    }

    @Test fun lostDeviceKeyMeansNoUnlock() {
        val v = vault()
        v.create(code("123456")).wipe()
        device.destroy()
        assertNull(v.unlock(code("123456")))
    }

    @Test fun storedFilesHoldNoCodeKeyOrPlaintext() {
        val v = vault()
        val s = v.create(code("98765432"))
        s.put("note", "plain words here".toByteArray())
        val all = tmp.root.walkTopDown().filter { it.isFile }.map { it.readBytes() }.toList()
        val dek = s.javaClass.getDeclaredField("dek").apply { isAccessible = true }.get(s) as ByteArray
        for (bytes in all) {
            assertFalse(contains(bytes, "98765432".toByteArray()))
            assertFalse(contains(bytes, "plain words".toByteArray()))
            assertFalse(contains(bytes, dek))
        }
        // Neutral file names only.
        tmp.root.walkTopDown().forEach { assertTrue(it.name, it == tmp.root || it.name.matches(Regex("c0|c1|d|[0-9a-f]{16}"))) }
    }

    @Test fun eraseRemovesEverything() {
        val v = vault()
        v.create(code("123456")).apply { put("x", byteArrayOf(1)); wipe() }
        v.erase()
        assertFalse(v.exists())
        assertNull(device.key)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    private fun contains(hay: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return false
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    @Test fun wipeAfterTenWrongCodesOnlyWhenEnabled() {
        val v = vault()
        v.create(code("123456")).wipe()
        repeat(12) { assertNull(v.unlock(code("000000"))) }
        assertTrue("off by default: nothing erased", v.exists())
        assertNotNull(v.unlock(code("123456")))
        assertEquals(0, v.failures())

        v.setWipeEnabled(true)
        repeat(9) { assertNull(v.unlock(code("000000"))) }
        assertTrue(v.exists())
        assertNotNull("a right code resets the count", v.unlock(code("123456")))
        repeat(9) { v.unlock(code("000000")) }
        assertTrue(v.exists())
        assertNull(v.unlock(code("000000")))
        assertFalse("tenth wrong code in a row erases", v.exists())
        assertNull(device.key)
    }

    @Test fun changeCodeRewrapsTheSameKey() {
        val v = vault()
        val s = v.create(code("111111"))
        s.put("x", "kept".toByteArray())
        v.changeCode(s, code("22222222"))
        s.wipe()
        assertNull(v.unlock(code("111111")))
        assertArrayEquals("kept".toByteArray(), v.unlock(code("22222222"))!!.get("x"))
    }

    @Test fun vaultWithoutACode() {
        val v = vault()
        v.create(ByteArray(0)).apply { put("x", "open".toByteArray()); wipe() }
        assertFalse(v.hasCode())
        assertArrayEquals("open".toByteArray(), v.unlockWithoutCode()!!.get("x"))
        assertNull("no code means no code is accepted", v.unlock(code("123456")))
        assertTrue("data is still sealed", File(tmp.root, "d").listFiles()!!.none { contains(it.readBytes(), "open".toByteArray()) })
    }

    @Test fun codeCanBeRemovedAndSetAgain() {
        val v = vault()
        val s = v.create(code("123456"))
        s.put("x", "kept".toByteArray())
        assertTrue(v.hasCode())
        assertNull("a vault with a code doesn't open without it", v.unlockWithoutCode())
        assertEquals("and that is not counted as a wrong code", 0, v.failures())

        v.changeCode(s, ByteArray(0))
        assertFalse(v.hasCode())
        assertNull(v.unlock(code("123456")))
        assertArrayEquals("kept".toByteArray(), v.unlockWithoutCode()!!.get("x"))

        v.changeCode(s, code("87654321"))
        s.wipe()
        assertTrue(v.hasCode())
        assertNull(v.unlockWithoutCode())
        assertArrayEquals("kept".toByteArray(), v.unlock(code("87654321"))!!.get("x"))
    }

    @Test fun guardFromBeforeNoCodeStillReads() {
        val v = vault()
        v.create(code("123456")).wipe()
        v.setWipeEnabled(true)
        // A 1.10 guard file: [wipe][count] with no no-code byte.
        File(tmp.root, "c1").writeBytes(device.encrypt(java.nio.ByteBuffer.allocate(5).put(1).putInt(3).array()))
        assertTrue(v.hasCode())
        assertTrue(v.wipeEnabled())
        assertEquals(3, v.failures())
        assertNotNull(v.unlock(code("123456")))
        assertTrue(v.wipeEnabled())
    }

    @Test fun backupRoundTrip() {
        val data = "highlights and notes".toByteArray()
        val file = com.tbce.calc.vault.Backup.seal("correct horse".toCharArray(), data)
        assertArrayEquals(data, com.tbce.calc.vault.Backup.open("correct horse".toCharArray(), file))
        assertNull(com.tbce.calc.vault.Backup.open("wrong horse!".toCharArray(), file))
        assertFalse("no plaintext in the file", contains(file, "highlights".toByteArray()))
        val tampered = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(com.tbce.calc.vault.Backup.open("correct horse".toCharArray(), tampered))
        assertNull(com.tbce.calc.vault.Backup.open("x".toCharArray(), ByteArray(10)))
    }
}
