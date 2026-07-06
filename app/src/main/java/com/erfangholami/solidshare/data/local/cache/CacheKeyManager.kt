package com.erfangholami.solidshare.data.local.cache

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheKeyManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val secretKey: SecretKey by lazy { loadOrCreateKey() }

    fun databasePassphrase(): ByteArray {
        val file = passphraseFile()
        if (file.exists()) {
            return decrypt(file.readBytes())
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        file.writeBytes(encrypt(passphrase))
        return passphrase
    }

    fun resetPassphrase() {
        passphraseFile().delete()
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + ciphertext.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(ciphertext, 0, this, 1 + iv.size, ciphertext.size)
        }
    }

    fun decrypt(payload: ByteArray): ByteArray {
        val ivSize = payload[0].toInt()
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptStream(source: InputStream, target: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        target.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            CipherOutputStream(out, cipher).use { encrypted -> source.copyTo(encrypted) }
        }
    }

    fun decryptStream(source: File, target: OutputStream) {
        DataInputStream(source.inputStream()).use { input ->
            val iv = ByteArray(input.readUnsignedByte())
            input.readFully(iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            CipherInputStream(input, cipher).use { decrypted -> decrypted.copyTo(target) }
        }
    }

    private fun passphraseFile(): File = File(context.filesDir, PASSPHRASE_FILE)

    private fun loadOrCreateKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "solidshare_cache_key"
        const val PASSPHRASE_FILE = "solid_cache.pass"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
