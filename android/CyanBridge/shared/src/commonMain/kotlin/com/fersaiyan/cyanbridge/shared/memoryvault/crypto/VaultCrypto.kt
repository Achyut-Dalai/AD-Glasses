package com.achyut.adglasses.shared.memoryvault.crypto

data class CipherEnvelope(
    val version: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CipherEnvelope) return false
        return version == other.version &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * Cross-platform AES-256-GCM encryption/decryption abstraction.
 * Android uses javax.crypto; iOS uses CommonCrypto.
 */
expect object VaultCrypto {
    fun randomBytes(size: Int): ByteArray
    fun newAesKeyBytes(): ByteArray
    fun encryptAesGcm(keyBytes: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): CipherEnvelope
    fun decryptAesGcm(keyBytes: ByteArray, envelope: CipherEnvelope, aad: ByteArray? = null): ByteArray
    fun derivePassphraseKey(passphrase: CharArray, salt: ByteArray): ByteArray
    fun destroy(bytes: ByteArray?)
}
