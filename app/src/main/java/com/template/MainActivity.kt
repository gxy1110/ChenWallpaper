package com.template

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private val okHttpClient = okhttp3.OkHttpClient() // 用于临时测试自定义API

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fileManager = FileManager()
        val networkManager = NetworkManager()
        val prefs = getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)

        // 基础组件
        val etInterval = findViewById<EditText>(R.id.etInterval)
        val etTarget = findViewById<EditText>(R.id.etTarget)
        val swAutoRefresh = findViewById<Switch>(R.id.swAutoRefresh)
        
        // 高级组件：API与多源
        val cbBuiltIn = findViewById<CheckBox>(R.id.cbBuiltIn)
        val cbCustom = findViewById<CheckBox>(R.id.cbCustom)
        val etCustomPortApi = findViewById<EditText>(R.id.etCustomPortApi)
        val etCustomLandApi = findViewById<EditText>(R.id.etCustomLandApi)

        // 初始化数据回显
        etInterval.setText(prefs.getInt("interval", 10).toString())
        etTarget.setText(prefs.getInt("target_count", 100).toString())
        swAutoRefresh.isChecked = prefs.getBoolean("auto_refresh", false)
        
        cbBuiltIn.isChecked = prefs.getBoolean("use_builtin", true)
        cbCustom.isChecked = prefs.getBoolean("use_custom", false)
        etCustomPortApi.setText(prefs.getString("custom_port_api", ""))
        etCustomLandApi.setText(prefs.getString("custom_land_api", ""))

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                Toast.makeText(this, "正在后台批量导入 ${uris.size} 张图片...", Toast.LENGTH_SHORT).show()
                CoroutineScope(Dispatchers.IO).launch {
                    var count = 0
                    for (uri in uris) {
                        try {
                            val inputStream = contentResolver.openInputStream(uri)
                            val tempFile = File(cacheDir, "temp_import_${System.currentTimeMillis()}_$count.jpg")
                            val outputStream = FileOutputStream(tempFile)
                            inputStream?.copyTo(outputStream)
                            inputStream?.close()
                            outputStream.close()
                            
                            fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)
                            tempFile.delete()
                            count++
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(this@MainActivity, "成功导入 $count 张本地壁纸！", Toast.LENGTH_LONG).show() 
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

        findViewById<Button>(R.id.btnImport).setOnClickListener { filePickerLauncher.launch(arrayOf("image/*")) }

        // 保存 API 高级设置
        findViewById<Button>(R.id.btnSaveApiSettings).setOnClickListener {
            prefs.edit()
                .putBoolean("use_builtin", cbBuiltIn.isChecked)
                .putBoolean("use_custom", cbCustom.isChecked)
                .putString("custom_port_api", etCustomPortApi.text.toString().trim())
                .putString("custom_land_api", etCustomLandApi.text.toString().trim())
                .apply()
            Toast.makeText(this, "API 及多源策略已保存生效！", Toast.LENGTH_SHORT).show()
        }

        // 手动测试抓取逻辑升级：支持双源并发！
        fun fetchCustomDirect(url: String): ByteArray? {
            if (url.isBlank()) return null
            return try {
                val req = okhttp3.Request.Builder().url(url).build()
                okHttpClient.newCall(req).execute().body?.bytes()
            } catch (e: Exception) { null }
        }

        findViewById<Button>(R.id.btnManualUpdate).setOnClickListener {
            val useBuiltIn = cbBuiltIn.isChecked
            val useCustom = cbCustom.isChecked
            val customPort = etCustomPortApi.text.toString().trim()
            val customLand = etCustomLandApi.text.toString().trim()

            if (!useBuiltIn && (!useCustom || (customPort.isEmpty() && customLand.isEmpty()))) {
                Toast.makeText(this, "拦截：请至少开启一个有效图源！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "正在从混合源抓取新壁纸...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 如果开启内置源，各抓取一张
                    if (useBuiltIn) {
                        networkManager.fetchPortraitWallpaper()?.let { fileManager.saveNetworkWallpaper(it, 0, this@MainActivity) }
                        networkManager.fetchLandscapeWallpaper()?.let { fileManager.saveNetworkWallpaper(it, 1, this@MainActivity) }
                    }
                    // 如果开启自定义源且配置了 URL，各抓取一张
                    if (useCustom) {
                        fetchCustomDirect(customPort)?.let { fileManager.saveNetworkWallpaper(it, 0, this@MainActivity) }
                        fetchCustomDirect(customLand)?.let { fileManager.saveNetworkWallpaper(it, 1, this@MainActivity) }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "混合源抓取成功！", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "网络或配置错误，请检查", Toast.LENGTH_SHORT).show() }
                }
            }
        }

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
                Toast.makeText(this, "目标张数已生效", Toast.LENGTH_SHORT).show()
            }
        }

        swAutoRefresh.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_refresh", isChecked).apply()
        }
    }
}
