package com.template

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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

        // 初始化输入框并回显当前已保存的时间
        val etInterval = findViewById<EditText>(R.id.etInterval)
        etInterval.setText(prefs.getInt("interval", 10).toString())

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val tempFile = File(cacheDir, "temp_import.jpg")
                        val outputStream = FileOutputStream(tempFile)
                        inputStream?.copyTo(outputStream)
                        inputStream?.close()
                        outputStream.close()
                        
                        fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)
                        withContext(Dispatchers.Main) { 
                            Toast.makeText(this@MainActivity, "本地导入成功！请进入图库点击对应本地分类查看", Toast.LENGTH_LONG).show() 
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, ChenWallpaperService::class.java))
                }
                startActivity(intent)
            } catch (e: Exception) { Toast.makeText(this, "启动失败", Toast.LENGTH_SHORT).show() }
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

        // 👇 自定义时间保存监听：保存后壁纸后台会自动响应并生效
        findViewById<Button>(R.id.btnSaveInterval).setOnClickListener {
            val inputStr = etInterval.text.toString()
            if (inputStr.isNotEmpty()) {
                val seconds = inputStr.toInt()
                if (seconds > 0) {
                    prefs.edit().putInt("interval", seconds).apply()
                    Toast.makeText(this, "切换间隔已立刻更新为 ${seconds} 秒！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请输入大于0的秒数", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
