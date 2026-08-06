package com.example.minimalistnotebook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 扩展属性，方便全局单例调用
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_prefs")

class ReminderDataStore(private val context: Context) {
    companion object {
        val HAS_SET_INITIAL = booleanPreferencesKey("has_set_initial")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_REVIEW_TIMESTAMP = longPreferencesKey("last_review_timestamp")
        val MISSED_DAYS_COUNT = intPreferencesKey("missed_days_count")
    }

    val hasSetInitial: Flow<Boolean> = context.dataStore.data.map { it[HAS_SET_INITIAL] ?: false }
    val reminderHour: Flow<Int> = context.dataStore.data.map { it[REMINDER_HOUR] ?: 20 } // 默认晚上8点
    val reminderMinute: Flow<Int> = context.dataStore.data.map { it[REMINDER_MINUTE] ?: 0 }

    suspend fun saveReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[HAS_SET_INITIAL] = true
            prefs[REMINDER_HOUR] = hour
            prefs[REMINDER_MINUTE] = minute
        }
    }

    suspend fun updateLastReviewTime(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_REVIEW_TIMESTAMP] = timestamp
            prefs[MISSED_DAYS_COUNT] = 0 // 一旦复习，漏打卡天数清零
        }
    }

    suspend fun incrementMissedDays() {
        context.dataStore.edit { prefs ->
            val current = prefs[MISSED_DAYS_COUNT] ?: 0
            prefs[MISSED_DAYS_COUNT] = current + 1
        }
    }
}