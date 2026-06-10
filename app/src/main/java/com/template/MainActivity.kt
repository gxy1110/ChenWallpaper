package com.template

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
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
        
        val cbBuiltIn = findViewById<CheckBox>(R.id.cbBuiltIn)
        val cbCustom = findViewById<CheckBox>(R.id.cbCustom)
        val etCustomApi = findViewById<EditText>(R.id.etCustomApi)
        val tvFetchStatus = findViewById<TextView>(R.id.tvFetchStatus)

        etInterval.setText(prefs.getInt("interval", 10).toString())
        etTarget.setText(prefs.getInt("target_count", 100).toString())
        swAutoRefresh.isChecked = prefs.getBoolean("auto_refresh", false)
        
        cbBuiltIn.isChecked = prefs.getBoolean("use_builtin", true)
        cbCustom.isChecked = prefs.getBoolean("use_custom", false)
        etCustomApi.setText(prefs.getString("custom_api", ""))

        val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs: SharedPreferences?, key: String? ->
            if (key == "fetch_status_running") {
                val isRunning = sharedPrefs?.getBoolean(key, false) ?: false
                if (isRunning) {
                    tvFetchStatus.text = "🟢 获取状态：正在后台自动抓取..."
                    tvFetchStatus.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    tvFetchStatus.text = "🔴 获取状态：已停止"
                    tvFetchStatus.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        prefsListener.onSharedPreferenceChanged(prefs, "fetch_status_running")

        val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                Toast.makeText(this, "正在后台提取图片并排查重复项...", Toast.LENGTH_SHORT).show()
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
                            
                            // 👇 根据底层的去重结果，统计真实导入成功的数量
                            if (fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)) {
                                count++
                            }
                            tempFile.delete()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "过滤去重后，成功导入 $count 张新本地壁纸！", Toast.LENGTH_LONG).show() }
                }
            }
        }

        val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri != null) {
                Toast.makeText(this, "正在深度扫描并排查重复项，请稍候...", Toast.LENGTH_LONG).show()
                
                CoroutineScope(Dispatchers.IO).launch {
                    val rootDir = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                    val imageUris = mutableListOf<android.net.Uri>()

                    fun scanFolder(dir: DocumentFile) {
                        dir.listFiles().forEach { file ->
                            if (file.isDirectory) {
                                scanFolder(file)
                            } else {
                                val mime = file.type ?: ""
                                val name = file.name?.lowercase() ?: ""
                                if (mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg")) {
                                    imageUris.add(file.uri)
                                }
                            }
                        }
                    }

                    if (rootDir != null) {
                        scanFolder(rootDir)
                        
                        if (imageUris.isEmpty()) {
                            withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "未在该目录内找到任何图片！", Toast.LENGTH_SHORT).show() }
                            return@launch
                        }

                        var count = 0
                        for (imgUri in imageUris) {
                            try {
                                val inputStream = contentResolver.openInputStream(imgUri)
                                val tempFile = File(cacheDir, "temp_import_${System.currentTimeMillis()}_$count.jpg")
                                val outputStream = FileOutputStream(tempFile)
                                inputStream?.copyTo(outputStream)
                                inputStream?.close()
                                outputStream.close()

                                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, opts)
                                if (opts.outWidth > 0 && opts.outHeight > 0) {
                                    // 👇 根据底层的去重结果，统计真实导入成功的数量
                                    if (fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)) {
                                        count++
                                    }
                                }
                                tempFile.delete()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "过滤去重后，成功将 $count 张图片并入本地图库！", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply { putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, ChenWallpaperService::class.java)) })
        }

        findViewById<Button>(R.id.btnStartFetch).setOnClickListener {
            val useBuiltIn = cbBuiltIn.isChecked
            val useCustom = cbCustom.isChecked
            if (!useBuiltIn && (!useCustom || etCustomApi.text.toString().trim().isEmpty())) {
                Toast.makeText(this, "拦截：请至少开启一个有效图源！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putBoolean("is_fetching_enabled", true).apply()
            FetchManager.startFetching(this)
            Toast.makeText(this, "引擎已启动，正在后台为您抓取壁纸", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStopFetch).setOnClickListener {
            prefs.edit().putBoolean("is_fetching_enabled", false).apply()
            FetchManager.stopFetching(this)
            Toast.makeText(this, "已彻底停止网络抓取任务", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java).apply { putExtra("IS_TRASH", false) })
        }

        findViewById<Button>(R.id.btnTrash).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java).apply { putExtra("IS_TRASH", true) })
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener { 
            filePickerLauncher.launch("image/*") 
        }

        findViewById<Button>(R.id.btnImportFolder).setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        findViewById<Button>(R.id.btnSaveApiSettings).setOnClickListener {
            prefs.edit()
                .putBoolean("use_builtin", cbBuiltIn.isChecked)
                .putBoolean("use_custom", cbCustom.isChecked)
                .putString("custom_api", etCustomApi.text.toString().trim())
                .apply()
            Toast.makeText(this, "API 及多源策略已保存生效！", Toast.LENGTH_SHORT).show()
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
