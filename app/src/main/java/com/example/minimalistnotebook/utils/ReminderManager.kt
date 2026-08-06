package com.example.minimalistnotebook.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.minimalistnotebook.R
import com.example.minimalistnotebook.receiver.ReminderReceiver
import java.util.Calendar

object ReminderManager {
    private const val CHANNEL_ID = "review_reminder_channel"
    private const val ALARM_REQUEST_CODE = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Review Reminders"
            val descriptionText = "Daily reminders to review your words"
            // 🌟 核心：IMPORTANCE_HIGH 保证了微信一样的顶部悬浮弹窗
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true) // 🌟 开启桌面图标小红点
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // 如果设定的时间已经过了，就安排在明天
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // 使用精准闹钟，无视系统打盹模式强制唤醒
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace() // 针对 Android 14+ 缺少 EXACT_ALARM 权限的兜底
        }
    }

    fun showNotification(context: Context, wordCount: Int) {
        // 这里的 MainActivity 需要是你真正的入口 Activity
        val intent = Intent(context, Class.forName("com.example.minimalistnotebook.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // 临时占位图标，后续可换成你的 App Icon
            .setContentTitle("Time to Review! 📖")
            .setContentText("You have $wordCount words waiting for you. Keep up the good work!")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 再次强调高优先级
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // 点击后自动消失

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }
}