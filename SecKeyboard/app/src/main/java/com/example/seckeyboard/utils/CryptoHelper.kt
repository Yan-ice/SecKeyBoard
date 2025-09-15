package com.example.seckeyboard.utils

import android.nfc.*
import android.nfc.tech.Ndef
import android.util.Log
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val TAG = "NfcHelper"

    // 简单固定16字节AES密钥（仅示范，实际需安全管理）
    private val AES_KEY = "1234567890abcdef".toByteArray(Charsets.UTF_8)
    private val AES_IV = "abcdef1234567890".toByteArray(Charsets.UTF_8) // IV

    private val charset = Charset.forName("UTF-8")

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
