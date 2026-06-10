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

    // 元数据缓存池
    private val webDavImageCache = mutableMapOf<String, MutableList<String>>()

    fun startFetching(context: Context) {
        if (fetchJob?.isActive == true) return
        LogManager.i("Engine", "======== 引擎启动 ========")
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("fetch_status_running", true).apply()

        fetchJob = scope.launch {
            var dupCount = 0
            while (isActive) {
                val targetApi = prefs.getInt("target_count_api", 100)
                val targetDav = prefs.getInt("target_count_webdav", 100)
                val useBuiltIn = prefs.getBoolean("use_builtin", true)
                val useCustom = prefs.getBoolean("use_custom", false)
                val customApi = prefs.getString("custom_api", "") ?: ""
                
                val configs = WebDavManager.loadConfigs(context)
                val actions = mutableListOf<suspend () -> Boolean?>()

                if (useBuiltIn) {
                    actions.add { LogManager.i("API", "拉取内置源..."); networkManager.fetchPortraitWallpaper()?.let { fileManager.saveNetworkWallpaper(it, 0, context) != null } }
                    actions.add { networkManager.fetchLandscapeWallpaper()?.let { fileManager.saveNetworkWallpaper(it, 1, context) != null } }
                }

                if (useCustom && customApi.isNotBlank()) {
                    actions.add {
                        try {
                            LogManager.i("API", "请求自定义图源: $customApi")
                            // 强制不缓存，确保你的 Python 脚本每次返回最新数据
                            val request = okhttp3.Request.Builder().url(customApi).cacheControl(okhttp3.CacheControl.FORCE_NETWORK).build()
                            val response = customOkHttpClient.newCall(request).execute()
                            
                            val finalUrl = response.request.url.toString()
                            // 👇 零带宽拦截：只有当 API 发生 301/302 跳转时，才用 URL 查账
                            if (finalUrl != customApi && LogManager.isUrlProcessed(finalUrl, context)) {
                                LogManager.i("API", "拦截到重复的重定向地址，切断数据流: $finalUrl")
                                response.close()
                                return@add false
                            }
                            
                            val bytes = response.body?.bytes()
                            if (bytes != null) {
                                val saved = fileManager.saveNetworkWallpaper(bytes, if(isLand(bytes)) 1 else 0, context) != null
                                if (saved) {
                                    LogManager.i("API", "成功下载入库")
                                    if (finalUrl != customApi) LogManager.markUrlProcessed(finalUrl, context) // 仅记录产生重定向的静态URL
                                } else LogManager.i("API", "MD5 拦截：图片内容已存在")
                                saved
                            } else false
                        } catch (e: Exception) { LogManager.i("API", "请求失败: ${e.message}"); false }
                    }
                }

                // 👇 核心多路径并行队列：遍历所有选中路径，加入抽奖池
                configs.filter { it.isEnabled }.forEach { config ->
                    config.paths.filter { it.isEnabled }.forEach { p ->
                        actions.add { fetchFromWebDavPath(config, p, context) }
                    }
                }

                if (actions.isEmpty()) { 
                    LogManager.i("Engine", "未发现有效的数据源，休眠...")
                    delay(5000L); continue 
                }

                // 随机抽取一个任务执行，避免任务饥饿
                val action = actions.random()
                val success = action() ?: false
                
                if (success) { dupCount = 0 } else { dupCount++ }
                
                if (dupCount >= 10) {
                    LogManager.i("Engine", "连续无新图，触发保护休眠 10 秒...")
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
            
            // 👇 递归扫描算法：最多往下扫 50 个文件夹，并提取所有图片
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
                        // 👇 零带宽拦截：如果在账单里，直接不要
                        if (!LogManager.isUrlProcessed(item.href, context)) allNewImages.add(item.href)
                    }
                }
                scanDepth++
            }

            WebDavManager.saveConfigs(context, WebDavManager.loadConfigs(context).map { if(it.id == config.id) config else it })
            
            if (allNewImages.isEmpty()) {
                LogManager.i("WebDAV", "扫描完毕，暂无新图片")
                return false
            }
            LogManager.i("WebDAV", "扫描完毕，共发现 ${allNewImages.size} 张新图加入列队")
            webDavImageCache[cacheKey] = allNewImages.shuffled().toMutableList()
        }
        
        val imgUrl = webDavImageCache[cacheKey]!!.removeAt(0)
        LogManager.i("WebDAV", "开始下载: ${imgUrl.substringAfterLast('/')}")
        val bytes = WebDavManager.downloadFile(config, imgUrl) ?: return false
        
        LogManager.markUrlProcessed(imgUrl, context) // 记入历史账单
        
        val type = if (isLand(bytes)) 5 else 4
        val saved = fileManager.saveWebDavWallpaper(bytes, type, context) != null
        if (saved) LogManager.i("WebDAV", "成功入库新图") else LogManager.i("WebDAV", "MD5拦截：本地已存在")
        return saved
    }

    fun stopFetching(context: Context) {
        fetchJob?.cancel()
        fetchJob = null
        webDavImageCache.clear()
        LogManager.i("Engine", "======== 引擎已强制停止 ========")
        context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE).edit().putBoolean("fetch_status_running", false).apply()
    }
}
