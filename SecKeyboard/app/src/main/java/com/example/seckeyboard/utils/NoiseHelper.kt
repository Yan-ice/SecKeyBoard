package com.example.seckeyboard.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random

object NoiseHelper {
    private const val sampleRate: Int = 44100
    private const val channelCount: Int = 1 // 1 = mono, 2 = stereo
    private const val volume: Float = 0.2f  // 0.0 .. 1.0
    private const val bufferMs: Int = 200

    fun start(durationMs: Long) {
        val channelConfig = if (channelCount == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bytesPerSample = 2 // 16-bit
        val frameSize = bytesPerSample * channelCount
        val targetBufSamples = (sampleRate * bufferMs / 1000.0).roundToInt()
        val targetBufBytes = (targetBufSamples * frameSize).coerceAtLeast(minBuf)

        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(channelConfig)
            .build()

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attr)
            .setAudioFormat(format)
            .setBufferSizeInBytes(targetBufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        thread(start = true, name = "WhiteNoiseThread") {
            val rand = Random(System.nanoTime())
            val samplesPerWrite = targetBufBytes / frameSize
            val shortBuffer = ShortArray(samplesPerWrite * channelCount)

            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= durationMs) {
                    try {
                        audioTrack.stop()
                        audioTrack.flush()
                        audioTrack.release()
                    } catch (_: Exception) { }
                    break
                }

                // 生成白噪音样本
                for (i in 0 until samplesPerWrite) {
                    val sample = (rand.nextDouble(-1.0, 1.0) * volume * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()

                    if (channelCount == 1) {
                        shortBuffer[i] = sample
                    } else {
                        val idx = i * 2
                        shortBuffer[idx] = sample
                        shortBuffer[idx + 1] = sample
                    }
                }

                try {
                    audioTrack.write(shortBuffer, 0, shortBuffer.size)
                } catch (_: Exception) {
                    break
                }
            }
        }
    }
}
