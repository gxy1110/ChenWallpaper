package com.template

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

class FileManager {

    private fun calculateMD5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun saveWallpaper(bytes: ByteArray, isLandscape: Boolean, context: Context) {
        val folderName = if (isLandscape) "landscape_cache" else "portrait_cache"
        val dir = File(context.filesDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        
        val hash = calculateMD5(bytes)
        val file = File(dir, "$hash.jpg")
        
        // 去重：如果文件不存在才写入
        if (!file.exists()) {
            file.writeBytes(bytes)
        }

        // 缓存刷新机制：保证最多存 120 张（满足至少 100 张的需求）
        val allFiles = dir.listFiles()?.sortedBy { it.lastModified() }
        if (allFiles != null && allFiles.size > 120) {
            val filesToDelete = allFiles.size - 120
            for (i in 0 until filesToDelete) {
                allFiles[i].delete()
            }
        }
    }

    // 获取对应方向的所有缓存壁纸
    fun getWallpapers(isLandscape: Boolean, context: Context): List<File> {
        val folderName = if (isLandscape) "landscape_cache" else "portrait_cache"
        val dir = File(context.filesDir, folderName)
        return dir.listFiles()?.toList() ?: emptyList()
    }

    fun importLocalImage(filePath: String, context: Context) {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true 
        BitmapFactory.decodeFile(filePath, options)
        
        val isLandscape = options.outWidth > options.outHeight
        val localFile = File(filePath)
        
        if (localFile.exists()) {
            val fileBytes = localFile.readBytes()
            saveWallpaper(fileBytes, isLandscape, context)
        }
    }
}
