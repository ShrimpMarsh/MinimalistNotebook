package com.example.minimalistnotebook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.minimalistnotebook.data.local.AppDatabase
import com.example.minimalistnotebook.data.local.ReminderDataStore
import com.example.minimalistnotebook.utils.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val goAsync = goAsync() // 允许在广播中使用协程进行耗时数据库查询

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 如果是手机重启触发的广播，重新把闹钟注册上
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    val dataStore = ReminderDataStore(context)
                    val hour = dataStore.reminderHour.first()
                    val minute = dataStore.reminderMinute.first()
                    ReminderManager.scheduleDailyReminder(context, hour, minute)
                    return@launch
                }

                // 2. 正常闹钟触发：静默查询数据库
                val database = AppDatabase.getDatabase(context)
                val now = System.currentTimeMillis()

                // 查询目前所有到期需要复习的单词数量 (等级<9 且时间已到)
                val dueWords = database.wordDao().getAllWordsSync().filter {
                    it.level < 9 && it.nextReviewTime <= now
                }

                if (dueWords.isNotEmpty()) {
                    // 🌟 只有真的有单词需要复习，才弹出高优先级通知！
                    ReminderManager.showNotification(context, dueWords.size)
                } else {
                    // 🌟 任务清空，不打扰，但记录一次漏打卡（说明用户没打开App但任务本就是空的，这里其实可根据业务微调，暂作忽略）
                }

                // 3. 闹钟是一次性的，执行完后需要预定明天的闹钟
                val dataStore = ReminderDataStore(context)
                val hour = dataStore.reminderHour.first()
                val minute = dataStore.reminderMinute.first()
                ReminderManager.scheduleDailyReminder(context, hour, minute)

            } finally {
                goAsync.finish()
            }
        }
    }
}