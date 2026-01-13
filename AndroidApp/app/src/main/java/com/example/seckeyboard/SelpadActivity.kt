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
import com.example.seckeyboard.utils.SettingsManager
import kotlin.jvm.java

class SelpadActivity : ComponentActivity() {
    private val keypad = listOf(
        listOf("A", "B"),
        listOf("C", "D"),
    )

    // Compose 状态放这里
    private val inputSequence = mutableStateListOf<String>()
    private var timeText = " "
    private var isConfirmed by mutableStateOf(true)
    private var showColumnLines by mutableStateOf(false)
    private var startTime: Long = 0
    private var numberA: Int = 0
    private var numberB: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startTime = System.currentTimeMillis()
        setContent {
            SecKeyboardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SelpadScreen(
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
            return
        }

        val colCount = keypad[0].size
        val rowCount = keypad.size


        var newRow = (foundRow - numberA) % rowCount
        if (newRow < 0) newRow += rowCount


        var newCol = (foundCol - numberB) % colCount
        if (newCol < 0) newCol += colCount

        val movedDigit = keypad[newRow][newCol]

        inputSequence.add(movedDigit)

        if(inputSequence.size < 4) {
            handleStart()
        }else{
            handleConfirm()
        }
    }

    private fun handleStart() {
        if (isConfirmed) {

            inputSequence.clear()
            startTime = System.currentTimeMillis()
            timeText = " "
            isConfirmed = false
        }

        showColumnLines = true

        numberA = (0..1).random()
        numberB = (0..1).random()

        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return

            val pattern = DeviceHelper.buildVibrationPattern(listOf(numberA, numberB), 2)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            Log.e("Numpad", "Vib Failed", e)
        }
    }


    private fun handleConfirm() {
        isConfirmed = true
        val password = inputSequence.joinToString(separator = "")
        if(SharedState.phase == 1) {

            SharedState.password = password
            SharedState.phase = 2
            val intent = Intent(this, NfcActivity::class.java)
            startActivity(intent)
        }else if (SharedState.phase == 0) {
            SharedState.password = password

            val endTime = System.currentTimeMillis()
            timeText = "Time usage: "+ (((endTime-startTime)/100).toFloat()/10)
            Toast.makeText(this@SelpadActivity, "Your input is $password", Toast.LENGTH_SHORT).show()
        }
    }
}


@Composable
fun SelpadScreen(
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
        listOf("A", "B"),
        listOf("C", "D"),
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (showColumnLines) {
            SelColumnLineOverlay(
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
                                .padding(20.dp)
                                .size(100.dp)
                        ) {
                            Text(text = key, fontSize = 8.em)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
fun SelColumnLineOverlay(modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    var verticalAlpha by remember { mutableStateOf(0f) }
    var horizontalAlpha by remember { mutableStateOf(0f) }
    val maxAlpha = 0.4f

    LaunchedEffect(Unit) {

        val cycleDuration = SettingsManager
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
