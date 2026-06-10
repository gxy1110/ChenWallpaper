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

    private val webDavImageCache = mutableMapOf<String, MutableList<String>>()
    
    // 状态记录器，用于防止日志被重复刷屏
    private var lastApiFullState = false
    private var lastDavFullState = false

    fun startFetching(context: Context) {
        if (fetchJob?.isActive == true) return
        LogManager.i("Engine", "======== 抓取调度引擎 启动 ========")
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("fetch_status_running", true).apply()

        fetchJob = scope.launch {
            var dupCount = 0
            while (isActive) {
                val targetApi = prefs.getInt("target_count_api", 100)
                val targetDav = prefs.getInt("target_count_webdav", 100)
                val autoRefresh = prefs.getBoolean("auto_refresh", false)
                val useBuiltIn = prefs.getBoolean("use_builtin", true)
                val useCustom = prefs.getBoolean("use_custom", false)
                val customApi = prefs.getString("custom_api", "") ?: ""
                
                val configs = WebDavManager.loadConfigs(context)

                val ports = fileManager.getWallpapers(0, false, context)
                val lands = fileManager.getWallpapers(1, false, context)
                val davPorts = fileManager.getWallpapers(4, false, context)
                val davLands = fileManager.getWallpapers(5, false, context)

                val apiFull = ports.size >= targetApi && lands.size >= targetApi && !autoRefresh
                val davFull = davPorts.size >= targetDav && davLands.size >= targetDav && !autoRefresh

                // 👇 核心升级 4：智能挂起与唤醒日志追踪
                if (apiFull != lastApiFullState) {
                    if (apiFull) LogManager.i("Engine", "API 已达目标限额 ($targetApi 张)，暂缓抓取 API 图源。")
                    else LogManager.i("Engine", "API 目标额度扩大或触发刷新，恢复抓取 API 图源...")
                    lastApiFullState = apiFull
                }
                
                if (davFull != lastDavFullState) {
                    if (davFull) LogManager.i("Engine", "WebDAV 已达目标限额 ($targetDav 张)，暂缓抓取云盘图源。")
                    else LogManager.i("Engine", "WebDAV 目标额度扩大或触发刷新，恢复抓取云盘图源...")
                    lastDavFullState = davFull
                }

                val actions = mutableListOf<suspend () -> Boolean?>()

                if (useBuiltIn && !apiFull) {
                    actions.add { 
                        LogManager.i("API", "请求内置竖屏图源...")
                        val bytes = networkManager.fetchPortraitWallpaper()
                        if (bytes != null) {
                            LogManager.i("API", "内置竖屏获取成功 (${bytes.size / 1024} KB)")
                            fileManager.saveNetworkWallpaper(bytes, 0, context) != null
                        } else { LogManager.i("API", "内置竖屏获取失败"); false }
                    }
                    actions.add { 
                        LogManager.i("API", "请求内置横屏图源...")
                        val bytes = networkManager.fetchLandscapeWallpaper()
                        if (bytes != null) {
                            LogManager.i("API", "内置横屏获取成功 (${bytes.size / 1024} KB)")
                            fileManager.saveNetworkWallpaper(bytes, 1, context) != null
                        } else { LogManager.i("API", "内置横屏获取失败"); false }
                    }
                }

                if (useCustom && customApi.isNotBlank() && !apiFull) {
                    actions.add {
                        try {
                            LogManager.i("API", "请求自定义图源: $customApi")
                            val request = okhttp3.Request.Builder().url(customApi).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).build()
                            val response = customOkHttpClient.newCall(request).execute()
                            
                            val finalUrl = response.request.url.toString()
                            if (finalUrl != customApi && LogManager.isUrlProcessed(finalUrl, context)) {
                                LogManager.i("API", "拦截到已记录的重定向地址，切断下载: $finalUrl")
                                response.close()
                                return@add false
                            }
                            
                            val bytes = response.body?.bytes()
                            if (bytes != null) {
                                val saved = fileManager.saveNetworkWallpaper(bytes, if(isLand(bytes)) 1 else 0, context) != null
                                if (saved) {
                                    LogManager.i("API", "成功入库新图 (${bytes.size / 1024} KB)")
                                    if (finalUrl != customApi) LogManager.markUrlProcessed(finalUrl, context) 
                                } else LogManager.i("API", "过滤：图片内容 MD5 校验已存在")
                                saved
                            } else false
                        } catch (e: Exception) { LogManager.i("API", "请求失败: ${e.message}"); false }
                    }
                }

                if (!davFull) {
                    configs.filter { it.isEnabled }.forEach { config ->
                        config.paths.filter { it.isEnabled }.forEach { p ->
                            actions.add { fetchFromWebDavPath(config, p, context) }
                        }
                    }
                }

                if (actions.isEmpty()) { 
                    delay(5000L); continue 
                }

                val action = actions.random()
                val success = action() ?: false
                
                if (success) { dupCount = 0 } else { dupCount++ }
                
                if (dupCount >= 10) {
                    LogManager.i("Engine", "连续获取失败或重复，防刷保护休眠 10 秒...")
                    delay(10000L) 
                } else delay(1000L)
            }
        }
    }

    private fun isLand(bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth > opts.outHeight
    }

    private fun fetchFromWebDavPath(config: WebDavConfig, p: WebDavPath, context: Context): Boolean? {
        val cacheKey = "${config.id}_${p.path}"
        
        if (webDavImageCache[cacheKey].isNullOrEmpty()) {
            LogManager.i("WebDAV", "扫描元数据: ${p.path.substringAfterLast('/')}")
            
            val allNewImages = mutableListOf<String>()
            val folderQueue = mutableListOf(p.path)
            var scanDepth = 0
            
            while (folderQueue.isNotEmpty() && scanDepth < 50) {
                val targetUrl = folderQueue.removeAt(0)
                val items = WebDavManager.listDirectory(config, targetUrl) ?: continue
                config.isConnected = true
                items.forEach { item ->
                    if (item.isFolder) { folderQueue.add(item.href) } 
                    else if (item.name.endsWith(".jpg", true) || item.name.endsWith(".png", true) || item.name.endsWith(".webp", true) || item.name.endsWith(".jpeg", true)) {
                        if (!LogManager.isUrlProcessed(item.href, context)) allNewImages.add(item.href)
                    }
                }
                scanDepth++
            }

            WebDavManager.saveConfigs(context, WebDavManager.loadConfigs(context).map { if(it.id == config.id) config else it })
            
            if (allNewImages.isEmpty()) {
                LogManager.i("WebDAV", "此路径下扫描完毕，账单验证无新图片。")
                return false
            }
            LogManager.i("WebDAV", "扫描完成，发现 ${allNewImages.size} 张新图加入任务列队")
            webDavImageCache[cacheKey] = allNewImages.shuffled().toMutableList()
        }
        
        val imgUrl = webDavImageCache[cacheKey]!!.removeAt(0)
        LogManager.i("WebDAV", "下行抓取流: ${imgUrl.substringAfterLast('/')}")
        val bytes = WebDavManager.downloadFile(config, imgUrl) ?: return false
        
        LogManager.markUrlProcessed(imgUrl, context) 
        
        val type = if (isLand(bytes)) 5 else 4
        val saved = fileManager.saveWebDavWallpaper(bytes, type, context) != null
        if (saved) LogManager.i("WebDAV", "成功入库新云盘图") else LogManager.i("WebDAV", "过滤：本地 MD5 校验已存在")
        return saved
    }

    fun stopFetching(context: Context) {
        fetchJob?.cancel()
        fetchJob = null
        webDavImageCache.clear()
        lastApiFullState = false
        lastDavFullState = false
        LogManager.i("Engine", "======== 引擎已强制停止 ========")
        context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE).edit().putBoolean("fetch_status_running", false).apply()
    }
}
