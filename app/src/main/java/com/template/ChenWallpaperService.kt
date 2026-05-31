package com.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import java.io.File

class ChenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ChenEngine()

    inner class ChenEngine : Engine() {
        private val fileManager = FileManager()
        private val networkManager = NetworkManager()
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var loopJob: Job? = null
        
        // 核心：在内存中保持“当前一对”壁纸，实现旋转秒切
        private var currentPortrait: Bitmap? = null
        private var currentLandscape: Bitmap? = null

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) startWallpaperLoop() else loopJob?.cancel()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawCurrentWallpaper(width, height) // 旋转屏幕时，直接绘制内存中现成的图，0延迟！
            if (loopJob == null || loopJob?.isActive == false) startWallpaperLoop()
        }

        private fun startWallpaperLoop() {
            loopJob?.cancel()
            loopJob = engineScope.launch {
                val prefs = applicationContext.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
                while (isActive) {
                    val intervalSeconds = prefs.getInt("interval", 10) // 默认 10 秒
                    
                    // 1. 后台静默补充网络弹药库
                    if (fileManager.getWallpapers(0, applicationContext).size < 50) {
                        networkManager.fetchPortraitWallpaper()?.let { fileManager.saveNetworkWallpaper(it, false, applicationContext) }
                    }
                    if (fileManager.getWallpapers(1, applicationContext).size < 50) {
                        networkManager.fetchLandscapeWallpaper()?.let { fileManager.saveNetworkWallpaper(it, true, applicationContext) }
                    }

                    // 2. 抽取下一对壁纸并加载到内存
                    loadNewPairIntoMemory()
                    
                    // 3. 绘制到屏幕
                    withContext(Dispatchers.Main) {
                        val canvas = surfaceHolder.lockCanvas()
                        if (canvas != null) {
                            drawCurrentWallpaper(canvas.width, canvas.height, canvas)
                            surfaceHolder.unlockCanvasAndPost(canvas)
                        }
                    }
                    
                    delay(intervalSeconds * 1000L)
                }
            }
        }

        private fun loadNewPairIntoMemory() {
            val ports = fileManager.getWallpapers(0, applicationContext) + fileManager.getWallpapers(2, applicationContext)
            val lands = fileManager.getWallpapers(1, applicationContext) + fileManager.getWallpapers(3, applicationContext)
            
            if (ports.isNotEmpty()) currentPortrait = BitmapFactory.decodeFile(ports.random().absolutePath)
            if (lands.isNotEmpty()) currentLandscape = BitmapFactory.decodeFile(lands.random().absolutePath)
        }

        private fun drawCurrentWallpaper(width: Int, height: Int, canvas: android.graphics.Canvas? = null) {
            val c = canvas ?: surfaceHolder.lockCanvas() ?: return
            val isLandscape = width > height
            val bitmap = if (isLandscape) currentLandscape else currentPortrait

            if (bitmap != null) {
                val scale = Math.max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                val left = (width - scaledWidth) / 2f
                val top = (height - scaledHeight) / 2f
                
                c.drawColor(Color.BLACK)
                c.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), RectF(left, top, left + scaledWidth, top + scaledHeight), null)
            } else {
                c.drawColor(Color.BLACK)
            }
            if (canvas == null) surfaceHolder.unlockCanvasAndPost(c)
        }

        override fun onDestroy() {
            super.onDestroy()
            engineScope.cancel()
        }
    }
}
