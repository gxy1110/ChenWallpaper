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
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        private var displayJob: Job? = null
        private var currentPortrait: Bitmap? = null
        private var currentLandscape: Bitmap? = null
        private lateinit var prefs: SharedPreferences
        private var surfaceWidth = 1080
        private var surfaceHeight = 1920

        // 👇 核心修复 1：记录上一次真正切换壁纸的具体时间戳
        private var lastChangeTime = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            prefs = applicationContext.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            
            // 初始化拉取一次图片并记录下最初的时间
            loadNewPairIntoMemory()
            lastChangeTime = System.currentTimeMillis()
            
            if (prefs.getBoolean("is_fetching_enabled", false)) FetchManager.startFetching(applicationContext)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            // 可见性改变时，依然重新拉起循环，但循环内部逻辑已经变了
            if (visible) startDisplayLoop() else displayJob?.cancel()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.surfaceWidth = width
            this.surfaceHeight = height
            drawCurrentWallpaper(holder)
            if (displayJob == null || displayJob?.isActive == false) startDisplayLoop()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "interval") startDisplayLoop()
        }

        private fun startDisplayLoop() {
            displayJob?.cancel()
            displayJob = engineScope.launch {
                while (isActive) {
                    val intervalSeconds = prefs.getInt("interval", 10)
                    val intervalMs = intervalSeconds * 1000L
                    val currentTime = System.currentTimeMillis()

                    // 👇 核心修复 2：计算时间差。只有当真正流逝的时间超过了设定的间隔，才允许读取新图片
                    if (currentTime - lastChangeTime >= intervalMs || (currentPortrait == null && currentLandscape == null)) {
                        loadNewPairIntoMemory()
                        lastChangeTime = System.currentTimeMillis() // 更新记忆时间戳
                    }

                    // 无论有没有换图，只要你切回了桌面，就把当前内存里存着的这张图再画一次，防止黑屏
                    withContext(Dispatchers.Main) { drawCurrentWallpaper(surfaceHolder) }

                    // 👇 核心修复 3：计算下一次换图【还需要等待】多长时间
                    val elapsed = System.currentTimeMillis() - lastChangeTime
                    val timeToWait = intervalMs - elapsed

                    // 比如设定10秒，你切出去3秒切回来，这里算出来就是再等7秒，而不是重新等10秒
                    if (timeToWait > 0) {
                        delay(timeToWait)
                    } else {
                        delay(100L) // 兜底防止意外的死循环卡顿
                    }
                }
            }
        }

        private fun loadNewPairIntoMemory() {
            val ports = fileManager.getWallpapers(0, false, applicationContext) + fileManager.getWallpapers(2, false, applicationContext) + fileManager.getWallpapers(4, false, applicationContext)
            val lands = fileManager.getWallpapers(1, false, applicationContext) + fileManager.getWallpapers(3, false, applicationContext) + fileManager.getWallpapers(5, false, applicationContext)
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
            } finally { holder.unlockCanvasAndPost(canvas) }
        }

        override fun onDestroy() {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            engineScope.cancel()
            super.onDestroy()
        }
    }
}
