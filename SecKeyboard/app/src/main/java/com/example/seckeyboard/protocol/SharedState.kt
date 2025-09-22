package com.example.seckeyboard.protocol

import java.security.cert.X509Certificate

object SharedState {
    @Volatile
    var currentStatus: String = "等待读卡器靠近..."

    @Volatile
    var screenActive: Boolean = false

    @Volatile
    var password: String? = null

    @Volatile
    var certificate: X509Certificate? = null

    @Volatile
    var serverDHkey: ByteArray ? = null

}