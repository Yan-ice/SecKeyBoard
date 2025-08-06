package com.example.seckeyboard.utils

import android.nfc.*
import android.nfc.tech.Ndef
import android.util.Log
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NfcHelper {

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

    /** 判断NDEF消息是否是我们协议的消息 */
    fun isOurNdefMessage(msg: NdefMessage): Boolean {
        return msg.records.any { record ->
            record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(record.type, charset) == "application/vnd.myapp.secure"
        }
    }

    /** 从NDEF消息中提取协议负载（解密后字符串） */
    fun extractPayload(msg: NdefMessage): String? {
        val record = msg.records.find {
            it.tnf == NdefRecord.TNF_MIME_MEDIA &&
                    String(it.type, charset) == "application/vnd.myapp.secure"
        } ?: return null

        return try {
            val decrypted = aesDecrypt(record.payload)
            String(decrypted, charset)
        } catch (e: Exception) {
            Log.e(TAG, "解密失败", e)
            null
        }
    }

    /** 生成NDEF消息，负载为加密后的字符串 */
    fun createNdefMessage(plainText: String): NdefMessage {
        val encrypted = aesEncrypt(plainText.toByteArray(charset))
        val record = NdefRecord.createMime("application/vnd.myapp.secure", encrypted)
        return NdefMessage(arrayOf(record))
    }

    /** 读写NDEF标签 */
    fun writeNdefMessage(tag: Tag, message: NdefMessage): Boolean {
        try {
            val ndef = Ndef.get(tag) ?: return false
            ndef.connect()
            if (!ndef.isWritable) {
                Log.e(TAG, "标签不可写")
                ndef.close()
                return false
            }
            if (ndef.maxSize < message.toByteArray().size) {
                Log.e(TAG, "标签空间不足")
                ndef.close()
                return false
            }
            ndef.writeNdefMessage(message)
            ndef.close()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "写入标签异常", e)
            return false
        }
    }

    /** 读取标签NDEF消息 */
    fun readNdefMessage(tag: Tag): NdefMessage? {
        return try {
            val ndef = Ndef.get(tag) ?: return null
            ndef.connect()
            val msg = ndef.ndefMessage
            ndef.close()
            msg
        } catch (e: Exception) {
            Log.e(TAG, "读取标签异常", e)
            null
        }
    }
}
