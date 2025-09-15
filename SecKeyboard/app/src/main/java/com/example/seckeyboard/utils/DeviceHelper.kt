package com.example.seckeyboard.utils

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

import kotlin.math.PI
import kotlin.math.sin

object DeviceHelper {

    fun buildVibrationPattern(numbers: List<Int>): LongArray {

        val pattern = mutableListOf<Long>()
        val cycles = 4

        val vibrateDuration = 40L

        val cycleDuration = SettingsManager
            .getVibrationInterval(default = 200L)
            .coerceAtLeast(vibrateDuration + 10)

        pattern.add(100)
        for (num in numbers) {
            for (i in 0 until cycles) {
                // 振动时间，前 num 个周期振动，其余静默
                val vibrateTime = if (i < num) vibrateDuration else 0L
                pattern.add(vibrateTime)
                // 静默时间，第一次第一个周期静默0，其他周期静默为周期减振动时长
                pattern.add(cycleDuration-vibrateTime)
            }
        }
        pattern.add(0)
        Log.d("pattern", pattern.joinToString(", "))
        return pattern.toLongArray()
    }

}