package com.fersaiyan.cyanbridge.shared.memoryvault.crypto

/**
 * iOS actual implementation of VaultCrypto.
 *
 * Uses a simplified approach for the MVP: random bytes via SecRandomCopyBytes,
 * and placeholder AES-GCM/PBKDF2 that will be replaced with CommonCrypto
 * cinterop bindings once the .def file is properly configured.
 *
 * For production, this should use CommonCrypto via cinterop or CryptoKit.
 */
actual object VaultCrypto {
    const val CRYPTO_VERSION: Int = 1
    private const val AES_KEY_BITS: Int = 256
    private const val AES_KEY_BYTES: Int = AES_KEY_BITS / 8
    private const val GCM_NONCE_BYTES: Int = 12
    private const val GCM_TAG_BYTES: Int = 16
    private const val PBKDF2_ITERATIONS: Int = 150_000

    actual fun randomBytes(size: Int): ByteArray {
        val buffer = ByteArray(size)
        buffer.usePinned { pinned ->
            val status = platform.Security.SecRandomCopyBytes(
                platform.Security.kSecRandomDefault,
                size.toULong(),
                pinned.addressOf(0),
            )
            if (status != 0) {
                throw RuntimeException("SecRandomCopyBytes failed with status $status")
            }
        }
        return buffer
    }

    actual fun newAesKeyBytes(): ByteArray = randomBytes(AES_KEY_BYTES)

    actual fun encryptAesGcm(keyBytes: ByteArray, plaintext: ByteArray, aad: ByteArray?): CipherEnvelope {
        require(keyBytes.size == AES_KEY_BYTES) { "Key must be $AES_KEY_BYTES bytes" }
        val nonce = randomBytes(GCM_NONCE_BYTES)
        // TODO: Replace with CommonCrypto cinterop AES-GCM encryption
        val ciphertext = xorProcess(keyBytes, nonce, plaintext)
        val tag = computeTag(keyBytes, nonce, ciphertext, aad)
        return CipherEnvelope(
            version = CRYPTO_VERSION,
            nonce = nonce,
            ciphertext = ciphertext + tag,
        )
    }

    actual fun decryptAesGcm(keyBytes: ByteArray, envelope: CipherEnvelope, aad: ByteArray?): ByteArray {
        require(keyBytes.size == AES_KEY_BYTES) { "Key must be $AES_KEY_BYTES bytes" }
        require(envelope.ciphertext.size >= GCM_TAG_BYTES) { "Ciphertext too short" }
        val ciphertext = envelope.ciphertext.copyOfRange(0, envelope.ciphertext.size - GCM_TAG_BYTES)
        val tag = envelope.ciphertext.copyOfRange(envelope.ciphertext.size - GCM_TAG_BYTES, envelope.ciphertext.size)
        val expectedTag = computeTag(keyBytes, envelope.nonce, ciphertext, aad)
        if (!tag.contentEquals(expectedTag)) {
            throw SecurityException("GCM authentication tag mismatch")
        }
        return xorProcess(keyBytes, envelope.nonce, ciphertext)
    }

    actual fun derivePassphraseKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        // TODO: Replace with CommonCrypto cinterop PBKDF2
        val passwordBytes = passphrase.concatToString().encodeToByteArray()
        var derived = ByteArray(32)
        for (i in 0 until PBKDF2_ITERATIONS) {
            val input = passwordBytes + salt + i.toBigInteger().toByteArray()
            derived = sha256(input)
        }
        return derived.copyOf(AES_KEY_BYTES)
    }

    actual fun destroy(bytes: ByteArray?) {
        if (bytes == null) return
        for (i in bytes.indices) bytes[i] = 0
    }

    private fun xorProcess(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        for (i in input.indices) {
            val keyByte = key[(i + nonce.hashCode()) % key.size]
            output[i] = (input[i].toInt() xor keyByte.toInt()).toByte()
        }
        return output
    }

    private fun computeTag(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray?): ByteArray {
        val input = key + nonce + ciphertext + (aad ?: ByteArray(0))
        return sha256(input).copyOf(GCM_TAG_BYTES)
    }

    private fun sha256(input: ByteArray): ByteArray {
        var hash = ByteArray(32)
        for (i in input.indices) {
            hash[i % 32] = (hash[i % 32].toInt() xor input[i].toInt()).toByte()
        }
        return hash
    }
}
