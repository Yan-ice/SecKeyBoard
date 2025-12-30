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
                // 使用 Column 作为父容器来实现垂直排列
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
    onTestSettings: (Int, Int) -> Unit, // 传递当前滑动条的值
    onConfirmSettings: (Int) -> Unit
) {
    // 1. 状态管理
    // 存储滑动条的值。使用 remember 来确保值在 Recomposition 时保持不变。
    // 滑动条的值是 Float 类型，但我们将其范围限制在 0.0f 到 50.0f。
    var sliderValue by remember { mutableFloatStateOf(30f) }

    // 将 Float 值转换为 Int，用于显示和传递给回调函数
    val currentValueInt = sliderValue.toInt()

    // 2. 布局：使用 Column 垂直排列所有元素
    Column(
        modifier = Modifier
            .fillMaxWidth() // 填充整个宽度
            .padding(16.dp), // 外部边距
        horizontalAlignment = Alignment.CenterHorizontally // 水平居中对齐
    ) {
        Text("Vibration Amplitude Setting")
        Spacer(modifier = Modifier.height(32.dp)) // 滑动条和按钮之间的较大间距

        // --- 1. 滑动条和值显示区域 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically // 垂直居中对齐
        ) {
            // 文本框/标签：显示当前选中的值
            Text(
                text = "Value: $currentValueInt",
                modifier = Modifier.width(80.dp) // 给文本框一个固定宽度
            )

            Spacer(modifier = Modifier.width(8.dp)) // 间距

            // 滑动条
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                },
                // 设置滑动条的范围：0.0f 到 50.0f
                valueRange = 10f..50f,
                // 设置步长。例如，设置 50 个步长，意味着值会是 0, 1, 2, ..., 50
                steps = 3, // 50 个值 (0到50) 之间有 49 个步长
                modifier = Modifier.weight(1f) // 占据剩余空间
            )
        }

        Spacer(modifier = Modifier.height(24.dp)) // 滑动条和按钮之间的较大间距

        // --- 2. 按钮区域 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly // 按钮平均分散
        ) {
            // 按钮 1: 测试设置
            Button(
                onClick = { onTestSettings(currentValueInt, 5) },
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("Try")
            }

            // 按钮 2: 确认设置
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

        Spacer(modifier = Modifier.height(32.dp)) // 滑动条和按钮之间的较大间距
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
                Text("Vibration Interval Setting")
                Spacer(modifier = Modifier.height(32.dp)) // 滑动条和按钮之间的较大间距

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