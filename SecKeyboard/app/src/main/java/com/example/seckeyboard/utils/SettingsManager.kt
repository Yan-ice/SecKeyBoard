package com.example.seckeyboard.utils

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREF_NAME = "app_settings"
    private const val KEY_INTERVAL = "vibration_interval"
    private const val KEY_AMP = "vibration_amp"

    private lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveVibrationInterval(ms: Int) {
        preferences.edit().putInt(KEY_INTERVAL, ms).apply()
    }

    fun getVibrationInterval(default: Int = 200): Int {
        return preferences.getInt(KEY_INTERVAL, default)
    }

    fun saveVibrationAmp(amp: Int) {
        preferences.edit().putInt(KEY_AMP, amp).apply()
    }

    fun getVibrationAmp(default: Int = 50): Int {
        return preferences.getInt(KEY_AMP, default)
    }
}
