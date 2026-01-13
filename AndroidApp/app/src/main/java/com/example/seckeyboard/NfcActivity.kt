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
import com.example.seckeyboard.utils.CryptoHelper.toHexString
import com.example.seckeyboard.utils.EventBroadcastHelper.INFO_FINISH_EVENT
import com.example.seckeyboard.utils.EventBroadcastHelper.MSG_FINISH_EVENT

class NfcActivity : ComponentActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == INFO_FINISH_EVENT) {
                Toast.makeText(this@NfcActivity, "Signature verified.", Toast.LENGTH_SHORT).show()
                SharedState.phase = 2
                val intent = Intent(context, NumpadActivity::class.java)
                context?.startActivity(intent)
            }
            if (intent?.action == MSG_FINISH_EVENT) {
                Toast.makeText(this@NfcActivity, "Data transfer success.", Toast.LENGTH_SHORT).show()
                SharedState.phase = 0
                val intent = Intent(context, MainActivity::class.java)
                context?.startActivity(intent)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Please activate NFC.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } else{
            Log.d("Nfc", "NFC available")
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        //start phase
        if (SharedState.phase == 0) {
            SharedState.phase = 1
        }
        setContent {
            SecKeyboardTheme {
                var displayText by remember { mutableStateOf(SharedState.currentStatus) }

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
                            text = if (SharedState.phase == 1) "Tap to open keyboard" else "Tap to send your input",
                            style = MaterialTheme.typography.headlineSmall
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

            SharedState.clientDHkey = dhClient.public.encoded
            SharedState.sessionKey = CryptoHelper.ECDHcal(SharedState.serverDHkey!!, dhClient.private)
            Log.d("session", "session key is:"+ SharedState.sessionKey?.let { toHexString(it) }) //TODO

            val filter = IntentFilter(MSG_FINISH_EVENT)
            registerReceiver(eventReceiver, filter)
        }

//        val serviceIntent = Intent(this, NfcService::class.java)
//        ContextCompat.startForegroundService(this, serviceIntent)
        startService(Intent(this, NfcService::class.java))
    }
    override fun onStop() {
        super.onStop()
        unregisterReceiver(eventReceiver)
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
//            val component = ComponentName(this, NfcService::class.java)
//            CardEmulation.getInstance(nfcAdapter).setPreferredService(this, component)
//        }

        SharedState.screenActive = true
        SharedState.currentStatus = "Hold the card reader close..."
    }

    override fun onPause() {
        super.onPause()

        //nfcAdapter.disableForegroundDispatch(this)

        SharedState.screenActive = false
        SharedState.currentStatus = "[Service is paused]"
    }
}
