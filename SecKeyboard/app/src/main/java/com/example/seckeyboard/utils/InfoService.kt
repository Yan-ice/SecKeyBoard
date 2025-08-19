package com.example.seckeyboard.utils

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class InfoService : HostApduService() {

    companion object {
        const val TAG = "HCE_Service"
        val AID = "F020020521"

        // INS定义
        private const val INS_INIT: Byte = 0x10
        private const val INS_CONTINUE: Byte = 0x11
        private const val INS_END: Byte = 0x12
        private const val INS_STATUS: Byte = 0x13

        // ISO-like 状态字（SW1 SW2）
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val STATUS_BAD_PARAM = byteArrayOf(0x6A.toByte(), 0x80.toByte())
        private val STATUS_MORE_DATA_PREFIX = 0x61.toByte() // 0x61 XX

        // 超时与包大小限制
        private const val RECEIVE_TIMEOUT_MS = 30_000L
        private const val MAX_TOTAL_SIZE = 10 * 1024 // 安全上限制：最大 10 KB（根据需要调整）
    }

    // 用于重组接收数据
    @Volatile
    private var receiving = false

    private val buffer = ByteArrayOutputStream()
    private var expectedTotalLength: Int? = null // 可选，INIT 包里发送
    private var lastSeq: Int = -1

    // 超时处理
    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable {
        Log.w(TAG, "Receive timeout — resetting state")
        resetState()
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            Log.d(TAG, "No command APDU.")
            return STATUS_FAILED
        }

        Log.d(TAG, "APDU received: ${commandApdu.joinToString(" ") { "%02X".format(it) }}")

        // 简单保护：屏幕活动检查或其它共享状态可以放在这里
        // if (!SharedState.screenActive) return STATUS_FAILED

        // 解析 INS（假设短 APDU: [CLA, INS, P1, P2, Lc, ...data...])
        if (commandApdu.size < 5) {
            Log.w(TAG, "APDU too short")
            return STATUS_BAD_PARAM
        }

        val ins = commandApdu[1]
        val lc = commandApdu[4].toInt() and 0xFF
        if (commandApdu.size < 5 + lc) {
            Log.w(TAG, "APDU stated length mismatch: lc=$lc but actual=${commandApdu.size - 5}")
            return STATUS_BAD_PARAM
        }
        val data = if (lc > 0) commandApdu.copyOfRange(5, 5 + lc) else ByteArray(0)

        // 每次收到命令，重置超时定时器
        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, RECEIVE_TIMEOUT_MS)

        return try {
            when (ins) {
                INS_INIT -> handleInit(data)
                INS_CONTINUE -> handleContinue(data)
                INS_END -> handleEnd(data)
                INS_STATUS -> handleStatus()
                else -> {
                    Log.w(TAG, "Unknown INS: %02X".format(ins))
                    STATUS_BAD_PARAM
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in processCommandApdu", e)
            resetState()
            STATUS_FAILED
        }
    }

    private fun handleInit(data: ByteArray): ByteArray {
        // INIT: 重置接收状态。data 可包含元信息（例如 totalLength 的 4 字节整型或 JSON）。
        Log.i(TAG, "INIT received, bytes=${data.size}")
        resetState()

        // 解析可能的 total length（如果传过来 4 字节 big-endian）
        if (data.size >= 4) {
            val len = ((data[0].toInt() and 0xFF) shl 24) or
                    ((data[1].toInt() and 0xFF) shl 16) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    (data[3].toInt() and 0xFF)
            if (len > 0 && len <= MAX_TOTAL_SIZE) {
                expectedTotalLength = len
                Log.i(TAG, "Expected total length set to $len")
            } else {
                Log.w(TAG, "Invalid total length from INIT: $len (ignored)")
            }
        } else if (data.isNotEmpty()) {
            // 如果 INIT 带的是 JSON 元数据，可选解析（示例尝试解析 UTF-8 文本）
            val txt = try { String(data, Charset.forName("UTF-8")) } catch (e: Exception) { null }
            if (!txt.isNullOrEmpty()) {
                Log.i(TAG, "INIT metadata: $txt")
                // TODO: 解析 JSON metadata（例如 totalLength 字段）
            }
        }

        receiving = true
        lastSeq = -1
        buffer.reset()

        // 回应 ACK
        return STATUS_SUCCESS
    }

    private fun handleContinue(data: ByteArray): ByteArray {
        // CONTINUE: data = [seq(1B), chunk...]
        if (!receiving) {
            Log.w(TAG, "CONTINUE received but not in receiving state")
            return STATUS_BAD_PARAM
        }
        if (data.isEmpty()) {
            Log.w(TAG, "CONTINUE no data")
            return STATUS_BAD_PARAM
        }

        val seq = data[0].toInt() and 0xFF
        val chunk = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)

        // 简单顺序检查（可根据需要更严格）
        if (lastSeq >= 0 && seq != (lastSeq + 1 and 0xFF)) {
            Log.w(TAG, "Sequence mismatch: last=$lastSeq got=$seq")
            // 但仍然接受（或返回错误以让读卡器重传）
            // return STATUS_BAD_PARAM
        }

        if (buffer.size() + chunk.size > MAX_TOTAL_SIZE) {
            Log.e(TAG, "Exceeded max receive size")
            resetState()
            return STATUS_FAILED
        }

        buffer.write(chunk)
        lastSeq = seq
        Log.d(TAG, "Received chunk seq=$seq len=${chunk.size} total=${buffer.size()}")

        // 反馈：如果我们知道 expectedTotalLength，可以告诉读卡器还需多少字节（用 0x61 XX）
        expectedTotalLength?.let { total ->
            val remaining = total - buffer.size()
            if (remaining <= 0) {
                // 等待 END 包来完成
                return STATUS_SUCCESS
            }
            val toSend = if (remaining <= 0xFF) remaining else 0xFF
            return byteArrayOf(STATUS_MORE_DATA_PREFIX, toSend.toByte())
        }

        // 否则仅返回普通 ACK
        return STATUS_SUCCESS
    }

    private fun handleEnd(data: ByteArray): ByteArray {
        // END: 最后一个 chunk 也可能随 END 一起发送（data = [seq?, chunk...])
        if (!receiving) {
            Log.w(TAG, "END received but not in receiving state")
            return STATUS_BAD_PARAM
        }
        if (data.isNotEmpty()) {
            // 如果包含 seq：同 CONTINUE 一样
            val seq = data[0].toInt() and 0xFF
            val chunk = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)
            buffer.write(chunk)
            lastSeq = seq
            Log.d(TAG, "END got final chunk seq=$seq len=${chunk.size} total=${buffer.size()}")
        } else {
            Log.d(TAG, "END received with no final chunk")
        }

        // 验证（可选）：如果我们知道 expectedTotalLength，先比对大小
        expectedTotalLength?.let { total ->
            if (buffer.size() != total) {
                Log.w(TAG, "Received size != expected: got=${buffer.size()} expect=$total")
                // 仍然可以继续，但通常认为失败
                // resetState()
                // return STATUS_FAILED
            }
        }

        // 完整数据到达，调用处理器
        val receivedBytes = buffer.toByteArray()
        try {
            onComplete(receivedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "onComplete handler failed", e)
            resetState()
            return STATUS_FAILED
        }

        // 完成并复位
        resetState()

        // 可以返回一个带有成功确认与小量数据（例如指纹）的响应；这里仅返回 SUCCESS
        return STATUS_SUCCESS
    }

    private fun handleStatus(): ByteArray {
        // 返回已接收长度（4 字节 big-endian） + SW
        val received = buffer.size()
        val resp = ByteArray(4 + 2)
        resp[0] = ((received shr 24) and 0xFF).toByte()
        resp[1] = ((received shr 16) and 0xFF).toByte()
        resp[2] = ((received shr 8) and 0xFF).toByte()
        resp[3] = (received and 0xFF).toByte()
        resp[4] = STATUS_SUCCESS[0]
        resp[5] = STATUS_SUCCESS[1]
        Log.d(TAG, "STATUS requested -> received=$received")
        return resp
    }

    /**
     * 处理已经完整接收的数据
     * 这里示例把数据当作 UTF-8 JSON 展示；你可以改为解析 DER / 二进制公钥或其他格式。
     */
    private fun onComplete(data: ByteArray) {
        Log.i(TAG, "Complete data received, len=${data.size}")

        // 尝试当作 JSON 文本解析（如果是文本）
        val maybeText = try { String(data, Charset.forName("UTF-8")) } catch (e: Exception) { null }
        if (!maybeText.isNullOrEmpty() && (maybeText.trim().startsWith("{") || maybeText.trim().startsWith("["))) {
            Log.i(TAG, "Received JSON:\n$maybeText")
            // TODO: parse JSON, validate fingerprint, 校验签名，或保存到文件等
            return
        }

        // 否则，可能是二进制 DER 公钥或证书摘要
        // TODO: 保存到文件 / 解析公钥
        Log.i(TAG, "Received binary data (non-text). Save or parse as needed.")
    }

    /**
     * 清理状态
     */
    private fun resetState() {
        receiving = false
        try {
            buffer.reset()
        } catch (e: Exception) { /* ignore */ }
        expectedTotalLength = null
        lastSeq = -1
        handler.removeCallbacks(resetRunnable)
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "HCE deactivated, reason=$reason, resetting state")
        resetState()
    }
}