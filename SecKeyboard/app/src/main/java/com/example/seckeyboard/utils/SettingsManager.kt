package com.example.seckeyboard.utils

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREF_NAME = "app_settings"
    private const val KEY_INTERVAL = "vibration_interval"

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
}
