package org.openmmo.bytedex.proxy.tls

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private const val UNCOMPRESSED: Byte = 0x04
private val keyFactory: KeyFactory = KeyFactory.getInstance("EC")
private val ecParameterSpec: ECParameterSpec by lazy {
    val ap = AlgorithmParameters.getInstance("EC")
    ap.init(ECGenParameterSpec("secp256r1"))
    ap.getParameterSpec(ECParameterSpec::class.java)
}

fun ECPublicKey.toUncompressedPoint(): ByteArray {
    val keyLen = (ecParameterSpec.order.bitLength() + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
    val data = ByteArray(1 + 2 * keyLen)
    data[0] = UNCOMPRESSED
    writeAffine(w.affineX.toByteArray(), data, 1, keyLen)
    writeAffine(w.affineY.toByteArray(), data, 1 + keyLen, keyLen)
    return data
}

fun ByteArray.toECPublicKey(): ECPublicKey {
    require(this[0] == UNCOMPRESSED) { "not an uncompressed EC point" }
    val keyLen = (ecParameterSpec.order.bitLength() + Byte.SIZE_BITS - 1) / Byte.SIZE_BITS
    require(size == 1 + 2 * keyLen) { "EC point has wrong length" }
    val x = BigInteger(1, copyOfRange(1, 1 + keyLen))
    val y = BigInteger(1, copyOfRange(1 + keyLen, 1 + 2 * keyLen))
    return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), ecParameterSpec)) as ECPublicKey
}

private fun writeAffine(value: ByteArray, dest: ByteArray, offset: Int, keyLen: Int) {
    when {
        value.size <= keyLen ->
            System.arraycopy(value, 0, dest, offset + keyLen - value.size, value.size)
        value.size == keyLen + 1 && value[0] == 0.toByte() ->
            System.arraycopy(value, 1, dest, offset, keyLen)
        else -> error("EC affine coordinate too large")
    }
}

fun ECPrivateKey.sign(data: ByteArray): ByteArray {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initSign(this)
    sig.update(data)
    return sig.sign()
}

fun ECPublicKey.verify(data: ByteArray, signature: ByteArray): Boolean {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initVerify(this)
    sig.update(data)
    return sig.verify(signature)
}

fun decodeSpkiPublicKey(b64: String): ECPublicKey =
    keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(b64))) as ECPublicKey

fun decodePkcs8PrivateKey(b64: String): ECPrivateKey =
    keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64))) as ECPrivateKey
