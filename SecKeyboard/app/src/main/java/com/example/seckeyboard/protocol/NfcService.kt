package com.example.seckeyboard.protocol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.seckeyboard.protocol.SharedState.certificate
import com.example.seckeyboard.utils.CertificateHelper
import com.example.seckeyboard.utils.EventBroadcastHelper.INFO_FINISH_EVENT
import com.example.seckeyboard.utils.NFCHelper
import java.security.PublicKey
import java.security.Signature

class NfcService : HostApduService() {

    companion object {
        const val TAG = "INFO_Service"

        // 超时限制
        private const val RECEIVE_TIMEOUT_MS = 30_000L
    }

    override fun onCreate() {
        super.onCreate()

        val channelId = "hce_channel"
        val channelName = "HCE Service"
        val channelDescription = "Notifications for HCE service"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = channelDescription
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, "hce_channel")
            .setContentTitle("NFC Service Active")
            .setContentText("HCE Service running in foreground")
            .build()
        startForeground(1, notification)
    }

    // 超时处理
    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable {
        Log.w(TAG, "InfoService receive timeout — resetting state")
        NFCHelper.resetState()
    }
    fun ByteArray.toHexString(): String {
        return joinToString(" ") { "%02X".format(it) }
    }
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) Log.i("NFC", "receive empty command.")
        Log.i("NFC", "receive command: "+ commandApdu?.toHexString())

        // 每次收到命令，重置超时定时器
        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, RECEIVE_TIMEOUT_MS)

        val response = NFCHelper.processCommandApdu(commandApdu) {
            swtype, param, data ->
            val ret: ByteArray =
                if(swtype == NFCHelper.INS_RECV_END){
                    when(param) {
                        NFCHelper.INS_RECV_TYPE_CERT -> onCertComplete(data)
                        NFCHelper.INS_RECV_TYPE_DH -> onDHComplete(data)
                        NFCHelper.INS_RECV_TYPE_DH_SIG -> onDHSigComplete(data)
                        else -> byteArrayOf(0)
                    }
                }else if(swtype == NFCHelper.INS_SEND_INIT){
                    when(param) {
                        NFCHelper.INS_SEND_TYPE_DH -> SharedState.clientDHkey!!
                        else -> byteArrayOf(0)
                    }
                }else{
                    byteArrayOf(0)
                }
            ret
        }
        return response
    }

    /**
     * 处理已经完整接收的数据
     * 这里示例把数据当作 UTF-8 JSON 展示；你可以改为解析 DER / 二进制公钥或其他格式。
     */
    private fun onCertComplete(data: ByteArray): ByteArray {
        Log.i(TAG, "Certificate received, len=${data.size}")

        SharedState.certificate = CertificateHelper.loadCertificateFromBytes(data)
        certificate?.let { CertificateHelper.printCertificateInfo(TAG, it) }
        return byteArrayOf(0)
    }
    /**
     * 处理已经完整接收的数据
     * 这里示例把数据当作 UTF-8 JSON 展示；你可以改为解析 DER / 二进制公钥或其他格式。
     */
    private fun onDHComplete(data: ByteArray): ByteArray {
        Log.i(TAG, "DH key received, len=${data.size}")
        SharedState.serverDHkey = data
        return byteArrayOf(0)
    }

    private fun onDHSigComplete(data: ByteArray): ByteArray {
        Log.i(TAG, "DH signature received, len=${data.size}")

        val signature = data
        try {
            val publicKey: PublicKey = SharedState.certificate?.publicKey!!
            val verif_sig = Signature.getInstance("SHA256withRSA")
            verif_sig.initVerify(publicKey)
            verif_sig.update(SharedState.serverDHkey)
            verif_sig.verify(signature)
            Log.d(TAG, "Signature verify success.")

            val intent = Intent(INFO_FINISH_EVENT)
            sendBroadcast(intent)

        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Signature verify failed.")
        }
        return byteArrayOf(0)
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "HCE deactivated, reason=$reason, resetting state")
        handler.removeCallbacks(resetRunnable)
        NFCHelper.resetState()
    }
}