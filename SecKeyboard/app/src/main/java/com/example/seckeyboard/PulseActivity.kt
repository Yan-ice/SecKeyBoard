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
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.example.seckeyboard.utils.CryptoHelper.toHexString
import com.example.seckeyboard.utils.EventBroadcastHelper.INFO_FINISH_EVENT
import com.example.seckeyboard.utils.EventBroadcastHelper.MSG_FINISH_EVENT
import com.example.seckeyboard.utils.NoiseHelper
import kotlinx.coroutines.delay

class PulseActivity : ComponentActivity() {

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecKeyboardTheme {
                var displayText by remember { mutableStateOf(SharedState.currentStatus) }

                LaunchedEffect(Unit) {
                    while (true) {
                        displayText = SharedState.currentStatus
                        delay(500)
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
                        Text("When you click the button below, your device will generate evenly spaced vibration pulses within 5 seconds.")
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { vibrate() }) {
                            Text("Start Vibration")
                        }
                    }
                }
            }
        }
    }
    fun vibrate() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return

            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(450,50,450,50,450,50,450,50,450,50,450,50,450,50,450,50,450,50,450,50)
                , -1))
            NoiseHelper.start(2000)
        } catch (e: Exception) {
            Log.e("Numpad", "Vib Failed", e)
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()

    }
    override fun onStop() {
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}
