package com.example.seckeyboard.protocol

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.example.seckeyboard.protocol.SharedState
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SubmitService : HostApduService() {

    companion object {
        const val TAG = "HCE_Service"
        val AID = "F020020520"

        val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    }

    val iv = byteArrayOf(
        0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0xD4.toByte(),
        0xE5.toByte(), 0xF6.toByte(), 0x07.toByte(), 0x18.toByte(),
        0x29.toByte(), 0x3A.toByte(), 0x4B.toByte(), 0x5C.toByte()
    )

    private var encryptedPasswordChunks: List<ByteArray>? = null
    private var currentChunkIndex = 0

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.genKeyPair()
    private val publicKey = keyPair.public as RSAPublicKey

    private val publicKeyEncoded = publicKey.encoded
    private val pubHalfLength = publicKeyEncoded.size / 2
    private val pubKeyPart1 = publicKeyEncoded.sliceArray(0 until pubHalfLength)
    private val pubKeyPart2 = publicKeyEncoded.sliceArray(pubHalfLength until publicKeyEncoded.size)

    private val privateKey = keyPair.private


    private var aesPart1: ByteArray? = null
    private var aesPart2: ByteArray? = null
    private val fullAESKey: ByteArray?
        get() = if (aesPart1 != null && aesPart2 != null)
            aesPart1!! + aesPart2!!
        else null

    private fun encryptPasswordChunks(password: String, aesKey: ByteArray): List<ByteArray> {
        val aes = SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        // encrypted = 密文 + tag
        val payload = iv + encrypted

        val chunkSize = 240
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val end = (offset + chunkSize).coerceAtMost(payload.size)
            chunks.add(payload.sliceArray(offset until end))
            offset = end
        }
        return chunks
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null){
            Log.d(TAG, "No command APDU.")
            return STATUS_FAILED
        }

        Log.d(TAG, "APDU received: ${commandApdu.joinToString(" ") { "%02X".format(it) }}")


        if (!SharedState.screenActive) return STATUS_FAILED

        return when (commandApdu[1]) {
            0xA4.toByte() -> {
                SharedState.currentStatus = "读卡器连接成功"
                Log.d(TAG, "SELECT AID: ${STATUS_SUCCESS.joinToString(" ") { "%02X".format(it) }}")
                STATUS_SUCCESS
            }

            0xB0.toByte() -> {
                SharedState.currentStatus = "发送公钥前半段 (${pubKeyPart1.size}字节)"
                Log.d(TAG, "SEND PUB1")
                pubKeyPart1 + STATUS_SUCCESS
            }
            0xB1.toByte() -> {
                SharedState.currentStatus = "发送公钥后半段 (${pubKeyPart2.size}字节)"
                Log.d(TAG, "SEND PUB2")
                pubKeyPart2 + STATUS_SUCCESS
            }

            0xD0.toByte() -> {  // 收第一段AES密钥
                aesPart1 = commandApdu.copyOfRange(5, commandApdu.size)
                SharedState.currentStatus = "收到AES密钥前半段 (${aesPart1?.size}字节)"
                Log.d(TAG, "PART1: ${aesPart1?.size} bytes")
                STATUS_SUCCESS
            }

            0xD1.toByte() -> {  // 收第二段AES密钥
                aesPart2 = commandApdu.copyOfRange(5, commandApdu.size)
                SharedState.currentStatus = "收到AES密钥后半段 (${aesPart2?.size}字节)"
                Log.d(TAG, "PART2: ${aesPart2?.size} bytes")
                STATUS_SUCCESS
            }

            0xD2.toByte() -> {  // 解密密码
                val payload = commandApdu.copyOfRange(5, commandApdu.size)
                val keyEncrypted = fullAESKey
                if (keyEncrypted == null) {
                    SharedState.currentStatus = "AES密钥不完整"
                    return STATUS_FAILED
                }
                return try {
                    val iv = payload.sliceArray(0 until 12)
                    val cipherText = payload.sliceArray(12 until payload.size - 16)
                    val tag = payload.sliceArray(payload.size - 16 until payload.size)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val aes = SecretKeySpec(decryptRSA(keyEncrypted), "AES")
                    cipher.init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, iv))
                    val decrypted = cipher.doFinal(cipherText + tag)
                    SharedState.currentStatus = "密码解密成功：${String(decrypted)}"
                    STATUS_SUCCESS
                } catch (e: Exception) {
                    SharedState.currentStatus = "密文解密失败：${e.message}"
                    STATUS_FAILED
                }
            }

            0xD3.toByte() -> { // 读卡器请求发送加密密码分包
                val keyEncrypted = fullAESKey ?: return STATUS_FAILED
                val password = SharedState.password ?: return STATUS_FAILED  // 你需要在SharedState里保存传过来的密码

                if (encryptedPasswordChunks == null) {
                    // 第一次进来，进行加密并切分
                    encryptedPasswordChunks = encryptPasswordChunks(password, decryptRSA(keyEncrypted))
                    Log.d(TAG, "加密密码 ${password}")
                    currentChunkIndex = 0
                }

                if (currentChunkIndex >= encryptedPasswordChunks!!.size) {
                    // 所有包发完
                    encryptedPasswordChunks = null
                    currentChunkIndex = 0
                    return STATUS_SUCCESS
                }

                val chunk = encryptedPasswordChunks!![currentChunkIndex]
                currentChunkIndex++
                Log.d(TAG, "发送加密密码分包 ${currentChunkIndex}/${encryptedPasswordChunks!!.size}, 长度 ${chunk.size} 字节")
                chunk + STATUS_SUCCESS
            }

            else -> STATUS_FAILED
        }
    }

    override fun onDeactivated(reason: Int) {
        SharedState.currentStatus = "连接断开"
    }

    private fun decryptRSA(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(data)
    }
}