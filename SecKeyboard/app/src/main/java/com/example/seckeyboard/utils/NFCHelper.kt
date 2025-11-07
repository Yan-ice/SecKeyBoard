package com.example.seckeyboard.utils

import android.util.Log
import com.example.seckeyboard.protocol.NfcService.Companion.TAG

import com.example.seckeyboard.protocol.SharedState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object NFCHelper {

    // 包大小限制
    private const val MAX_TOTAL_SIZE = 10 * 1024 // 安全上限制：最大 10 KB（根据需要调整）

    // INS定义
    private const val SELECT_AID: Byte = 0xA4.toByte()

    private const val INS_RECV_INIT: Byte = 0xD0.toByte()
    private const val INS_RECV_CONTINUE: Byte = 0xD1.toByte()
    const val INS_RECV_END: Byte = 0xD2.toByte()

    const val INS_SEND_INIT: Byte = 0xB0.toByte()
    private const val INS_SEND_CONTINUE: Byte = 0xB1.toByte()
    const val INS_BYE: Byte = 0xB2.toByte()

    const val INS_RECV_TYPE_CERT: Byte = 0x01 //param
    const val INS_RECV_TYPE_DH: Byte = 0x02 //param
    const val INS_RECV_TYPE_DH_SIG: Byte = 0x03 //param


    const val INS_SEND_TYPE_CERT: Byte = 0x01 //param
    const val INS_SEND_TYPE_DH: Byte = 0x02 //param
    const val INS_SEND_TYPE_DH_SIG: Byte = 0x03 //param
    const val INS_SEND_TYPE_PWD: Byte = 0x04 //param

    private const val INS_STATUS: Byte = 0x40

    // ISO-like 状态字（SW1 SW2）
    val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
    val STATUS_NOT_PREPARED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

    private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
    private val STATUS_BAD_PARAM = byteArrayOf(0x6A.toByte(), 0x80.toByte())
    private val STATUS_MORE_DATA_PREFIX = 0x61.toByte() // 0x61 XX


    // 用于重组接收数据
    @Volatile
    private var receiving = false

    private val buffer = ByteArrayOutputStream()
    private var expectedTotalLength: Int? = null // 可选，INIT 包里发送
    private var lastSeq: Int = -1

    // 用于重组发送数据
    @Volatile
    private var sending = false

    private var buffer_o = ByteArrayInputStream(byteArrayOf(0x00.toByte()))
    private var lastSeq_o: Int = -1

    //处理NFC包请求，如果有一个完整的包被处理，callback将会被调用。
    // callback格式： ins, param, data，分别对应apdu的第二字节、第三字节(p1)、extend部分
    //请将NFC包直接传给commandApdu，并将该函数返回值直接返回。
    fun processCommandApdu(commandApdu: ByteArray?, callback: (Byte, Byte, ByteArray) -> ByteArray): ByteArray {
        if (commandApdu == null) {
            return STATUS_FAILED
        }
        // 解析 INS（假设短 APDU: [CLA, INS, P1, P2, Lc, ...data...])
        if (commandApdu.size < 5) {
            return STATUS_BAD_PARAM
        }

        val ins = commandApdu[1]
        val param = commandApdu[2]
        val lc = commandApdu[4].toInt() and 0xFF
        if (commandApdu.size < 5 + lc) {
            return STATUS_BAD_PARAM
        }
        val data = if (lc > 0) commandApdu.copyOfRange(5, 5 + lc) else ByteArray(0)

        return try {
            when (ins) {
                SELECT_AID -> selectAid(data)

                INS_RECV_INIT -> recvInit(data)
                INS_RECV_CONTINUE -> recvContinue(data)
                INS_RECV_END ->
                    recvEnd(data, ins, param, callback)

                INS_SEND_INIT ->
                    sendInit(ins, param, callback)
                INS_SEND_CONTINUE -> sendContinue()

                INS_BYE -> handleBye(callback)
                INS_STATUS -> handleStatus()
                else -> {
                    STATUS_BAD_PARAM
                }
            }
        } catch (e: Exception) {
            resetState()
            STATUS_FAILED
        }
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

    private fun handleBye(callback: (Byte, Byte, ByteArray) -> ByteArray): ByteArray {
        SharedState.currentStatus = "Session close. You can exit APP now"

        callback(INS_BYE, INS_BYE, byteArrayOf(0))

        return STATUS_SUCCESS
    }

    private fun selectAid(data: ByteArray): ByteArray {
        SharedState.currentStatus = "Connection success"
        Log.d("NFC", "SELECT AID: ${data.joinToString(" ") { "%02X".format(it) }}")
        // TODO: specific session id
        return byteArrayOf(0x23.toByte(), 0x23.toByte(), 0x23.toByte()) + STATUS_SUCCESS
    }

    private fun sendInit(type: Byte, param: Byte, callback: (Byte, Byte, ByteArray) -> ByteArray): ByteArray {
        if(sending) {
            Log.d("NFC", "WARN: new send task enabled before the last one finished.")
        }
        sending = true
        val data = callback(type, param, byteArrayOf(0))
        buffer_o = ByteArrayInputStream(data)
        return sendContinue()
    }

    private fun sendContinue(): ByteArray {
        val tmp_buffer = ByteArray(240)
        var bytesRead: Int = buffer_o.read(tmp_buffer)
        if(bytesRead == -1) {
            sending = false
            bytesRead = 0
        }
        return byteArrayOf(bytesRead.toByte()) + tmp_buffer.copyOf(bytesRead) + STATUS_SUCCESS
    }


    private fun recvInit(data: ByteArray): ByteArray {

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

            val txt = try { String(data, Charset.forName("UTF-8")) } catch (e: Exception) { null }
            if (!txt.isNullOrEmpty()) {
                Log.i(TAG, "INIT metadata: $txt")

            }
        }

        receiving = true
        lastSeq = -1
        buffer.reset()


        return STATUS_SUCCESS
    }


    private fun recvContinue(data: ByteArray): ByteArray {
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

        if (lastSeq >= 0 && seq != (lastSeq + 1 and 0xFF)) {
            return STATUS_BAD_PARAM
        }

        if (buffer.size() + chunk.size > MAX_TOTAL_SIZE) {
            Log.e(TAG, "Exceeded max receive size")
            resetState()
            return STATUS_FAILED
        }

        buffer.write(chunk)
        lastSeq = seq
        Log.d(TAG, "Received chunk seq=$seq len=${chunk.size} total=${buffer.size()}")

        expectedTotalLength?.let { total ->
            val remaining = total - buffer.size()
            if (remaining <= 0) {

                return STATUS_SUCCESS
            }
            val toSend = if (remaining <= 0xFF) remaining else 0xFF
            return byteArrayOf(STATUS_MORE_DATA_PREFIX, toSend.toByte())
        }


        return STATUS_SUCCESS
    }

    private fun recvEnd(data: ByteArray, type: Byte, param: Byte, callback: (Byte, Byte, ByteArray) -> ByteArray): ByteArray {

        if (!receiving) {
            return STATUS_BAD_PARAM
        }
        if (data.isNotEmpty()) {

            val seq = data[0].toInt() and 0xFF
            val chunk = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)
            buffer.write(chunk)
            lastSeq = seq
        }

        expectedTotalLength?.let { total ->
            if (buffer.size() != total) {

                resetState()
                return STATUS_FAILED
            }
        }

        val receivedBytes = buffer.toByteArray()
        try {
            callback(type, param, receivedBytes);
        } catch (e: Exception) {
            Log.e(TAG, "onComplete handler failed", e)
            resetState()
            return STATUS_FAILED
        }

        resetState()

        return STATUS_SUCCESS
    }

    fun resetState() {
        receiving = false
        try {
            buffer.reset()
        } catch (e: Exception) { /* ignore */ }
        expectedTotalLength = null
        lastSeq = -1
    }

}
