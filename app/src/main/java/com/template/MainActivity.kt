package com.template

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
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
        val prefs = getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)

        val etInterval = findViewById<EditText>(R.id.etInterval)
        val etTarget = findViewById<EditText>(R.id.etTarget)
        val swAutoRefresh = findViewById<Switch>(R.id.swAutoRefresh)

        etInterval.setText(prefs.getInt("interval", 10).toString())
        etTarget.setText(prefs.getInt("target_count", 100).toString())
        swAutoRefresh.isChecked = prefs.getBoolean("auto_refresh", false)

        // 👇 核心升级1：改用 OpenMultipleDocuments()，直接唤醒原生系统文件管理器，且原生支持批量选择
        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                Toast.makeText(this, "正在后台批量导入 ${uris.size} 张图片...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    var count = 0
                    for (uri in uris) {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            // 使用带有时间戳和序号的安全命名，防止批量导入时覆盖
                            val tempFile = File(cacheDir, "temp_import_${System.currentTimeMillis()}_$count.jpg")
                            val outputStream = FileOutputStream(tempFile)
                            inputStream?.copyTo(outputStream)
                            inputStream?.close()
                            outputStream.close()
                            
                            fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)
                            tempFile.delete() // 导入完毕后立刻清理临时文件，释放内存
                            count++
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(this@MainActivity, "成功导入 $count 张本地壁纸！请进入图库查看", Toast.LENGTH_LONG).show() 
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply { putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, ChenWallpaperService::class.java)) })
        }

        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java).apply { putExtra("IS_TRASH", false) })
        }

        findViewById<Button>(R.id.btnTrash).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java).apply { putExtra("IS_TRASH", true) })
        }

        // 👇 核心升级2：传入 image/*，让文件管理器只过滤显示图片格式的文件
        findViewById<Button>(R.id.btnImport).setOnClickListener { filePickerLauncher.launch(arrayOf("image/*")) }

        findViewById<Button>(R.id.btnSaveInterval).setOnClickListener {
            if (etInterval.text.isNotEmpty()) {
                prefs.edit().putInt("interval", etInterval.text.toString().toInt()).apply()
                Toast.makeText(this, "时间已立刻生效", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnSaveTarget).setOnClickListener {
            if (etTarget.text.isNotEmpty()) {
                val target = etTarget.text.toString().toInt()
                prefs.edit().putInt("target_count", target).apply()
                fileManager.shrinkNetworkCache(target, this)
                Toast.makeText(this, "目标张数已生效，引擎会根据该阈值运作", Toast.LENGTH_SHORT).show()
            }
        }

        swAutoRefresh.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_refresh", isChecked).apply()
        }
    }
}
