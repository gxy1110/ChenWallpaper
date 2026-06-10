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

    // 👇 核心进化：你的“元数据结构缓存池”！保存每个网盘下所有扫描到的图片链接
    private val webDavImageCache = mutableMapOf<String, MutableList<String>>()

    fun startFetching(context: Context) {
        if (fetchJob?.isActive == true) return
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("fetch_status_running", true).apply()

        fetchJob = scope.launch {
            var dupCount = 0
            try {
                while (isActive) {
                    val targetApi = prefs.getInt("target_count_api", 100)
                    val targetDav = prefs.getInt("target_count_webdav", 100)
                    val autoRefresh = prefs.getBoolean("auto_refresh", false)
                    val useBuiltIn = prefs.getBoolean("use_builtin", true)
                    val useCustom = prefs.getBoolean("use_custom", false)
                    val customApi = prefs.getString("custom_api", "") ?: ""
                    
                    val webDavConfigs = WebDavManager.loadConfigs(context).filter { it.isEnabled }

                    val ports = fileManager.getWallpapers(0, false, context)
                    val lands = fileManager.getWallpapers(1, false, context)
                    val davPorts = fileManager.getWallpapers(4, false, context)
                    val davLands = fileManager.getWallpapers(5, false, context)

                    val needPort = ports.size < targetApi || autoRefresh
                    val needLand = lands.size < targetApi || autoRefresh
                    val needDav = davPorts.size < targetDav || davLands.size < targetDav || autoRefresh

                    val actions = mutableListOf<String>()
                    if (useBuiltIn) {
                        if (needPort) actions.add("BUILTIN_PORT")
                        if (needLand) actions.add("BUILTIN_LAND")
                    }
                    if (useCustom && customApi.isNotBlank() && (needPort || needLand)) actions.add("CUSTOM")
                    if (webDavConfigs.isNotEmpty() && needDav) actions.add("WEBDAV")

                    var performedFetch = false

                    if (actions.isNotEmpty()) {
                        val action = actions.random()

                        if (action == "WEBDAV") {
                            val config = webDavConfigs.random()
                            val enabledPaths = config.paths.filter { it.isEnabled }.map { it.path }
                            if (enabledPaths.isNotEmpty()) {
                                
                                // 👇 如果元数据池没数据，开始全自动深度遍历扫描
                                if (webDavImageCache[config.id].isNullOrEmpty()) {
                                    val allImages = mutableListOf<String>()
                                    val folderQueue = enabledPaths.toMutableList()
                                    var scanDepth = 0
                                    
                                    // 递归探测引擎，最多下潜 200 次文件夹（防止陷入死循环被封 IP）
                                    while (folderQueue.isNotEmpty() && scanDepth < 200) {
                                        val targetUrl = folderQueue.removeAt(0)
                                        val items = WebDavManager.listDirectory(config, targetUrl)
                                        if (items != null) {
                                            config.isConnected = true
                                            items.forEach { item ->
                                                if (item.isFolder) {
                                                    folderQueue.add(item.href) // 发现子文件夹，加入待扫队列
                                                } else {
                                                    val name = item.name.lowercase()
                                                    // 捕获各种常见图片格式
                                                    if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif")) {
                                                        allImages.add(item.href) // 发现图片，存入元数据大池
                                                    }
                                                }
                                            }
                                        }
                                        scanDepth++
                                    }
                                    
                                    if (allImages.isNotEmpty()) {
                                        webDavImageCache[config.id] = allImages.shuffled().toMutableList() // 打乱顺序，准备抽取
                                    } else {
                                        config.isConnected = false // 如果扫完依然啥也没有，亮红灯预警
                                    }
                                    // 刷新红绿灯状态到 UI
                                    WebDavManager.saveConfigs(context, WebDavManager.loadConfigs(context).map { if(it.id == config.id) config else it })
                                }

                                // 👇 重点：直接从元数据池里抽一张图片进行下载，绝不浪费网络请求！
                                val images = webDavImageCache[config.id]
                                if (!images.isNullOrEmpty()) {
                                    val imgUrl = images.removeAt(0)
                                    val bytes = WebDavManager.downloadFile(config, imgUrl)
                                    if (bytes != null) {
                                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                        if (opts.outWidth > 0 && opts.outHeight > 0) {
                                            val type = if (opts.outWidth > opts.outHeight) 5 else 4
                                            val currentDavPool = fileManager.getWallpapers(type, false, context)
                                            val saved = fileManager.saveWebDavWallpaper(bytes, type, context)
                                            if (saved != null) {
                                                dupCount = 0
                                                if (currentDavPool.size >= targetDav && autoRefresh) fileManager.moveToTrash(currentDavPool.random(), type, context)
                                            } else dupCount++
                                            performedFetch = true
                                        }
                                    }
                                }
                            }
                        } else {
                            var fetchedBytes: ByteArray? = null
                            var type = -1
                            when (action) {
                                "BUILTIN_PORT" -> { fetchedBytes = networkManager.fetchPortraitWallpaper(); type = 0 }
                                "BUILTIN_LAND" -> { fetchedBytes = networkManager.fetchLandscapeWallpaper(); type = 1 }
                                "CUSTOM" -> {
                                    try {
                                        fetchedBytes = customOkHttpClient.newCall(okhttp3.Request.Builder().url(customApi).build()).execute().body?.bytes()
                                        if (fetchedBytes != null) {
                                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                            BitmapFactory.decodeByteArray(fetchedBytes, 0, fetchedBytes.size, opts)
                                            type = if (opts.outWidth > opts.outHeight) 1 else 0
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                            if (fetchedBytes != null && type != -1) {
                                val currentPool = fileManager.getWallpapers(type, false, context)
                                val saved = fileManager.saveNetworkWallpaper(fetchedBytes, type, context)
                                if (saved != null) {
                                    dupCount = 0
                                    if (currentPool.size >= targetApi && autoRefresh) fileManager.moveToTrash(currentPool.random(), type, context)
                                } else dupCount++
                                performedFetch = true
                            }
                        }
                    }

                    if (dupCount >= 3) { dupCount = 0; delay(10000L) } 
                    else if (performedFetch) delay(1000L)
                    else delay(3000L)
                }
            } finally {
                prefs.edit().putBoolean("fetch_status_running", false).apply()
            }
        }
    }

    fun stopFetching(context: Context) {
        fetchJob?.cancel()
        fetchJob = null
        context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE).edit().putBoolean("fetch_status_running", false).apply()
    }
}
