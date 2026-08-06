package com.example.minimalistnotebook.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.minimalistnotebook.service.MemoryGuardianService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 当手机重启完毕时，系统会发送 BOOT_COMPLETED 广播
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 自动唤醒记忆胶囊服务，挂载灵动岛
            MemoryGuardianService.start(context)
        }
    }
}