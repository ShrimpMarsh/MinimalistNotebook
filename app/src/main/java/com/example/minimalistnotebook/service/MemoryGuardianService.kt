package com.example.minimalistnotebook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.minimalistnotebook.MainActivity
import com.example.minimalistnotebook.R
import com.example.minimalistnotebook.data.local.AppDatabase
import com.example.minimalistnotebook.data.local.WordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MemoryGuardianService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val channelId = "memory_capsule_channel_v3"

    // 状态缓存：用于判断是否发生了“真正需要改变通知形态”的跨状态大变动 (-1 代表未初始化)
    private var lastHasTaskState: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification(0))
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startMonitoring() {
        val wordDao = AppDatabase.getDatabase(applicationContext).wordDao()

        // 监听数据库变化
        serviceScope.launch {
            wordDao.getAllWords().collectLatest { allWords ->
                evaluateAndNotify(allWords)
            }
        }

        // 低频兜底轮询（延长至每 5 分钟一次，彻底杜绝高频刷新被系统判定为 noisy）
        serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000)
                val allWords = wordDao.getAllWordsSync()
                evaluateAndNotify(allWords)
            }
        }
    }

    private fun evaluateAndNotify(allWords: List<WordEntity>) {
        val currentTime = System.currentTimeMillis()
        val toReviewCount = allWords.count { it.level < 9 && it.nextReviewTime <= currentTime }
        val hasTask = toReviewCount > 0

        // 🌟 核心防刷锁：只有当“有任务”和“没任务”的状态发生翻转时，才触发系统通知刷新！
        // 比如从 0 变到 5（无 -> 有），或者从 3 变到 0（有 -> 无）。中间数字变动绝不打扰系统。
        if (lastHasTaskState != hasTask) {
            lastHasTaskState = hasTask
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, buildNotification(toReviewCount))
        }
    }

    private fun buildNotification(reviewCount: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 根据是否有任务，切换状态栏小图标
        val smallIconRes = if (reviewCount > 0) {
            R.drawable.ic_capsule_busy
        } else {
            R.drawable.ic_capsule_idle
        }

        // 切换下拉大图标
        val largeIconRes = if (reviewCount > 0) {
            R.drawable.book_note
        } else {
            R.drawable.book_cover
        }

        val (title, content) = if (reviewCount > 0) {
            "记忆的涟漪已被唤醒" to "有 $reviewCount 个词汇，正等待着与你重逢。"
        } else {
            "Minimalist Notebook" to "合上笔记本，让记忆在时间里沉淀。"
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(smallIconRes)
            .setLargeIcon(BitmapFactory.decodeResource(resources, largeIconRes))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "记忆守护胶囊",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "在后台实时、优雅地守护你的复习进度"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MemoryGuardianService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}