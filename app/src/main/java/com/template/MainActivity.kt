package com.template

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
    private lateinit var webDavContainer: LinearLayout

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
        webDavContainer = findViewById(R.id.webDavContainer)

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
            } else if (key == "webdav_configs") {
                renderWebDavList()
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
                            if (fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)) count++
                            tempFile.delete()
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "成功导入 $count 张新本地壁纸！", Toast.LENGTH_LONG).show() }
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
                            if (file.isDirectory) scanFolder(file) else {
                                val mime = file.type ?: ""
                                val name = file.name?.lowercase() ?: ""
                                if (mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".png")) imageUris.add(file.uri)
                            }
                        }
                    }
                    if (rootDir != null) {
                        scanFolder(rootDir)
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
                                    if (fileManager.importLocalImage(tempFile.absolutePath, this@MainActivity)) count++
                                }
                                tempFile.delete()
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "成功将 $count 张图片并入本地图库！", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply { putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, ChenWallpaperService::class.java)) })
        }
        findViewById<Button>(R.id.btnStartFetch).setOnClickListener {
            prefs.edit().putBoolean("is_fetching_enabled", true).apply()
            FetchManager.startFetching(this)
            Toast.makeText(this, "引擎已启动", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStopFetch).setOnClickListener {
            prefs.edit().putBoolean("is_fetching_enabled", false).apply()
            FetchManager.stopFetching(this)
            Toast.makeText(this, "引擎已停止", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java).apply { putExtra("IS_TRASH", false) }) }
        findViewById<Button>(R.id.btnTrash).setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java).apply { putExtra("IS_TRASH", true) }) }
        findViewById<Button>(R.id.btnImport).setOnClickListener { filePickerLauncher.launch("image/*") }
        findViewById<Button>(R.id.btnImportFolder).setOnClickListener { folderPickerLauncher.launch(null) }
        findViewById<Button>(R.id.btnSaveApiSettings).setOnClickListener {
            prefs.edit().putBoolean("use_builtin", cbBuiltIn.isChecked).putBoolean("use_custom", cbCustom.isChecked).putString("custom_api", etCustomApi.text.toString().trim()).apply()
            Toast.makeText(this, "API 设置已保存", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSaveInterval).setOnClickListener {
            if (etInterval.text.isNotEmpty()) { prefs.edit().putInt("interval", etInterval.text.toString().toInt()).apply(); Toast.makeText(this, "时间已生效", Toast.LENGTH_SHORT).show() }
        }
        findViewById<Button>(R.id.btnSaveTarget).setOnClickListener {
            if (etTarget.text.isNotEmpty()) {
                val t = etTarget.text.toString().toInt()
                prefs.edit().putInt("target_count", t).apply()
                fileManager.shrinkNetworkCache(t, this)
                Toast.makeText(this, "目标张数已生效", Toast.LENGTH_SHORT).show()
            }
        }
        swAutoRefresh.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("auto_refresh", isChecked).apply() }

        findViewById<Button>(R.id.btnAddWebDav).setOnClickListener { showAddWebDavDialog() }
        renderWebDavList()
    }

    private fun renderWebDavList() {
        webDavContainer.removeAllViews()
        val configs = WebDavManager.loadConfigs(this)
        
        for (config in configs) {
            val rootCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(16, 16, 16, 16) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            rootCard.layoutParams = lp

            // 👇 核心修复 1：扩大点击范围，整个头部加上 Padding，并接管全局点击事件
            val header = LinearLayout(this).apply { 
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 16) 
            }
            
            val tvIcon = TextView(this).apply { text = "💽 "; textSize = 20f }
            val tvName = TextView(this).apply { text = config.name; textSize = 16f; setPadding(8,0,0,0); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            val tvStatus = TextView(this).apply { text = if(config.isConnected) "🟢" else "🔴"; setPadding(0,0,16,0) }
            
            val cbEnable = CheckBox(this).apply { isChecked = config.isEnabled }
            cbEnable.setOnCheckedChangeListener { _, isChecked ->
                config.isEnabled = isChecked
                WebDavManager.saveConfigs(this, configs)
            }

            // 👇 核心修复 2：加入独立的编辑按钮
            val btnEdit = Button(this).apply { 
                text = "编辑"
                setBackgroundColor(Color.parseColor("#FF9800"))
                setTextColor(Color.WHITE)
                minimumHeight = 0
                minimumWidth = 0
                setPadding(24, 16, 24, 16)
                textSize = 14f
            }
            val editLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,8,0) }
            btnEdit.layoutParams = editLp
            btnEdit.setOnClickListener { showEditWebDavDialog(config, configs) }

            val btnDelete = Button(this).apply { 
                text = "删除"
                setBackgroundColor(Color.parseColor("#F44336"))
                setTextColor(Color.WHITE)
                minimumHeight = 0
                minimumWidth = 0
                setPadding(24, 16, 24, 16)
                textSize = 14f
            }
            val delLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            btnDelete.layoutParams = delLp
            btnDelete.setOnClickListener {
                WebDavManager.saveConfigs(this, configs.filter { it.id != config.id })
                renderWebDavList()
            }

            header.addView(tvIcon); header.addView(tvName); header.addView(tvStatus); header.addView(cbEnable); header.addView(btnEdit); header.addView(btnDelete)
            rootCard.addView(header)

            val pathContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 16, 0, 0); visibility = View.GONE }
            config.paths.forEach { p ->
                val pRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0,0,0,8) }
                val pText = TextView(this).apply { text = "📂 ${java.net.URLDecoder.decode(p.path.substringAfterLast('/'), "UTF-8").ifEmpty { "/" }}"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                val pCb = CheckBox(this).apply { isChecked = p.isEnabled }
                pCb.setOnCheckedChangeListener { _, isChecked -> p.isEnabled = isChecked; WebDavManager.saveConfigs(this, configs) }
                
                val pDel = Button(this).apply { 
                    text = "X"
                    setBackgroundColor(Color.parseColor("#9E9E9E"))
                    setTextColor(Color.WHITE)
                    minimumHeight = 0
                    minimumWidth = 0
                    setPadding(24, 16, 24, 16)
                }
                pDel.setOnClickListener { config.paths.remove(p); WebDavManager.saveConfigs(this, configs); renderWebDavList() }
                pRow.addView(pText); pRow.addView(pCb); pRow.addView(pDel)
                pathContainer.addView(pRow)
            }

            val btnAddPath = Button(this).apply { 
                text = "🌐 浏览并添加此网盘中的文件夹"
                setBackgroundColor(Color.parseColor("#2196F3"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
            }
            btnAddPath.setOnClickListener { showWebDavBrowser(config, configs) }
            pathContainer.addView(btnAddPath)
            
            // 只要点击这一整行（除了按钮的地方），就会立刻弹开底部的抽屉
            header.setOnClickListener { 
                pathContainer.visibility = if (pathContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE 
            }

            rootCard.addView(pathContainer)
            webDavContainer.addView(rootCard)
        }
    }

    private fun showAddWebDavDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        val etName = EditText(this).apply { hint = "名称 (如: 尘尘的webdav)" }
        val etUrl = EditText(this).apply { hint = "地址 (如: https://.../dav)" }
        val etUser = EditText(this).apply { hint = "账号 (选填)" }
        val etPass = EditText(this).apply { hint = "密码 (选填)" }
        layout.addView(etName); layout.addView(etUrl); layout.addView(etUser); layout.addView(etPass)

        AlertDialog.Builder(this).setTitle("配置 WebDAV 节点").setView(layout).setPositiveButton("保存") { _, _ ->
            if (etName.text.isNotEmpty() && etUrl.text.isNotEmpty()) {
                val configs = WebDavManager.loadConfigs(this)
                if (configs.any { it.name == etName.text.toString() }) { Toast.makeText(this, "名称不能重复", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val newUrl = etUrl.text.toString().trim()
                configs.add(WebDavConfig(name = etName.text.toString().trim(), url = if(newUrl.endsWith("/")) newUrl else "$newUrl/", user = etUser.text.toString().trim(), pass = etPass.text.toString().trim()))
                WebDavManager.saveConfigs(this, configs)
                renderWebDavList()
            }
        }.setNegativeButton("取消", null).show()
    }

    // 👇 核心修复 3：全新的编辑逻辑，修改后会自动重置红绿灯状态要求重新检测
    private fun showEditWebDavDialog(config: WebDavConfig, configs: List<WebDavConfig>) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        val etName = EditText(this).apply { hint = "名称 (如: 尘尘的webdav)"; setText(config.name) }
        val etUrl = EditText(this).apply { hint = "地址 (如: https://.../dav)"; setText(config.url) }
        val etUser = EditText(this).apply { hint = "账号 (选填)"; setText(config.user) }
        val etPass = EditText(this).apply { hint = "密码 (选填)"; setText(config.pass) }
        layout.addView(etName); layout.addView(etUrl); layout.addView(etUser); layout.addView(etPass)

        AlertDialog.Builder(this).setTitle("编辑 WebDAV 节点").setView(layout).setPositiveButton("保存") { _, _ ->
            if (etName.text.isNotEmpty() && etUrl.text.isNotEmpty()) {
                val newName = etName.text.toString().trim()
                if (newName != config.name && configs.any { it.name == newName }) { 
                    Toast.makeText(this, "名称不能重复", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton 
                }
                val newUrl = etUrl.text.toString().trim()
                config.name = newName
                config.url = if(newUrl.endsWith("/")) newUrl else "$newUrl/"
                config.user = etUser.text.toString().trim()
                config.pass = etPass.text.toString().trim()
                
                config.isConnected = false // 配置已变，重置为红灯等待管家检测
                
                WebDavManager.saveConfigs(this, configs)
                renderWebDavList()
            }
        }.setNegativeButton("取消", null).show()
    }

    private fun showWebDavBrowser(config: WebDavConfig, configs: List<WebDavConfig>) {
        var currentUrl = config.url
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        val tvPath = TextView(this).apply { 
            setPadding(32,32,32,16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val listView = ListView(this)
        layout.addView(tvPath); layout.addView(listView)

        val dialog = AlertDialog.Builder(this).setTitle("浏览网络文件夹").setView(layout).setPositiveButton("选择当前目录") { _, _ ->
            if (!config.paths.any { it.path == currentUrl }) config.paths.add(WebDavPath(currentUrl))
            WebDavManager.saveConfigs(this, configs)
            renderWebDavList()
            Toast.makeText(this, "已添加抓取路径！", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("取消", null).show()

        fun loadUrl(url: String) {
            tvPath.text = "当前: " + java.net.URLDecoder.decode(url.removePrefix(config.url), "UTF-8").ifEmpty { "/" }
            CoroutineScope(Dispatchers.IO).launch {
                val items = WebDavManager.listDirectory(config, url)
                withContext(Dispatchers.Main) {
                    if (items != null) {
                        val displayList = mutableListOf<WebDavItem>()
                        if (url != config.url) displayList.add(WebDavItem(url.substringBeforeLast('/', url.removeSuffix("/").substringBeforeLast('/') + "/"), true, ".. (返回上级)"))
                        displayList.addAll(items.filter { it.isFolder })
                        
                        listView.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, displayList.map { "📁 ${it.name}" })
                        listView.setOnItemClickListener { _, _, position, _ -> loadUrl(displayList[position].href) }
                    } else Toast.makeText(this@MainActivity, "连接失败或目录为空", Toast.LENGTH_SHORT).show()
                }
            }
        }
        loadUrl(currentUrl)
    }
}
