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
import java.io.File

class ChenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ChenEngine()

    inner class ChenEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val fileManager = FileManager()
        private val networkManager = NetworkManager()
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var loopJob: Job? = null
        
        // 核心内存池：锁定当前周期内使用的“一对”壁纸
        private var currentPortrait: Bitmap? = null
        private var currentLandscape: Bitmap? = null
        private lateinit var prefs: SharedPreferences
        
        private var surfaceWidth = 1080
        private var surfaceHeight = 1920

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            prefs = applicationContext.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
            // 👇 注册动态配置监听器：当主 App 修改时间时，这里会立刻收到通知
            prefs.registerOnSharedPreferenceChangeListener(this)
            
            // 👇 核心修复1：服务挂载瞬间即刻从缓存提取第一对壁纸，彻底终结黑屏
            loadNewPairIntoMemory()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) startWallpaperLoop() else loopJob?.cancel()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.surfaceWidth = width
            this.surfaceHeight = height
            // 屏幕旋转或尺寸改变时，立刻执行重绘
            drawCurrentWallpaper(holder)
            if (loopJob == null || loopJob?.isActive == false) startWallpaperLoop()
        }

        // 👇 核心修复2：收到时间变更通知，立刻中断当前 delay，重新开始新周期的循环
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "interval") {
                startWallpaperLoop()
            }
        }

        private fun startWallpaperLoop() {
            loopJob?.cancel()
            loopJob = engineScope.launch {
                while (isActive) {
                    val intervalSeconds = prefs.getInt("interval", 10) // 动态读取实时自定义时间

                    // 1. 静默补充网络缓存池
                    try {
                        if (fileManager.getWallpapers(0, applicationContext).size < 15) {
                            networkManager.fetchPortraitWallpaper()?.let { fileManager.saveNetworkWallpaper(it, false, applicationContext) }
                        }
                        if (fileManager.getWallpapers(1, applicationContext).size < 15) {
                            networkManager.fetchLandscapeWallpaper()?.let { fileManager.saveNetworkWallpaper(it, true, applicationContext) }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    // 2. 获取本轮周期的下一对壁纸
                    loadNewPairIntoMemory()
                    
                    // 3. 驱动主线程进行壁纸表面重绘
                    withContext(Dispatchers.Main) {
                        drawCurrentWallpaper(surfaceHolder)
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

        private fun drawCurrentWallpaper(holder: SurfaceHolder) {
            val canvas = holder.lockCanvas() ?: return
            try {
                val isLandscape = surfaceWidth > surfaceHeight
                
                // 👇 核心修复3（统一逻辑）：旋转屏幕时，优先使用对应方向的图。
                // 如果图库缓存里暂时没有对应的横屏/竖屏图，则自动用另一方向的图进行 Center Crop 居中裁剪对齐，确保绝不露黑
                val bitmap = if (isLandscape) {
                    currentLandscape ?: currentPortrait
                } else {
                    currentPortrait ?: currentLandscape
                }

                canvas.drawColor(Color.BLACK)
                if (bitmap != null) {
                    val scale = Math.max(surfaceWidth.toFloat() / bitmap.width, surfaceHeight.toFloat() / bitmap.height)
                    val scaledWidth = bitmap.width * scale
                    val scaledHeight = bitmap.height * scale
                    val left = (surfaceWidth - scaledWidth) / 2f
                    val top = (surfaceHeight - scaledHeight) / 2f
                    
                    canvas.drawBitmap(
                        bitmap, 
                        Rect(0, 0, bitmap.width, bitmap.height), 
                        RectF(left, top, left + scaledWidth, top + scaledHeight), 
                        null
                    )
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
