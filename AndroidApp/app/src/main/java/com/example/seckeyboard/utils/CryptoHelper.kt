package com.example.seckeyboard.utils

import android.content.Context
import android.nfc.*
import android.nfc.tech.Ndef
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.example.seckeyboard.protocol.SharedState
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private val AES_IV = "abcdef1234567890".toByteArray(Charsets.UTF_8) // IV

    internal val charset = Charset.forName("UTF-8")

    fun toHexString(b: ByteArray): String {
        return b.joinToString(" ") {
            String.format("%02X", it)
        }
    }

    // HKDF-SHA256
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

    fun ECDHgen(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val kp: KeyPair = kpg.generateKeyPair()
        return kp
    }

    fun ECDHcal(serverPubBytes: ByteArray, clientPriv: PrivateKey): ByteArray {


        val kf = KeyFactory.getInstance("EC")
        val x509Spec = X509EncodedKeySpec(serverPubBytes)
        val serverTempPubKey = kf.generatePublic(x509Spec) as ECPublicKey


        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(clientPriv)
        ka.doPhase(serverTempPubKey, true)
        val sharedSecret = ka.generateSecret() // 32-byte raw secret (may be larger in some impls)


        val info = "handshake data".toByteArray()
        val aesKey = hkdfSha256(sharedSecret, info, null, 32) // AES-256 key

        return aesKey
    }


    fun aesEncrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(SharedState.sessionKey, "AES")
        val ivSpec = IvParameterSpec(AES_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }


    fun aesEncrypt(sdata: String): ByteArray {
        val data = sdata.toByteArray(charset)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(SharedState.sessionKey, "AES")
        val ivSpec = IvParameterSpec(AES_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }


    fun aesDecrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(SharedState.sessionKey, "AES")
        val ivSpec = IvParameterSpec(AES_IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }


    fun generateAttestedKey(alias: String, challenge: ByteArray) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setAttestationChallenge(challenge) // Attestation Challenge
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun getAttestationCertificateChain(context: Context, alias: String): Array<Certificate>? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        return entry?.certificateChain
    }
    fun attestedKeySignData(alias: String, data: ByteArray): ByteArray? {
        return try {

            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey ?: return null

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)

            signature.update(data)

            signature.sign()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
