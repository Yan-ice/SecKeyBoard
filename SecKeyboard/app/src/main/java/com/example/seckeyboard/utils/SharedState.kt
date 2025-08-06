package com.example.seckeyboard.utils

object SharedState {
    @Volatile
    var currentStatus: String = "等待读卡器靠近..."

    @Volatile
    var screenActive: Boolean = false

    @Volatile
    var password: String? = null
}
