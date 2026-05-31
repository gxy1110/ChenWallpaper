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
        private val networkManager = NetworkManager()
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
        }

        // --- 核心一：显示引擎 ---
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

        // --- 核心二：智能网络抓取引擎 (1秒限流 + 自动替换机制) ---
        private fun startFetchLoop() {
            fetchJob?.cancel()
            fetchJob = engineScope.launch {
                var dupPort = 0
                var dupLand = 0

                while (isActive) {
                    val target = prefs.getInt("target_count", 100)
                    val autoRefresh = prefs.getBoolean("auto_refresh", false)

                    val ports = fileManager.getWallpapers(0, false, applicationContext)
                    val lands = fileManager.getWallpapers(1, false, applicationContext)

                    var performedFetch = false

                    // 抓取竖屏
                    if (ports.size < target || (ports.size >= target && autoRefresh)) {
                        val bytes = networkManager.fetchPortraitWallpaper()
                        if (bytes != null) {
                            val newFile = fileManager.saveNetworkWallpaper(bytes, 0, applicationContext)
                            if (newFile != null) {
                                dupPort = 0 // 成功获取，重置重复计数器
                                // 如果是自动刷新替换，随机丢一张旧图进回收站
                                if (ports.size >= target && autoRefresh) fileManager.moveToTrash(ports.random(), 0, applicationContext)
                            } else {
                                dupPort++
                            }
                            performedFetch = true
                        }
                    }

                    // 抓取横屏
                    if (lands.size < target || (lands.size >= target && autoRefresh)) {
                        val bytes = networkManager.fetchLandscapeWallpaper()
                        if (bytes != null) {
                            val newFile = fileManager.saveNetworkWallpaper(bytes, 1, applicationContext)
                            if (newFile != null) {
                                dupLand = 0
                                if (lands.size >= target && autoRefresh) fileManager.moveToTrash(lands.random(), 1, applicationContext)
                            } else {
                                dupLand++
                            }
                            performedFetch = true
                        }
                    }

                    // 智能延迟策略：
                    // 如果连续3次拿到重复图片，说明 API 库见底了，强行休眠 10 秒跳过；否则严格执行 1 秒间隔。
                    if (dupPort >= 3 && dupLand >= 3) {
                        dupPort = 0; dupLand = 0
                        delay(10000L) 
                    } else if (performedFetch) {
                        delay(1000L) // 严格遵循 API 的 1 秒规则
                    } else {
                        delay(3000L) // 无需抓取时的空闲探测
                    }
                }
            }
        }

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
