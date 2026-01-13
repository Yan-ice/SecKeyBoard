package com.example.seckeyboard.utils

import android.util.Log

object DeviceHelper {

    fun buildVibrationPattern(numbers: List<Int>, cycles: Int = 3): LongArray {

        val pattern = mutableListOf<Long>()

        val vibrateDuration = SettingsManager
            .getVibrationAmp(default = 50).toLong()
            .coerceAtLeast(1)

        val cycleDuration = SettingsManager
            .getVibrationInterval(default = 200).toLong()
            .coerceAtLeast(vibrateDuration + 10)

        pattern.add(cycleDuration)
        pattern.add(0)
        pattern.add(cycleDuration)
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