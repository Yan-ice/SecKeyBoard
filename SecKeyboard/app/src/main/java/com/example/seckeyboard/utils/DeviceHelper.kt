package com.example.seckeyboard.utils


import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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

    fun playSineWave(frequency: Double = 1000.0, durationMs: Int = 1000, volume: Float = 1.0f) {
        val sampleRate = 44100
        val numSamples = durationMs * sampleRate / 1000
        val samples = DoubleArray(numSamples)
        val buffer = ShortArray(numSamples)

        // 生成正弦波
        for (i in samples.indices) {
            samples[i] = sin(2 * PI * i * frequency / sampleRate)
            buffer[i] = (samples[i] * Short.MAX_VALUE * volume).toInt().toShort()
        }

        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
    }
}