package com.template

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.*

class ChenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ChenEngine()

    inner class ChenEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val fileManager = FileManager()
        private val networkManager = NetworkManager() // 原生内置源管理器
        private val customOkHttpClient = okhttp3.OkHttpClient() // 专属自定义API网络客户端
        
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        private var displayJob: Job? = null
        private var fetchJob: Job? = null
        
        private var currentPortrait: Bitmap? = null
        private var currentLandscape: Bitmap? = null
        private lateinit var prefs: SharedPreferences
        
        private var surfaceWidth = 1080
        private var surfaceHeight = 1920

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            prefs = applicationContext.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            loadNewPairIntoMemory()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                startDisplayLoop()
                startFetchLoop()
            } else {
                displayJob?.cancel()
                fetchJob?.cancel()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.surfaceWidth = width
            this.surfaceHeight = height
            drawCurrentWallpaper(holder)
            if (displayJob == null || displayJob?.isActive == false) startDisplayLoop()
            if (fetchJob == null || fetchJob?.isActive == false) startFetchLoop()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "interval") startDisplayLoop()
            // 如果多源配置被修改，立刻重启抓取循环以应用新策略
            if (key in listOf("use_builtin", "use_custom", "custom_port_api", "custom_land_api")) startFetchLoop()
        }

        private fun startDisplayLoop() {
            displayJob?.cancel()
            displayJob = engineScope.launch {
                while (isActive) {
                    val intervalSeconds = prefs.getInt("interval", 10)
                    loadNewPairIntoMemory()
                    withContext(Dispatchers.Main) { drawCurrentWallpaper(surfaceHolder) }
                    delay(intervalSeconds * 1000L)
                }
            }
        }

        // ================== 👇 核心：多源并发与负载均衡抓取引擎 ==================
        private fun fetchFromCustom(url: String): ByteArray? {
            if (url.isBlank()) return null
            return try {
                val req = okhttp3.Request.Builder().url(url).build()
                customOkHttpClient.newCall(req).execute().body?.bytes()
            } catch (e: Exception) { null }
        }

        private fun startFetchLoop() {
            fetchJob?.cancel()
            fetchJob = engineScope.launch {
                var dupPort = 0
                var dupLand = 0

                while (isActive) {
                    val target = prefs.getInt("target_count", 100)
                    val autoRefresh = prefs.getBoolean("auto_refresh", false)
                    
                    val useBuiltIn = prefs.getBoolean("use_builtin", true)
                    val useCustom = prefs.getBoolean("use_custom", false)
                    val customPortApi = prefs.getString("custom_port_api", "") ?: ""
                    val customLandApi = prefs.getString("custom_land_api", "") ?: ""

                    val ports = fileManager.getWallpapers(0, false, applicationContext)
                    val lands = fileManager.getWallpapers(1, false, applicationContext)

                    var performedFetch = false

                    // 1. 组装可用的竖屏数据源 (0 = 内置, 1 = 自定义)
                    val srcPorts = mutableListOf<Int>()
                    if (useBuiltIn) srcPorts.add(0)
                    if (useCustom && customPortApi.isNotBlank()) srcPorts.add(1)

                    // 竖屏轮询抓取
                    if (srcPorts.isNotEmpty() && (ports.size < target || (ports.size >= target && autoRefresh))) {
                        val src = srcPorts.random() // 负载均衡：多选时随机决定本次走哪个API
                        val bytes = if (src == 0) networkManager.fetchPortraitWallpaper() else fetchFromCustom(customPortApi)
                        
                        if (bytes != null) {
                            val newFile = fileManager.saveNetworkWallpaper(bytes, 0, applicationContext)
                            if (newFile != null) {
                                dupPort = 0 
                                if (ports.size >= target && autoRefresh) fileManager.moveToTrash(ports.random(), 0, applicationContext)
                            } else dupPort++
                            performedFetch = true
                        }
                    }

                    // 2. 组装可用的横屏数据源
                    val srcLands = mutableListOf<Int>()
                    if (useBuiltIn) srcLands.add(0)
                    if (useCustom && customLandApi.isNotBlank()) srcLands.add(1)

                    // 横屏轮询抓取
                    if (srcLands.isNotEmpty() && (lands.size < target || (lands.size >= target && autoRefresh))) {
                        val src = srcLands.random()
                        val bytes = if (src == 0) networkManager.fetchLandscapeWallpaper() else fetchFromCustom(customLandApi)
                        
                        if (bytes != null) {
                            val newFile = fileManager.saveNetworkWallpaper(bytes, 1, applicationContext)
                            if (newFile != null) {
                                dupLand = 0
                                if (lands.size >= target && autoRefresh) fileManager.moveToTrash(lands.random(), 1, applicationContext)
                            } else dupLand++
                            performedFetch = true
                        }
                    }

                    // 智能限流与避退策略
                    if (dupPort >= 3 && dupLand >= 3) {
                        dupPort = 0; dupLand = 0
                        delay(10000L) // API 见底，进入长休眠
                    } else if (performedFetch) {
                        delay(1000L) // 严格遵循 API 1 秒访问规范
                    } else {
                        delay(3000L) // 数据满额且未开启自动刷新，降低巡检频率节约电量
                    }
                }
            }
        }
        // =====================================================================

        private fun loadNewPairIntoMemory() {
            val ports = fileManager.getWallpapers(0, false, applicationContext) + fileManager.getWallpapers(2, false, applicationContext)
            val lands = fileManager.getWallpapers(1, false, applicationContext) + fileManager.getWallpapers(3, false, applicationContext)
            if (ports.isNotEmpty()) currentPortrait = BitmapFactory.decodeFile(ports.random().absolutePath)
            if (lands.isNotEmpty()) currentLandscape = BitmapFactory.decodeFile(lands.random().absolutePath)
        }

        private fun drawCurrentWallpaper(holder: SurfaceHolder) {
            val canvas = holder.lockCanvas() ?: return
            try {
                val isLandscape = surfaceWidth > surfaceHeight
                val bitmap = if (isLandscape) currentLandscape ?: currentPortrait else currentPortrait ?: currentLandscape
                canvas.drawColor(Color.BLACK)
                if (bitmap != null) {
                    val scale = Math.max(surfaceWidth.toFloat() / bitmap.width, surfaceHeight.toFloat() / bitmap.height)
                    val scaledWidth = bitmap.width * scale
                    val scaledHeight = bitmap.height * scale
                    val left = (surfaceWidth - scaledWidth) / 2f
                    val top = (surfaceHeight - scaledHeight) / 2f
                    canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), RectF(left, top, left + scaledWidth, top + scaledHeight), null)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            engineScope.cancel()
            super.onDestroy()
        }
    }
}
