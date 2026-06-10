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

    // 内存级路径缓存池
    private val webDavImageCache = mutableMapOf<String, MutableList<String>>()

    fun startFetching(context: Context) {
        if (fetchJob?.isActive == true) return
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
                
                // 1. 构建所有开启的抓取动作 (API + 所有勾选的 WebDAV 路径)
                val actions = mutableListOf<() -> Boolean?>()
                if (useBuiltIn) {
                    actions.add { networkManager.fetchPortraitWallpaper()?.let { fileManager.saveNetworkWallpaper(it, 0, context) != null } }
                    actions.add { networkManager.fetchLandscapeWallpaper()?.let { fileManager.saveNetworkWallpaper(it, 1, context) != null } }
                }
                if (useCustom && customApi.isNotBlank()) {
                    actions.add {
                        val bytes = try { customOkHttpClient.newCall(okhttp3.Request.Builder().url(customApi).build()).execute().body?.bytes() } catch (e: Exception) { null }
                        bytes?.let { fileManager.saveNetworkWallpaper(it, if(isLand(it)) 1 else 0, context) != null }
                    }
                }

                // 👇 核心修复：遍历所有节点下的所有“已开启路径”，并动态将其加入行动列表
                configs.filter { it.isEnabled }.forEach { config ->
                    config.paths.filter { it.isEnabled }.forEach { p ->
                        actions.add { fetchFromWebDavPath(config, p, targetDav, context) }
                    }
                }

                if (actions.isEmpty()) { delay(5000L); continue }

                // 2. 随机执行一项行动
                val action = actions.random()
                val success = action() ?: false
                
                if (success) dupCount = 0 else dupCount++
                
                if (dupCount >= 10) delay(10000L) else delay(1000L)
            }
        }
    }

    private fun isLand(bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth > opts.outHeight
    }

    // 独立执行单次抓取
    private fun fetchFromWebDavPath(config: WebDavConfig, p: WebDavPath, target: Int, context: Context): Boolean? {
        val cacheKey = "${config.id}_${p.path}"
        if (webDavImageCache[cacheKey].isNullOrEmpty()) {
            val items = WebDavManager.listDirectory(config, p.path) ?: return false
            config.isConnected = true
            webDavImageCache[cacheKey] = items.filter { !it.isFolder && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true)) }.map { it.href }.toMutableList()
            if (webDavImageCache[cacheKey]!!.isEmpty()) return false
        }
        
        val imgUrl = webDavImageCache[cacheKey]!!.removeAt(0)
        val bytes = WebDavManager.downloadFile(config, imgUrl) ?: return false
        val type = if (isLand(bytes)) 5 else 4
        return fileManager.saveWebDavWallpaper(bytes, type, context) != null
    }

    fun stopFetching(context: Context) {
        fetchJob?.cancel()
        fetchJob = null
        webDavImageCache.clear()
        context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE).edit().putBoolean("fetch_status_running", false).apply()
    }
}
