package com.template

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

class FileManager {

    // 计算文件的 MD5 值，用于去重
    private fun calculateMD5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // 保存从 API 获取的壁纸
    fun saveWallpaper(bytes: ByteArray, isLandscape: Boolean, context: Context) {
        val hash = calculateMD5(bytes)
        val folderName = if (isLandscape) "landscape_cache" else "portrait_cache"
        val dir = File(context.filesDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "$hash.jpg")
        // 若获取到重复文件，去重（文件已存在则不保存）
        if (!file.exists()) {
            file.writeBytes(bytes)
        }

        // 👇 核心接线：将最新获取的壁纸另存为 current_xxx.jpg，供壁纸引擎直接读取
        val currentFileName = if (isLandscape) "current_landscape.jpg" else "current_portrait.jpg"
        File(context.filesDir, currentFileName).writeBytes(bytes)
    }

    // 导入本地图片并自动分类
    fun importLocalImage(filePath: String, context: Context) {
        val options = BitmapFactory.Options()
        // 只读取边缘信息，不加载实际像素，极快且省内存
        options.inJustDecodeBounds = true 
        BitmapFactory.decodeFile(filePath, options)
        
        // 自动判断横竖屏
        val isLandscape = options.outWidth > options.outHeight
        
        // 读取本地文件字节
        val localFile = File(filePath)
        if (localFile.exists()) {
            val fileBytes = localFile.readBytes()
            // 复用 saveWallpaper 方法，实现自动存入对应分类的缓存文件夹，并去重
            saveWallpaper(fileBytes, isLandscape, context)
        }
    }
}
