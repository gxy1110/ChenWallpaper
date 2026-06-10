package com.template

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object LogManager {
    private val logs = LinkedList<String>()
    private const val MAX_LOGS = 1000
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun i(tag: String, msg: String) {
        val time = dateFormat.format(Date())
        val logLine = "[$time][$tag] $msg"
        Log.i("ChenWall", logLine)
        synchronized(logs) {
            logs.add(logLine)
            if (logs.size > MAX_LOGS) logs.removeFirst()
        }
    }

    fun getLogs(): String = synchronized(logs) { logs.joinToString("\n") }
    fun clearLogs() = synchronized(logs) { logs.clear() }

    // 核心查账机制：利用本地轻量级 KV 存储记忆已抓取的 URL
    fun isUrlProcessed(url: String, context: Context): Boolean {
        val prefs = context.getSharedPreferences("WallHistory", Context.MODE_PRIVATE)
        return prefs.getBoolean(url.hashCode().toString(), false) 
    }

    fun markUrlProcessed(url: String, context: Context) {
        val prefs = context.getSharedPreferences("WallHistory", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(url.hashCode().toString(), true).apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences("WallHistory", Context.MODE_PRIVATE).edit().clear().apply()
        i("System", "已彻底清空 URL 抓取历史账本！")
    }
}
