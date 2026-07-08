package com.erfangholami.solidshare.data.local.cache

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Instrumented test — CacheKeyManager exercises the real Android Keystore, which Robolectric
 * cannot shadow, so this must run on a device or emulator (./gradlew connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class CacheKeyManagerTest {

    private lateinit var keyManager: CacheKeyManager

    @Before
    fun setUp() {
        keyManager = CacheKeyManager(ApplicationProvider.getApplicationContext())
        keyManager.resetPassphrase()
    }

    @Test
    fun databasePassphrase_is32BytesAndStableAcrossCalls() {
        val first = keyManager.databasePassphrase()
        val second = keyManager.databasePassphrase()

        assertEquals(32, first.size)
        assertArrayEquals("passphrase must persist so the encrypted DB reopens", first, second)
    }

    @Test
    fun encryptDecrypt_bytes_roundTrips() {
        val plaintext = "the quick brown fox".toByteArray()

        val cipher = keyManager.encrypt(plaintext)

        assertFalse("ciphertext must differ from plaintext", cipher.contentEquals(plaintext))
        assertArrayEquals(plaintext, keyManager.decrypt(cipher))
    }

    @Test
    fun encryptDecrypt_streams_roundTrip() {
        val plaintext = ByteArray(5_000) { (it % 251).toByte() }
        val encrypted = File.createTempFile("enc", null)

        keyManager.encryptStream(plaintext.inputStream(), encrypted)
        val out = ByteArrayOutputStream()
        keyManager.decryptStream(encrypted, out)

        assertArrayEquals(plaintext, out.toByteArray())
        encrypted.delete()
    }
}
