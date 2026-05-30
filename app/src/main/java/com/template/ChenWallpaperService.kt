// ChenWallpaperService.kt
class ChenWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return ChenEngine()
    }

    inner class ChenEngine : Engine() {
        private var portraitBitmap: Bitmap? = null
        private var landscapeBitmap: Bitmap? = null

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawWallpaper(holder, width, height)
        }

        private fun drawWallpaper(holder: SurfaceHolder, width: Int, height: Int) {
            val canvas = holder.lockCanvas()
            if (canvas != null) {
                // 判断横竖屏
                val isLandscape = width > height
                // 从缓存中获取对应壁纸，如果为空则显示默认底色
                val bitmapToDraw = if (isLandscape) landscapeBitmap else portraitBitmap
                
                if (bitmapToDraw != null) {
                    // 实现需求7：完美贴合设备布局 (Center Crop 居中裁剪算法)
                    val scale = Math.max(width.toFloat() / bitmapToDraw.width, height.toFloat() / bitmapToDraw.height)
                    val scaledWidth = bitmapToDraw.width * scale
                    val scaledHeight = bitmapToDraw.height * scale
                    val left = (width - scaledWidth) / 2f
                    val top = (height - scaledHeight) / 2f
                    
                    val srcRect = Rect(0, 0, bitmapToDraw.width, bitmapToDraw.height)
                    val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                    
                    canvas.drawBitmap(bitmapToDraw, srcRect, destRect, null)
                } else {
                    canvas.drawColor(Color.BLACK) // 没有缓存时的默认背景
                }
                holder.unlockCanvasAndPost(canvas)
            }
        }
        
        // 当后台下载好新壁纸时，调用此方法更新内存并重绘
        fun updateBitmaps(portrait: Bitmap?, landscape: Bitmap?) {
            this.portraitBitmap = portrait
            this.landscapeBitmap = landscape
            // 触发重绘...
        }
    }
}
