package org.openmmo.bytedex.proxy.tls

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

interface TlsContext {
    fun encrypt(data: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

object NoOpTlsContext : TlsContext {
    override fun encrypt(data: ByteArray): ByteArray = data
    override fun decrypt(data: ByteArray): ByteArray = data
}

enum class TlsRole {
    SERVER,
    CLIENT,
}

class DefaultTlsContext(
    privateKey: PrivateKey,
    peerPublicKey: PublicKey,
    role: TlsRole,
) : TlsContext {

    private val encryptionCipher: Cipher
    private val decryptionCipher: Cipher
    val clientSeed: ByteArray
    val serverSeed: ByteArray

    init {
        val shared = ecdh(privateKey, peerPublicKey)
        clientSeed = if (shared.size * 8 >= 128) tripleHash(shared, CLIENT_KEY_SALT) else FALLBACK_CLIENT_SEED
        serverSeed = if (shared.size * 8 >= 128) tripleHash(shared, SERVER_KEY_SALT) else FALLBACK_SERVER_SEED

        val (encSeed, decSeed) = when (role) {
            TlsRole.SERVER -> serverSeed to clientSeed
            TlsRole.CLIENT -> clientSeed to serverSeed
        }
        encryptionCipher = aesCtr(Cipher.ENCRYPT_MODE, encSeed)
        decryptionCipher = aesCtr(Cipher.DECRYPT_MODE, decSeed)
    }

    override fun encrypt(data: ByteArray): ByteArray = encryptionCipher.update(data)
    override fun decrypt(data: ByteArray): ByteArray = decryptionCipher.update(data)

    private fun ecdh(priv: PrivateKey, peer: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(peer, true)
        return ka.generateSecret()
    }

    private fun aesCtr(mode: Int, seed: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(mode, SecretKeySpec(seed, "AES"), IvParameterSpec(tripleHash(seed, COMMON_IV)))
        return cipher
    }

    companion object {
        private val COMMON_IV = "IVDERIV".toByteArray()
        private val CLIENT_KEY_SALT = "KeySalt".toByteArray() + byteArrayOf(1)
        private val SERVER_KEY_SALT = "KeySalt".toByteArray() + byteArrayOf(2)

        private val FALLBACK_CLIENT_SEED = byteArrayOf(
            63, 24, -15, 98, 114, 7, 68, 24, -12, 109, -111, -105, 66, -96, -2, -55,
        )
        private val FALLBACK_SERVER_SEED = byteArrayOf(
            31, -102, -128, 60, -103, 38, 10, -117, -105, -50, 2, 116, -83, 57, 39, -76,
        )

        fun tripleHash(a: ByteArray, b: ByteArray): ByteArray {
            val sha = MessageDigest.getInstance("SHA-256")
            sha.update(b); sha.update(a); sha.update(b)
            return sha.digest().copyOfRange(0, 16)
        }
    }
}
