package com.example.seckeyboard

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.seckeyboard.ui.theme.SecKeyboardTheme
import com.example.seckeyboard.utils.SettingsManager
import kotlin.random.Random

class AutoSetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecKeyboardTheme {
                VibrationAutoSetScreen()
            }
        }
    }
}

@Composable
fun VibrationAutoSetScreen() {
    var vibrationInterval by remember { mutableIntStateOf(300) }
    var currentVibrationCount by remember { mutableIntStateOf(-1) }
    var succeedRecord = 300
    var succeedTime = 0
    val stats = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }
    val context = LocalContext.current

    // Initialize stats for all possible vibration counts (0-10)
    LaunchedEffect(Unit) {
        (1..3).forEach { count ->
            stats[count] = Pair(0, 0)
        }

        // 读取设置的 vibrationInterval
        vibrationInterval = SettingsManager.getVibrationInterval()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Vibration interval input
                OutlinedTextField(
                    value = vibrationInterval.toString(),
                    onValueChange = { vibrationInterval = it.toIntOrNull() ?: 300 },
                    label = { Text("Vibration interval (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Start button
                Button(
                    onClick = {
                        val count = Random.nextInt(if (succeedTime == 0) 1 else 2, 6) // Random between 1 and 5
                        currentVibrationCount = count
                        vibrate(context, count, vibrationInterval.toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Vibration Test")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Answer buttons
                Text("How many vibrations did you feel?", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(8.dp))

                // Create buttons in two rows for better layout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // First row (0-5)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        for (i in 1..5) {
                            AutoTestAnswerButton(i, currentVibrationCount) { selectedCount ->
                                if (currentVibrationCount >= 0) {
                                    if (selectedCount == currentVibrationCount) {
                                        succeedTime++
                                        if (succeedTime >= 3) {
                                            if(succeedRecord < vibrationInterval) {
                                                //end setting
                                                if (vibrationInterval > 0) {
                                                    SettingsManager.saveVibrationInterval(vibrationInterval+20)
                                                }

                                                (context as? ComponentActivity)?.finish()
                                                return@AutoTestAnswerButton
                                            }
                                            vibrationInterval = vibrationInterval - 30
                                            succeedTime = 1
                                            succeedRecord = vibrationInterval
                                        }
                                    } else {
                                        succeedTime = 0
                                        vibrationInterval = vibrationInterval + 10
                                    }
                                    currentVibrationCount = -1 // Reset for next test
                                }
                            }
                        }
                    }

                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (vibrationInterval > 0) {
                            SettingsManager.saveVibrationInterval(vibrationInterval)
                        }

                        (context as? ComponentActivity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Leave")
                }

            }
        }
    )
}

@Composable
fun AutoTestAnswerButton(
    count: Int,
    currentVibrationCount: Int,
    onClick: (Int) -> Unit
) {
//    Button(
//        onClick = { onClick(count) },
//        enabled = currentVibrationCount >= 0,
//        colors = ButtonDefaults.buttonColors(
//            containerColor = if (currentVibrationCount == count && currentVibrationCount >= 0) {
//                MaterialTheme.colorScheme.primaryContainer
//            } else {
//                MaterialTheme.colorScheme.primary
//            }
//        )
//    ) {
//        Text(count.toString())
//    }
    Button(
        onClick = { onClick(count) },
        enabled = currentVibrationCount >= 0
    ) {
        Text(count.toString())
    }
}

private fun vibrate(context: Context, count: Int, interval: Long) {
    if (count == 0) return

    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (!vibrator.hasVibrator()) return

    // Calculate the silent period (must be >= 0)
    val silentPeriod = (interval - 40).coerceAtLeast(0)

    // Create pattern: [vibrate 40ms, silent (interval-40)ms] repeated count times
    val pattern = LongArray(count * 2) { i ->
        when {
            // First part of each pair - vibration time (40ms)
            i % 2 == 1 -> 40
            // Second part of each pair - silent time (interval-40ms)
            else -> silentPeriod
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = VibrationEffect.createWaveform(pattern, -1)
        vibrator.vibrate(effect)
    } else {
        vibrator.vibrate(pattern, -1)
    }
}