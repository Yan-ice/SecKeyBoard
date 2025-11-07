package com.example.seckeyboard


import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.seckeyboard.ui.theme.SecKeyboardTheme
import com.example.seckeyboard.utils.NoiseHelper
import com.example.seckeyboard.utils.SettingsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        enableEdgeToEdge()
        setContent {
            SecKeyboardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Welcome")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val intent = Intent(context, PulseActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Generate Vibration")
        }

        // ✅ 新增跳转按钮
        Button(onClick = {
            val intent = Intent(context, SelpadActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Selection Pad")
        }

        Button(onClick = {
            val intent = Intent(context, NumpadActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Digit Pad")
        }

        Button(onClick = {
            val intent = Intent(context, KeypadActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("EN Pad")
        }

        Button(onClick = {
            val intent = Intent(context, AutoSetActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Preference")
        }
        Button(onClick = {
            val intent = Intent(context, VibrationActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Vibration Discrimination")
        }

        Button(onClick = {
            val intent = Intent(context, NfcActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Complete Protocol Test")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SecKeyboardTheme {
        Greeting("Android")
    }
}
