package com.example.seckeyboard

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.em
import com.example.seckeyboard.protocol.SharedState
import com.example.seckeyboard.ui.theme.SecKeyboardTheme
import com.example.seckeyboard.utils.DeviceHelper
import com.example.seckeyboard.utils.NoiseHelper
import kotlin.collections.joinToString
import kotlin.jvm.java

class NumpadActivity : ComponentActivity() {
    private val keypad = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    // Compose 状态放这里
    private val inputSequence = mutableStateListOf<String>()
    private var timeText = " "
    private var isConfirmed by mutableStateOf(true)
    private var showColumnLines by mutableStateOf(false)
    private var startTime: Long = 0
    private var startTime_single: Long = 0
    private var numberA: Int = 0
    private var numberB: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTime = System.currentTimeMillis()
        setContent {
            SecKeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NumpadScreen(
                        inputSequence = inputSequence,
                        isConfirmed = isConfirmed,
                        showColumnLines = showColumnLines,
                        onDigitPressed = { digit -> handleDigitInput(digit) },
                        onStartPressed = {
                            handleStart()
                        },
                        onConfirmPressed = { handleConfirm() },
                        onColumnLineDismiss = { showColumnLines = false },
                        timeText = timeText
                    )
                }
            }
        }
    }

    private fun handleDigitInput(digit: String) {
        isConfirmed = false

        var foundRow = -1
        var foundCol = -1

        outer@ for (r in keypad.indices) {
            val row = keypad[r]
            for (c in row.indices) {
                if (row[c] == digit) {
                    foundRow = r
                    foundCol = c
                    break@outer
                }
            }
        }

        if (foundRow == -1 || foundCol == -1) {
            Log.w("Numpad", "输入的digit不在键盘中: $digit")
            return
        }

        val colCount = keypad[0].size
        val rowCount = keypad.size

        // 上移 numberA 位（水平移动）
        var newRow = (foundRow - numberA) % rowCount
        if (newRow < 0) newRow += rowCount

        // 左移 numberB 位（垂直移动）
        var newCol = (foundCol - numberB) % colCount
        if (newCol < 0) newCol += colCount

        val movedDigit = keypad[newRow][newCol]

        inputSequence.add(movedDigit)
        Log.d("Numpad", "输入数字 $digit 左移 $numberA 位，上移 $numberB 位后变为 $movedDigit")

        if(startTime_single > 0) {
            var interval = System.currentTimeMillis()-startTime_single;
            Log.d("sidechannelrec", "$digit, $interval, $numberA, $numberB")
            // extract log fron logcat, to analyze time side channel
        }

        if(inputSequence.size < 9) {
            handleStart()
        }else{
            handleConfirm()
        }
    }

    private fun handleStart() {
        if (isConfirmed) {
            // 清空输入，重置状态
            inputSequence.clear()
            startTime = System.currentTimeMillis()
            timeText = " "
            isConfirmed = false
        }

        startTime_single = System.currentTimeMillis()
        showColumnLines = true //  触发动画

        numberA = (0..2).random()
        numberB = (0..2).random()

        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return

            val pattern = DeviceHelper.buildVibrationPattern(listOf(numberA, numberB))
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            NoiseHelper.start(2000)
        } catch (e: Exception) {
            Log.e("Numpad", "振动失败", e)
        }
    }


    private fun handleConfirm() {
        val password = inputSequence.joinToString(separator = "")
        if (SharedState.phase == 0) {
            isConfirmed = true
            SharedState.password = password

            val endTime = System.currentTimeMillis()
            timeText = "Time usage: "+ (((endTime-startTime)/100).toFloat()/10)
            Toast.makeText(this@NumpadActivity, "Your input is $password", Toast.LENGTH_SHORT).show()
        } else {
            // 这里进行跳转，把密码通过 Intent 传递给 NfcActivity
            SharedState.password = password
            SharedState.phase = 2
            val intent = Intent(this, NfcActivity::class.java)
            startActivity(intent)
        }
    }
}


@Composable
fun NumpadScreen(
    inputSequence: List<String> = emptyList(),
    timeText: String,
    isConfirmed: Boolean = false,
    showColumnLines: Boolean = false,
    onDigitPressed: (String) -> Unit,
    onStartPressed: () -> Unit,
    onConfirmPressed: () -> Unit,
    onColumnLineDismiss: () -> Unit
) {
    val digitKeys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (showColumnLines) {
            ColumnLineOverlay(
                modifier = Modifier.fillMaxSize(),
                onDismiss = onColumnLineDismiss
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            val displayText = if (isConfirmed && timeText.isNotEmpty()) inputSequence.joinToString("")
                                else "*".repeat(inputSequence.size.coerceAtMost(6))

            Text(
                text = displayText,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            digitKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Button(
                            onClick = { onDigitPressed(key) },
                            modifier = Modifier
                                .padding(8.dp)
                                .size(80.dp)
                        ) {
                            Text(text = key, fontSize = 6.em)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {onDigitPressed("0")},
                    modifier = Modifier
                        .padding(8.dp)
                        .width(60.dp)
                ) {
                    Text("0")
                }
                Button(
                    onClick = onStartPressed,
                    modifier = Modifier
                        .padding(8.dp)
                        .width(120.dp)
                ) {
                    Text("Start")
                }

                Button(
                    onClick = onConfirmPressed,
                    modifier = Modifier
                        .padding(8.dp)
                        .width(120.dp)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
fun ColumnLineOverlay(modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    var verticalAlpha by remember { mutableStateOf(0f) }
    var horizontalAlpha by remember { mutableStateOf(0f) }
    val maxAlpha = 0.4f

    LaunchedEffect(Unit) {

//        animate(0f, 1f, animationSpec = tween(10)) { value, _ ->
//            verticalAlpha = value
//        }


        val cycleDuration = com.example.seckeyboard.utils.SettingsManager
            .getVibrationInterval(default = 200)
            .coerceAtLeast(50).toInt()

        animate(0f, 0f, animationSpec = tween(cycleDuration)) { value, _ ->
            verticalAlpha = value
        }

        animate(1f, 1f, animationSpec = tween(cycleDuration)) { value, _ ->
            verticalAlpha = value
        }
        animate(1f, 0f, animationSpec = tween(cycleDuration)) { value, _ ->
            verticalAlpha = value
        }
        animate(0f, 0f, animationSpec = tween(cycleDuration)) { value, _ ->
            verticalAlpha = value
        }

        animate(1f, 1f, animationSpec = tween(cycleDuration*1)) { value, _ ->
            horizontalAlpha = value
        }
        animate(1f, 0f, animationSpec = tween(cycleDuration)) { value, _ ->
            horizontalAlpha = value
        }

        onDismiss?.invoke()
    }

    Canvas(modifier = modifier) {
        val colCount = 6
        val rowCount = 10
        val colWidth = size.width / colCount
        val rowHeight = size.height / (rowCount + 1)

        for (i in 0 until colCount) {
            val x = (i + 0.5f) * colWidth
            drawLine(
                color = Color.Red.copy(alpha = verticalAlpha * maxAlpha),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 15.dp.toPx()
            )
        }

        for (i in 0 until rowCount) {
            val y = (i + 0.5f) * rowHeight
            drawLine(
                color = Color.Blue.copy(alpha = horizontalAlpha * maxAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 15.dp.toPx()
            )
        }
    }
}
