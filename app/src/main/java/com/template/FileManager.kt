// FileManager.kt
import java.security.MessageDigest

fun calculateMD5(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

fun saveWallpaper(bytes: ByteArray, isLandscape: Boolean, context: Context) {
    val hash = calculateMD5(bytes)
    val folderName = if (isLandscape) "landscape_cache" else "portrait_cache"
    val dir = File(context.filesDir, folderName)
    if (!dir.exists()) dir.mkdirs()
    
    val file = File(dir, "$hash.jpg")
    // 实现需求4：若获取到重复文件，去重（文件已存在则不保存）
    if (!file.exists()) {
        file.writeBytes(bytes)
    }
}
