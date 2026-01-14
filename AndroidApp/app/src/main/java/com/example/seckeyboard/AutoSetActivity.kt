package com.example.seckeyboard

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
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
import com.example.seckeyboard.utils.DeviceHelper.buildVibrationPattern
import com.example.seckeyboard.utils.SettingsManager
import kotlin.random.Random

class AutoSetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isAutoSetScreenEnabled by remember { mutableStateOf(false) }

            SecKeyboardTheme {
                Column {
                    if (!isAutoSetScreenEnabled) {
                        VibrationBScreen(
                            onTestSettings = { value, amp -> onTestVibAmp(value, amp) },
                            onConfirmSettings = { value ->
                                    SettingsManager.saveVibrationAmp(value)
                                    isAutoSetScreenEnabled = true
                            }
                        )
                    }
                    if (isAutoSetScreenEnabled) {
                        VibrationAutoSetScreen()
                    }
                }

            }
        }
    }

    fun onTestVibAmp(amp: Int, repeat: Int) {
        SettingsManager.saveVibrationAmp(amp)
        SettingsManager.saveVibrationInterval(250)
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            val pattern = buildVibrationPattern(List(repeat) { 1 },2)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            Log.e("Numpad", "Vib Failed", e)
        }
    }
}

@Composable
fun VibrationBScreen(
    onTestSettings: (Int, Int) -> Unit,
    onConfirmSettings: (Int) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(30f) }

    val currentValueInt = sliderValue.toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth() 
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Vibration Amplitude Setting")
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically 
        ) {

            Text(
                text = "Value: $currentValueInt",
                modifier = Modifier.width(80.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                },
                valueRange = 10f..50f,
                steps = 3, 
                modifier = Modifier.weight(1f) 
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { onTestSettings(currentValueInt, 5) },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("Try")
            }

            Button(
                onClick = { onConfirmSettings(currentValueInt) },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("Confirm")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onTestSettings(currentValueInt, 120) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("(Test Only) Keep Vibrating")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Choose the smallest possible vibration amplitude that you can perceive.")

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
                Text("Vibration Interval Setting")
                Spacer(modifier = Modifier.height(32.dp))

                // Vibration interval input
                OutlinedTextField(
                    value = vibrationInterval.toString(),
                    onValueChange = { vibrationInterval = it.toIntOrNull() ?: 0 },
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
                                            if(succeedRecord < vibrationInterval-11) {
                                                //end setting
                                                if (vibrationInterval > 0) {
                                                    SettingsManager.saveVibrationInterval(vibrationInterval+20)
                                                }

                                                (context as? ComponentActivity)?.finish()
                                                return@AutoTestAnswerButton
                                            }
                                            vibrationInterval = vibrationInterval - 20
                                            succeedTime = 1
                                            succeedRecord = vibrationInterval
                                        }
                                    } else {
                                        succeedTime = 0
                                        vibrationInterval = vibrationInterval + 10
                                    }
                                    currentVibrationCount = -1 // Reset for next test
                                }
                                val count = Random.nextInt(if (succeedTime == 0) 1 else 2, 6) // Random between 1 and 5
                                currentVibrationCount = count
                                vibrate(context, count, vibrationInterval.toLong())
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

    val vibtime: Long = SettingsManager.getVibrationAmp().toLong()
    // Calculate the silent period (must be >= 0)
    val silentPeriod = (interval - vibtime).coerceAtLeast(0)

    // Create pattern: [vibrate 40ms, silent (interval-40)ms] repeated count times
    val pattern = LongArray(count * 2) { i ->
        when {
            // First part of each pair - vibration time (40ms)
            i % 2 == 1 -> vibtime
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