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

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            prefs = applicationContext.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            loadNewPairIntoMemory()
            if (prefs.getBoolean("is_fetching_enabled", false)) FetchManager.startFetching(applicationContext)
        }

        override fun onVisibilityChanged(visible: Boolean) {
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
                    loadNewPairIntoMemory()
                    withContext(Dispatchers.Main) { drawCurrentWallpaper(surfaceHolder) }
                    delay(intervalSeconds * 1000L)
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
