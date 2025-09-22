package com.example.seckeyboard.utils

import android.nfc.*
import android.nfc.tech.Ndef
import android.util.Log
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    // 简单固定16字节AES密钥（仅示范，实际需安全管理）
    private val AES_KEY = "1234567890abcdef".toByteArray(Charsets.UTF_8)
    private val AES_IV = "abcdef1234567890".toByteArray(Charsets.UTF_8) // IV

    private val charset = Charset.forName("UTF-8")

    // HKDF-SHA256 实现（简化）
    // Extract (salt optional) + Expand
    fun hkdfSha256Extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(salt ?: ByteArray(32) {0}, "HmacSHA256")
        mac.init(key)
        return mac.doFinal(ikm)
    }
    fun hkdfSha256Expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(prk, "HmacSHA256")
        mac.init(key)
        var t = ByteArray(0)
        var okm = ByteArray(0)
        var i = 1
        while (okm.size < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            okm += t
            i++
        }
        return okm.copyOfRange(0, length)
    }
    fun hkdfSha256(ikm: ByteArray, info: ByteArray = "handshake data".toByteArray(), salt: ByteArray? = null, length: Int = 32): ByteArray {
        val prk = hkdfSha256Extract(salt, ikm)
        return hkdfSha256Expand(prk, info, length)
    }

    fun ECDHgen(serverPubBytes: ByteArray) {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val kp: KeyPair = kpg.generateKeyPair()
        val clientPriv = kp.private
        val clientPub = kp.public

        // 3) 从 server_pub_x509 构造 PublicKey 对象
        val kf = KeyFactory.getInstance("EC")
        val x509Spec = X509EncodedKeySpec(serverPubBytes)
        val serverTempPubKey = kf.generatePublic(x509Spec) as ECPublicKey

        // 4) ECDH 计算 shared secret
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(clientPriv)
        ka.doPhase(serverTempPubKey, true)
        val sharedSecret = ka.generateSecret() // 32-byte raw secret (may be larger in some impls)

        // 5) HKDF -> AES key
        val info = "handshake data".toByteArray()
        val aesKey = hkdfSha256(sharedSecret, info, null, 32) // AES-256 key
    }
    /** AES CBC PKCS5Padding 加密 */
    fun aesEncrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /** AES CBC PKCS5Padding 解密 */
    fun aesDecrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(AES_KEY, "AES")
        val ivSpec = IvParameterSpec(AES_IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }
}
