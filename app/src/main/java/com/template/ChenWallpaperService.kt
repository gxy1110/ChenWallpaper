package com.template

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlinx.coroutines.*

class ChenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return ChenEngine()
    }

    inner class ChenEngine : Engine() {
        private val fileManager = FileManager()
        private val networkManager = NetworkManager()
        private var drawJob: Job? = null
        private var fetchJob: Job? = null
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                startWallpaperLoop()
                startAutoFetch()
            } else {
                // 回到桌面不可见时，暂停重绘和抓取以省电
                drawJob?.cancel()
                fetchJob?.cancel()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (drawJob == null || drawJob?.isActive == false) {
                startWallpaperLoop()
            }
        }

        // 核心：10秒随机切换机制
        private fun startWallpaperLoop() {
            drawJob?.cancel()
            drawJob = engineScope.launch {
                while (isActive) {
                    drawRandomWallpaper()
                    delay(10000) // 延迟 10000 毫秒 (10秒)
                }
            }
        }

        // 核心：后台静默填充缓存池机制
        private fun startAutoFetch() {
            fetchJob?.cancel()
            fetchJob = engineScope.launch {
                while (isActive) {
                    try {
                        val pFiles = fileManager.getWallpapers(false, applicationContext)
                        val lFiles = fileManager.getWallpapers(true, applicationContext)
                        
                        // 只要不到 100 张，就触发网络请求抓取
                        if (pFiles.size < 100) {
                            val portrait = networkManager.fetchPortraitWallpaper()
                            if (portrait != null) fileManager.saveWallpaper(portrait, false, applicationContext)
                        }
                        if (lFiles.size < 100) {
                            val landscape = networkManager.fetchLandscapeWallpaper()
                            if (landscape != null) fileManager.saveWallpaper(landscape, true, applicationContext)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(15000) // 每 15 秒检查/抓取一次，防止请求过快被 API 封禁 IP
                }
            }
        }

        private fun drawRandomWallpaper() {
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return
            
            val width = canvas.width
            val height = canvas.height
            val isLandscape = width > height
            
            // 获取图库列表并打乱
            val files = fileManager.getWallpapers(isLandscape, applicationContext).shuffled()
            
            if (files.isNotEmpty()) {
                val randomFile = files.first()
                val bitmap = BitmapFactory.decodeFile(randomFile.absolutePath)
                
                if (bitmap != null) {
                    val scale = Math.max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                    val scaledWidth = bitmap.width * scale
                    val scaledHeight = bitmap.height * scale
                    val left = (width - scaledWidth) / 2f
                    val top = (height - scaledHeight) / 2f
                    
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                    
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(bitmap, srcRect, destRect, null)
                    
                    // 极致内存优化：画完后立即手动销毁内存中的超大原图
                    bitmap.recycle()
                }
            } else {
                canvas.drawColor(Color.BLACK)
            }
            holder.unlockCanvasAndPost(canvas)
        }
        
        override fun onDestroy() {
            super.onDestroy()
            engineScope.cancel()
        }
    }
}
