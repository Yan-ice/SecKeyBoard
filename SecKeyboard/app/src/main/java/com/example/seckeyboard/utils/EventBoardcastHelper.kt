package com.example.seckeyboard.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

object EventBroadcastHelper {

    const val INFO_FINISH_EVENT = "com.example.app.INFO_FINISH_EVENT"

    private var receiver: BroadcastReceiver? = null

    /**
     * 发送广播事件
     */
    fun sendEvent(context: Context, event: String) {
        val intent = Intent(event)
//      intent.putExtra("message", message)
        context.sendBroadcast(intent)
    }

    /**
     * 注册广播接收器
     * @param context Activity 或 Application context
     * @param callback 收到事件时回调
     */
    fun register(context: Context, callback: (String) -> Unit) {
        if (receiver != null) return // 避免重复注册

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_EVENT) {
                    val msg = intent.getStringExtra("message") ?: ""
                    callback(msg)
                }
            }
        }
        val filter = IntentFilter(ACTION_EVENT)
        context.registerReceiver(receiver, filter)
    }

    /**
     * 取消注册广播接收器
     */
    fun unregister(context: Context) {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }
}
