package com.example.seckeyboard

import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.provider.Settings
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
import com.example.seckeyboard.ui.theme.SecKeyboardTheme
import com.example.seckeyboard.protocol.SubmitService
import com.example.seckeyboard.protocol.SharedState

class NfcActivity : ComponentActivity() {
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        password = intent.getStringExtra("password")

        // 检查 NFC 是否启用
        val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "该设备不支持NFC", Toast.LENGTH_LONG).show()
            finish()
            return
        } else if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "请先启用NFC功能", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } else{
            Log.d("Nfc", "NFC available")
        }

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
                        Text("靠近NFC发送密钥", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(displayText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SharedState.screenActive = true
        SharedState.currentStatus = "等待读卡器靠近..."
        SharedState.password = password

        val component = ComponentName(this, SubmitService::class.java)
        val cardEmulation = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this))
        cardEmulation.setPreferredService(this, component)
    }

    override fun onPause() {
        super.onPause()
        SharedState.screenActive = false
        SharedState.currentStatus = "界面不在前台，暂停服务"
    }
}
