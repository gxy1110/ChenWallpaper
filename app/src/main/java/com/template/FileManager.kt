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
            0 -> "net_port"
            1 -> "net_land"
            2 -> "local_port"
            else -> "local_land"
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
        val dir = getDir(context, type, false)
        val file = File(dir, "$md5.jpg")
        if (file.exists()) return null 

        // 👇 核心机制：霸道清洗。网络图片入库前，巡查本地文件夹。如果有一样的图，立刻物理删除本地图！
        val localPortFile = File(getDir(context, 2, false), "$md5.jpg")
        if (localPortFile.exists()) localPortFile.delete()
        
        val localLandFile = File(getDir(context, 3, false), "$md5.jpg")
        if (localLandFile.exists()) localLandFile.delete()

        file.writeBytes(bytes)
        return file
    }

    // 👇 修改返回值为 Boolean，告知外部该文件是否真的被导入了
    fun importLocalImage(filePath: String, context: Context): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val isLandscape = options.outWidth > options.outHeight
        val localFile = File(filePath)
        
        if (localFile.exists()) {
            val bytes = localFile.readBytes()
            val md5 = calculateMD5(bytes)
            
            // 👇 核心机制：网络优先。如果要导入的图已经存在于网络图库中，则默默拒绝导入
            val netPortFile = File(getDir(context, 0, false), "$md5.jpg")
            val netLandFile = File(getDir(context, 1, false), "$md5.jpg")
            if (netPortFile.exists() || netLandFile.exists()) {
                return false
            }

            val type = if (isLandscape) 3 else 2
            val file = File(getDir(context, type, false), "$md5.jpg")
            if (!file.exists()) {
                file.writeBytes(bytes)
                return true // 只有全新且未与网络撞车的图，才返回 true
            }
        }
        return false
    }

    fun moveToTrash(file: File, type: Int, context: Context) {
        val trashDir = getDir(context, type, true)
        file.renameTo(File(trashDir, file.name))
    }

    fun restoreFromTrash(file: File, type: Int, context: Context) {
        val activeDir = getDir(context, type, false)
        file.renameTo(File(activeDir, file.name))
    }

    fun shrinkNetworkCache(targetCount: Int, context: Context) {
        listOf(0, 1).forEach { type ->
            val files = getWallpapers(type, false, context).shuffled()
            if (files.size > targetCount) {
                files.take(files.size - targetCount).forEach { moveToTrash(it, type, context) }
            }
        }
    }
}
