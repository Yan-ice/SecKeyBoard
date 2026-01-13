package com.example.seckeyboard.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

object EventBroadcastHelper {

    const val INFO_FINISH_EVENT = "com.example.app.INFO_FINISH_EVENT"
    const val MSG_FINISH_EVENT = "com.example.app.MSG_FINISH_EVENT"

    private var receiver: BroadcastReceiver? = null


    fun sendEvent(context: Context, event: String) {
        val intent = Intent(event)
        context.sendBroadcast(intent)
    }

    fun register(context: Context, callback: (String) -> Unit) {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == INFO_FINISH_EVENT) {
                    val msg = intent.getStringExtra("message") ?: ""
                    callback(msg)
                }
            }
        }
        val filter = IntentFilter(INFO_FINISH_EVENT)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregister(context: Context) {
        receiver?.let {
            context.unregisterReceiver(it)
            receiver = null
        }
    }
}
