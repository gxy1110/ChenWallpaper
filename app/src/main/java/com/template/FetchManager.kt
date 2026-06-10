package com.template

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.*

object FetchManager {
    private var fetchJob: Job? = null
    private val fileManager = FileManager()
    private val networkManager = NetworkManager()
    private val customOkHttpClient = okhttp3.OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startFetching(context: Context) {
        if (fetchJob?.isActive == true) return
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        
        // 标记状态为正在运行，UI会自动响应
        prefs.edit().putBoolean("fetch_status_running", true).apply()

        fetchJob = scope.launch {
            var dupCount = 0
            try {
                while (isActive) {
                    val target = prefs.getInt("target_count", 100)
                    val autoRefresh = prefs.getBoolean("auto_refresh", false)
                    val useBuiltIn = prefs.getBoolean("use_builtin", true)
                    val useCustom = prefs.getBoolean("use_custom", false)
                    val customApi = prefs.getString("custom_api", "") ?: ""

                    val ports = fileManager.getWallpapers(0, false, context)
                    val lands = fileManager.getWallpapers(1, false, context)

                    val needPort = ports.size < target || autoRefresh
                    val needLand = lands.size < target || autoRefresh

                    // 1. 构建抓取任务池
                    val actions = mutableListOf<String>()
                    if (useBuiltIn) {
                        if (needPort) actions.add("BUILTIN_PORT")
                        if (needLand) actions.add("BUILTIN_LAND")
                    }
                    if (useCustom && customApi.isNotBlank()) {
                        if (needPort || needLand) actions.add("CUSTOM")
                    }

                    var performedFetch = false

                    // 2. 随机抽取一个任务执行（严格限流单次请求）
                    if (actions.isNotEmpty()) {
                        val action = actions.random()
                        var fetchedBytes: ByteArray? = null
                        var determinedType = -1

                        when (action) {
                            "BUILTIN_PORT" -> {
                                fetchedBytes = networkManager.fetchPortraitWallpaper()
                                determinedType = 0
                            }
                            "BUILTIN_LAND" -> {
                                fetchedBytes = networkManager.fetchLandscapeWallpaper()
                                determinedType = 1
                            }
                            "CUSTOM" -> {
                                try {
                                    val req = okhttp3.Request.Builder().url(customApi).build()
                                    fetchedBytes = customOkHttpClient.newCall(req).execute().body?.bytes()
                                    // 👇 核心修复：解析图片二进制流，智能判断横竖屏！
                                    if (fetchedBytes != null) {
                                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeByteArray(fetchedBytes, 0, fetchedBytes.size, opts)
                                        determinedType = if (opts.outWidth > opts.outHeight) 1 else 0
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }

                        // 3. 处理数据入库
                        if (fetchedBytes != null && determinedType != -1) {
                            val currentPool = fileManager.getWallpapers(determinedType, false, context)
                            if (currentPool.size < target || autoRefresh) {
                                val saved = fileManager.saveNetworkWallpaper(fetchedBytes, determinedType, context)
                                if (saved != null) {
                                    dupCount = 0
                                    if (currentPool.size >= target && autoRefresh) {
                                        fileManager.moveToTrash(currentPool.random(), determinedType, context)
                                    }
                                } else {
                                    dupCount++ // 触发MD5去重，算作重复
                                }
                            }
                            performedFetch = true
                        }
                    }

                    // 4. 智能休眠策略
                    if (dupCount >= 3) {
                        dupCount = 0
                        delay(10000L) // API枯竭，长休眠
                    } else if (performedFetch) {
                        delay(1000L)  // 正常1秒间隔
                    } else {
                        delay(3000L)  // 任务达标，降频巡检
                    }
                }
            } finally {
                // 无论是被手动停止还是异常断开，必然更新状态为停止
                prefs.edit().putBoolean("fetch_status_running", false).apply()
            }
        }
    }

    fun stopFetching(context: Context) {
        fetchJob?.cancel()
        fetchJob = null
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("fetch_status_running", false).apply()
    }
}
