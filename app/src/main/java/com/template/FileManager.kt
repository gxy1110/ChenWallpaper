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

    // type: 0=NetPort, 1=NetLand, 2=LocPort, 3=LocLand
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

    // 获取特定类别的壁纸列表
    fun getWallpapers(type: Int, isTrash: Boolean, context: Context): List<File> {
        return getDir(context, type, isTrash).listFiles()?.toList() ?: emptyList()
    }

    // 保存网络壁纸：如果已存在(重复)则返回 null，否则返回新文件
    fun saveNetworkWallpaper(bytes: ByteArray, type: Int, context: Context): File? {
        val dir = getDir(context, type, false)
        val file = File(dir, "${calculateMD5(bytes)}.jpg")
        if (file.exists()) return null // 检测到重复，拒绝写入
        file.writeBytes(bytes)
        return file
    }

    fun importLocalImage(filePath: String, context: Context) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        val isLandscape = options.outWidth > options.outHeight
        val localFile = File(filePath)
        if (localFile.exists()) {
            val bytes = localFile.readBytes()
            val type = if (isLandscape) 3 else 2
            val file = File(getDir(context, type, false), "${calculateMD5(bytes)}.jpg")
            if (!file.exists()) file.writeBytes(bytes)
        }
    }

    // 移入回收站
    fun moveToTrash(file: File, type: Int, context: Context) {
        val trashDir = getDir(context, type, true)
        file.renameTo(File(trashDir, file.name))
    }

    // 从回收站恢复
    fun restoreFromTrash(file: File, type: Int, context: Context) {
        val activeDir = getDir(context, type, false)
        file.renameTo(File(activeDir, file.name))
    }

    // 动态收缩网络缓存：当用户调低目标数量时触发
    fun shrinkNetworkCache(targetCount: Int, context: Context) {
        listOf(0, 1).forEach { type ->
            val files = getWallpapers(type, false, context).shuffled()
            if (files.size > targetCount) {
                // 将超出部分随机移入回收站
                files.take(files.size - targetCount).forEach { moveToTrash(it, type, context) }
            }
        }
    }
}
