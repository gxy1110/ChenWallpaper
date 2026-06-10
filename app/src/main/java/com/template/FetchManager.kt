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

    private val webDavFolderQueue = mutableMapOf<String, MutableList<String>>()

    fun startFetching(context: Context) {
        if (fetchJob?.isActive == true) return
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("fetch_status_running", true).apply()

        fetchJob = scope.launch {
            var dupCount = 0
            try {
                while (isActive) {
                    // 👇 读取分离的目标参数
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
                            val enabledPaths = config.paths.filter { it.isEnabled }
                            if (enabledPaths.isNotEmpty()) {
                                if (webDavFolderQueue[config.id].isNullOrEmpty()) {
                                    webDavFolderQueue[config.id] = enabledPaths.map { it.path }.toMutableList()
                                }
                                
                                val queue = webDavFolderQueue[config.id]!!
                                val targetPath = queue.removeAt(0) 
                                
                                val items = WebDavManager.listDirectory(config, targetPath)
                                if (items != null) {
                                    config.isConnected = true 
                                    WebDavManager.saveConfigs(context, WebDavManager.loadConfigs(context).map { if(it.id == config.id) config else it })
                                    
                                    val subFolders = items.filter { it.isFolder }.map { it.href }
                                    queue.addAll(subFolders) 
                                    
                                    val images = items.filter { !it.isFolder && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".jpeg", true)) }
                                    if (images.isNotEmpty()) {
                                        val imgTarget = images.random()
                                        val bytes = WebDavManager.downloadFile(config, imgTarget.href)
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
                                } else {
                                    config.isConnected = false
                                    WebDavManager.saveConfigs(context, WebDavManager.loadConfigs(context).map { if(it.id == config.id) config else it })
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
