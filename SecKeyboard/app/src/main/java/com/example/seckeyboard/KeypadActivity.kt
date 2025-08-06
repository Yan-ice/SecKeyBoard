package com.example.seckeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.seckeyboard.ui.theme.SecKeyboardTheme
import com.example.seckeyboard.utils.DeviceHelper.buildVibrationPattern
import kotlin.math.cos
import kotlin.math.sin

class KeypadActivity : ComponentActivity() {
    private var numberA: Int = 0
    private var numberB: Int = 0
    private var inputSequence = mutableListOf<String>()
    private var isConfirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        renderUI()
    }

    private fun renderUI() {
        setContent {
            SecKeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KeypadScreen(
                        inputSequence = inputSequence,
                        isConfirmed = isConfirmed,
                        onDigitPressed = { digit -> handleDigitInput(digit) },
                        onStartPressed = { handleStart() },
                        onConfirmPressed = { handleConfirm() }
                    )
                }
            }
        }
    }

    val keypad = listOf(
        listOf("-", "=", ";", ".", ",", "[", "]", "/", "\\", "'"),
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "En"),
        listOf("Z", "X", "C", "V", "Sp", "Sp", "B", "N", "M", " ")
    )

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
            Log.w("Keypad", "输入的digit不在键盘中: $digit")
            return
        }

        val colCount = keypad[0].size
        val rowCount = keypad.size

        val newRow = (foundRow - numberA).mod(rowCount)

        // 判断是否在左半区或右半区
        val subRangeStart: Int
        val subRangeEnd: Int

        if (foundCol < 5) {
            // 左半区：index 0~4
            subRangeStart = 0
            subRangeEnd = 4
        } else {
            // 右半区：index 5~9
            subRangeStart = 5
            subRangeEnd = 9
        }

        val subLength = subRangeEnd - subRangeStart + 1
        val localIndex = foundCol - subRangeStart

        // 局部左移 numberB 次
        val newLocalIndex = (localIndex - numberB).mod(subLength)
        val newCol = subRangeStart + newLocalIndex


        val movedDigit = keypad[newRow][newCol]
        inputSequence.add(movedDigit)

        Log.d("Keypad", "输入数字 $digit 左移 $numberA 位，上移 $numberB 位后变为 $movedDigit")

        renderUI()
    }

    private fun handleStart() {
        if (isConfirmed) {
            inputSequence.clear()
            isConfirmed = false
        }

        numberA = (0..4).random()
        numberB = (0..4).random()

        Log.d("Keypad", "开始按钮点击，生成的数字为 $numberA 和 $numberB")

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return

        val pattern = buildVibrationPattern(listOf(numberA, numberB))
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))

        renderUI()
    }

    private fun handleConfirm() {
        isConfirmed = true
        Log.d("Keypad", "确认按钮点击，输入序列为：$inputSequence")
        renderUI()
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun KeypadScreen(
    inputSequence: List<String>,
    isConfirmed: Boolean,
    onDigitPressed: (String) -> Unit,
    onStartPressed: () -> Unit,
    onConfirmPressed: () -> Unit
) {
    val digitKeys = listOf(
        listOf("-", "=", ";", ".", ",", "[", "]", "/", "\\", "'"),
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "En"),
        listOf("Z", "X", "C", "V", "Sp", "Sp", "B", "N", "M", " ")
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val centerX = maxWidth / 2
        val centerY = maxHeight /5*7 // 顶部三分之一高度处作为圆心
        var radius = 450
        digitKeys.forEachIndexed { rowIndex, row ->

            val buttonSize = 48 - rowIndex * 4  // 内圈按钮更小
            radius -= buttonSize + 5  // 半径递减
            row.forEachIndexed { i, _ ->
                val key = row[row.size - 1 - i]  // 逆序取key

                val angleDeg = if (i < 5) {
                    40f + (80f - 40f) * (i.toFloat() / 4f)
                } else {
                    100f + (140f - 100f) * ((i - 5).toFloat() / 4f)
                }
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val x = radius * cos(angleRad)
                val y = radius * sin(angleRad)

                Box(
                    modifier = Modifier
                        .absoluteOffset(
                            x = centerX + x.dp - (buttonSize / 2).dp,
                            y = centerY - y.dp - (buttonSize / 2).dp
                        )
                        .size(buttonSize.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { onDigitPressed(key) },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = key, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }


        }


        // 控制区：底部中央
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isConfirmed) inputSequence.joinToString("") else "*".repeat(inputSequence.size),
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = onStartPressed,
                    modifier = Modifier
                        .padding(8.dp)
                        .width(80.dp)
                        .height(35.dp)
                ) {
                    Text("开始")
                }

                Button(
                    onClick = onConfirmPressed,
                    modifier = Modifier
                        .padding(8.dp)
                        .width(80.dp)
                        .height(35.dp)
                ) {
                    Text("确认")
                }
            }

        }
    }
}
