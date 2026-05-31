package com.template

import android.app.WallpaperManager
import android.content.ComponentName
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 核心：直接加载原生 XML 布局，不使用任何 Compose
        setContentView(R.layout.activity_main)

        val fileManager = FileManager()
        val networkManager = NetworkManager()

        // 导入本地壁纸的回调逻辑
        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        if (bytes != null) {
                            fileManager.saveWallpaper(bytes, true, this@MainActivity)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "导入成功！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // 按钮1：启动服务
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, ChenWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "启动失败", Toast.LENGTH_SHORT).show()
            }
        }

        // 按钮2：手动从 API 更新
        findViewById<Button>(R.id.btnManualUpdate).setOnClickListener {
            Toast.makeText(this, "正在后台获取新壁纸...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val portrait = networkManager.fetchPortraitWallpaper()
                    if (portrait != null) fileManager.saveWallpaper(portrait, false, this@MainActivity)

                    val landscape = networkManager.fetchLandscapeWallpaper()
                    if (landscape != null) fileManager.saveWallpaper(landscape, true, this@MainActivity)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "获取成功！", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "网络错误，请检查API", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 按钮3：跳转到图库页面
        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        // 按钮4：触发导入本地
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            filePickerLauncher.launch("image/*")
        }
    }
}
