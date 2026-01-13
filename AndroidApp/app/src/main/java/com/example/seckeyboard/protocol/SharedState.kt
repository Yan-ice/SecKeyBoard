package com.example.seckeyboard.protocol

import java.security.cert.X509Certificate

object SharedState {

    @Volatile
    var phase: Int = 0

    @Volatile
    var currentStatus: String = "Wait for card reader..."

    @Volatile
    var screenActive: Boolean = false

    @Volatile
    var password: String? = null

    @Volatile
    var serverCert: X509Certificate? = null

    @Volatile
    var clientCert: X509Certificate? = null

    @Volatile
    var serverDHkey: ByteArray ? = null

    @Volatile
    var clientDHkey: ByteArray ? = null

    @Volatile
    var sessionKey: ByteArray ? = null
}