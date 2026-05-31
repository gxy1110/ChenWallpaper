package com.template

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

class FileManager {

    // 生成 MD5 用于文件去重
    private fun calculateMD5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun getDir(context: Context, folderName: String): File {
        val dir = File(context.filesDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 保存网络壁纸（自动限制 100 张缓存）
    fun saveNetworkWallpaper(bytes: ByteArray, isLandscape: Boolean, context: Context) {
        val folder = if (isLandscape) "net_land" else "net_port"
        val dir = getDir(context, folder)
        val file = File(dir, "${calculateMD5(bytes)}.jpg")
        
        if (!file.exists()) file.writeBytes(bytes)

        // 缓存清理机制
        val allFiles = dir.listFiles()?.sortedBy { it.lastModified() }
        if (allFiles != null && allFiles.size > 100) {
            allFiles.take(allFiles.size - 100).forEach { it.delete() }
        }
    }

    // 导入本地壁纸（自动判断横竖屏并分类保存）
    fun importLocalImage(filePath: String, context: Context) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val isLandscape = options.outWidth > options.outHeight
        
        val localFile = File(filePath)
        if (localFile.exists()) {
            val bytes = localFile.readBytes()
            val folder = if (isLandscape) "local_land" else "local_port"
            val file = File(getDir(context, folder), "${calculateMD5(bytes)}.jpg")
            if (!file.exists()) file.writeBytes(bytes)
        }
    }

    // 获取指定分类的图片列表
    // type: 0=网络竖屏, 1=网络横屏, 2=本地竖屏, 3=本地横屏
    fun getWallpapers(type: Int, context: Context): List<File> {
        val folder = when(type) {
            0 -> "net_port"
            1 -> "net_land"
            2 -> "local_port"
            else -> "local_land"
        }
        return getDir(context, folder).listFiles()?.toList() ?: emptyList()
    }
}
