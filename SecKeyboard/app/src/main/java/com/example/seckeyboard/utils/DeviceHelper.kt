package com.example.seckeyboard.utils

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

import kotlin.math.PI
import kotlin.math.sin

object DeviceHelper {

    fun buildVibrationPattern(numbers: List<Int>, cycles: Int = 3): LongArray {

        val pattern = mutableListOf<Long>()

        val vibrateDuration = 50L

        val cycleDuration = SettingsManager
            .getVibrationInterval(default = 200).toLong()
            .coerceAtLeast(vibrateDuration + 10)

        pattern.add(100)
        pattern.add(0)
        pattern.add(100)
        for (num in numbers) {
            for (i in 0 until cycles) {
                // vib
                val vibrateTime = if (i < num) vibrateDuration else 0L
                pattern.add(vibrateTime)
                // delay
                pattern.add(cycleDuration-vibrateTime)
            }
        }
        pattern.add(0)
        Log.d("pattern", pattern.joinToString(", "))
        return pattern.toLongArray()
    }

}