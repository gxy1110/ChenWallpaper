package com.template

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fileManager = FileManager()
        val networkManager = NetworkManager()
        val prefs = getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        // 将 URI 拷贝到临时文件以便解析长宽
                        val tempFile = File(cacheDir, "temp_import.jpg")
                        val outputStream = FileOutputStream(tempFile)
                        inputStream?.copyTo(outputStream)
                        inputStream?.close()
                        outputStream.close()
                        
                        fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "本地图片导入成功！", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, ChenWallpaperService::class.java))
            })
        }

        findViewById<Button>(R.id.btnManualUpdate).setOnClickListener {
            Toast.makeText(this, "正在后台获取新壁纸...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    networkManager.fetchPortraitWallpaper()?.let { fileManager.saveNetworkWallpaper(it, false, this@MainActivity) }
                    networkManager.fetchLandscapeWallpaper()?.let { fileManager.saveNetworkWallpaper(it, true, this@MainActivity) }
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "获取成功！", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "网络错误", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener { filePickerLauncher.launch("image/*") }

        findViewById<Button>(R.id.btnSetTime).setOnClickListener {
            val times = arrayOf("10秒", "30秒", "60秒 (1分钟)", "300秒 (5分钟)")
            val values = intArrayOf(10, 30, 60, 300)
            AlertDialog.Builder(this)
                .setTitle("选择切换间隔")
                .setItems(times) { _, which ->
                    prefs.edit().putInt("interval", values[which]).apply()
                    Toast.makeText(this, "已设置为 ${times[which]}，重新启动服务后生效", Toast.LENGTH_SHORT).show()
                }.show()
        }
    }
}
