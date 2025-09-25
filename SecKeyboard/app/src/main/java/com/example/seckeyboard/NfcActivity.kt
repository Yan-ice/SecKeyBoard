package com.example.seckeyboard

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.seckeyboard.protocol.NfcService
import com.example.seckeyboard.ui.theme.SecKeyboardTheme
import com.example.seckeyboard.protocol.SharedState
import com.example.seckeyboard.utils.CryptoHelper
import com.example.seckeyboard.utils.EventBroadcastHelper.INFO_FINISH_EVENT

class NfcActivity : ComponentActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == INFO_FINISH_EVENT) {
//                val msg = intent.getStringExtra("message")
                Toast.makeText(this@NfcActivity, "Signature verified.", Toast.LENGTH_SHORT).show()

                val intent = Intent(context, NumpadActivity::class.java)
                context?.startActivity(intent)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查 NFC 是否启用
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "请先启用NFC功能", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } else{
            Log.d("Nfc", "NFC available")
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        setContent {
            SecKeyboardTheme {
                var displayText by remember { mutableStateOf(SharedState.currentStatus) }

                // 自动更新状态信息
                LaunchedEffect(Unit) {
                    while (true) {
                        displayText = SharedState.currentStatus
                        kotlinx.coroutines.delay(500)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (SharedState.phase == 1) "靠近NFC打开键盘" else "靠近NFC发送输入",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(displayText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()

        if (SharedState.phase == 1) {
            val filter = IntentFilter(INFO_FINISH_EVENT)
            registerReceiver(eventReceiver, filter)
        }
        if(SharedState.phase == 2) {
            val dhClient = CryptoHelper.ECDHgen()
            Log.d("session", "client DH key is:"+dhClient.public.encoded) //TODO

            SharedState.clientDHkey = dhClient.public.encoded
            SharedState.sessionKey = CryptoHelper.ECDHcal(SharedState.serverDHkey!!, dhClient.private)
            Log.d("session", "session key is:"+Base64.encodeToString(SharedState.sessionKey, Base64.NO_WRAP)) //TODO
        }

        val serviceIntent = Intent(this, NfcService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

    }
    override fun onStop() {
        super.onStop()
        if (SharedState.phase == 1) {
            unregisterReceiver(eventReceiver)
        }
    }

    override fun onResume() {
        super.onResume()

//        if (nfcAdapter.isEnabled) {
//            Log.i("Nfc", "re-enabling NFC")
//            val pendingIntent = PendingIntent.getActivity(
//                this, 0,
//                Intent(this, this.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
//                PendingIntent.FLAG_MUTABLE
//            )
//            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null)
//
//            // 尝试重新设为首选服务（仅系统/厂商签名可用）
//            val component = ComponentName(this, NfcService::class.java)
//            CardEmulation.getInstance(nfcAdapter).setPreferredService(this, component)
//        }

        SharedState.screenActive = true
        SharedState.currentStatus = "等待读卡器靠近..."
    }

    override fun onPause() {
        super.onPause()

        //nfcAdapter.disableForegroundDispatch(this)

        SharedState.screenActive = false
        SharedState.currentStatus = "界面不在前台，暂停服务"
    }
}
