package com.template

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

class FileManager {
    private fun calculateMD5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun getDir(context: Context, type: Int, isTrash: Boolean): File {
        val base = when (type) {
            0 -> "net_port"; 1 -> "net_land"
            2 -> "local_port"; 3 -> "local_land"
            4 -> "dav_port"; else -> "dav_land"
        }
        val folderName = if (isTrash) "trash_$base" else base
        val dir = File(context.filesDir, folderName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getWallpapers(type: Int, isTrash: Boolean, context: Context): List<File> {
        return getDir(context, type, isTrash).listFiles()?.toList() ?: emptyList()
    }

    fun saveNetworkWallpaper(bytes: ByteArray, type: Int, context: Context): File? {
        val md5 = calculateMD5(bytes)
        val file = File(getDir(context, type, false), "$md5.jpg")
        if (file.exists()) return null 
        for (i in 2..5) {
            val target = File(getDir(context, i, false), "$md5.jpg")
            if (target.exists()) target.delete()
        }
        file.writeBytes(bytes)
        return file
    }

    fun importLocalImage(filePath: String, context: Context): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val isLandscape = options.outWidth > options.outHeight
        val localFile = File(filePath)
        
        if (localFile.exists()) {
            val bytes = localFile.readBytes()
            val md5 = calculateMD5(bytes)
            if (File(getDir(context, 0, false), "$md5.jpg").exists() || File(getDir(context, 1, false), "$md5.jpg").exists()) return false
            val targetDavPort = File(getDir(context, 4, false), "$md5.jpg")
            val targetDavLand = File(getDir(context, 5, false), "$md5.jpg")
            if (targetDavPort.exists()) targetDavPort.delete()
            if (targetDavLand.exists()) targetDavLand.delete()
            val type = if (isLandscape) 3 else 2
            val file = File(getDir(context, type, false), "$md5.jpg")
            if (!file.exists()) { file.writeBytes(bytes); return true }
        }
        return false
    }

    fun saveWebDavWallpaper(bytes: ByteArray, type: Int, context: Context): File? {
        val md5 = calculateMD5(bytes)
        for (i in 0..3) {
            if (File(getDir(context, i, false), "$md5.jpg").exists()) return null
        }
        val file = File(getDir(context, type, false), "$md5.jpg")
        if (file.exists()) return null
        file.writeBytes(bytes)
        return file
    }

    fun moveToTrash(file: File, type: Int, context: Context) {
        val trashDir = getDir(context, type, true)
        file.renameTo(File(trashDir, file.name))
    }

    fun restoreFromTrash(file: File, type: Int, context: Context) {
        val activeDir = getDir(context, type, false)
        file.renameTo(File(activeDir, file.name))
    }

    // 👇 核心分离：将 API 容量和 WebDAV 容量分别进行裁剪计算
    fun shrinkNetworkCache(apiTarget: Int, davTarget: Int, context: Context) {
        listOf(0, 1).forEach { type ->
            val files = getWallpapers(type, false, context).shuffled()
            if (files.size > apiTarget) files.take(files.size - apiTarget).forEach { moveToTrash(it, type, context) }
        }
        listOf(4, 5).forEach { type ->
            val files = getWallpapers(type, false, context).shuffled()
            if (files.size > davTarget) files.take(files.size - davTarget).forEach { moveToTrash(it, type, context) }
        }
    }
}
