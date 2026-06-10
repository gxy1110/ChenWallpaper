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
import java.net.URI

class MainActivity : ComponentActivity() {
    private lateinit var webDavContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fileManager = FileManager()
        val prefs = getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)

        val etInterval = findViewById<EditText>(R.id.etInterval)
        val swAutoRefresh = findViewById<Switch>(R.id.swAutoRefresh)
        val cbBuiltIn = findViewById<CheckBox>(R.id.cbBuiltIn)
        val cbCustom = findViewById<CheckBox>(R.id.cbCustom)
        val etCustomApi = findViewById<EditText>(R.id.etCustomApi)
        val etTargetApi = findViewById<EditText>(R.id.etTargetApi)
        val etTargetWebDav = findViewById<EditText>(R.id.etTargetWebDav)
        val tvFetchStatus = findViewById<TextView>(R.id.tvFetchStatus)
        webDavContainer = findViewById(R.id.webDavContainer)

        // 初始化数据回显
        etInterval.setText(prefs.getInt("interval", 10).toString())
        swAutoRefresh.isChecked = prefs.getBoolean("auto_refresh", false)
        cbBuiltIn.isChecked = prefs.getBoolean("use_builtin", true)
        cbCustom.isChecked = prefs.getBoolean("use_custom", false)
        etCustomApi.setText(prefs.getString("custom_api", ""))
        etTargetApi.setText(prefs.getInt("target_count_api", 100).toString())
        etTargetWebDav.setText(prefs.getInt("target_count_webdav", 100).toString())

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
        
        // 👇 核心升级：一键保存全局配置拦截器
        findViewById<Button>(R.id.btnSaveAll).setOnClickListener {
            val apiTarget = if (etTargetApi.text.isNotEmpty()) etTargetApi.text.toString().toInt() else 100
            val davTarget = if (etTargetWebDav.text.isNotEmpty()) etTargetWebDav.text.toString().toInt() else 100
            
            // 写入 SharedPreferences
            prefs.edit()
                .putBoolean("use_builtin", cbBuiltIn.isChecked)
                .putBoolean("use_custom", cbCustom.isChecked)
                .putString("custom_api", etCustomApi.text.toString().trim())
                .putInt("target_count_api", apiTarget)
                .putInt("target_count_webdav", davTarget)
                .apply()
            
            // 触发容量控制，根据新设置的保留容量清除多余图片
            fileManager.shrinkNetworkCache(apiTarget, davTarget, this)
            
            Toast.makeText(this, "所有配置已手动提交保存！", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSaveInterval).setOnClickListener {
            if (etInterval.text.isNotEmpty()) { prefs.edit().putInt("interval", etInterval.text.toString().toInt()).apply(); Toast.makeText(this, "时间已生效", Toast.LENGTH_SHORT).show() }
        }
        
        swAutoRefresh.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("auto_refresh", isChecked).apply() }

        findViewById<Button>(R.id.btnAddWebDav).setOnClickListener { showAddWebDavDialog() }
        
        // 初始渲染 WebDAV 列表
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

            val btnEdit = Button(this).apply { 
                text = "编辑"
                setBackgroundColor(Color.parseColor("#FF9800"))
                setTextColor(Color.WHITE)
                minimumHeight = 0; minimumWidth = 0
                setPadding(24, 16, 24, 16)
                textSize = 14f
            }
            btnEdit.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,8,0) }
            btnEdit.setOnClickListener { showEditWebDavDialog(config, configs) }

            val btnDelete = Button(this).apply { 
                text = "删除"
                setBackgroundColor(Color.parseColor("#F44336"))
                setTextColor(Color.WHITE)
                minimumHeight = 0; minimumWidth = 0
                setPadding(24, 16, 24, 16)
                textSize = 14f
            }
            btnDelete.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            btnDelete.setOnClickListener {
                WebDavManager.saveConfigs(this, configs.filter { it.id != config.id })
                renderWebDavList()
            }

            header.addView(tvIcon); header.addView(tvName); header.addView(tvStatus); header.addView(cbEnable); header.addView(btnEdit); header.addView(btnDelete)
            rootCard.addView(header)

            val pathContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 16, 0, 0); visibility = View.GONE }
            
            config.paths.forEach { p ->
                val pRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0,0,0,8) }
                
                val decodedPath = try {
                    val fullPath = URI(p.path).path
                    val rootPath = URI(config.url).path
                    val relative = fullPath.removePrefix(rootPath)
                    java.net.URLDecoder.decode(if (relative.startsWith("/")) relative else "/$relative", "UTF-8")
                } catch (e: Exception) { p.path }
                
                val pText = TextView(this).apply { 
                    text = "📂 $decodedPath"
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) 
                }
                
                val pCb = CheckBox(this).apply { isChecked = p.isEnabled }
                pCb.setOnCheckedChangeListener { _, isChecked -> p.isEnabled = isChecked; WebDavManager.saveConfigs(this, configs) }
                
                val pEditBtn = Button(this).apply { 
                    text = "📝"
                    setBackgroundColor(Color.parseColor("#2196F3"))
                    setTextColor(Color.WHITE)
                    minimumHeight = 0; minimumWidth = 0
                    setPadding(24, 16, 24, 16)
                }
                pEditBtn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,8,0) }
                pEditBtn.setOnClickListener {
                    showWebDavBrowser(config, configs, p)
                }

                val pDel = Button(this).apply { 
                    text = "X"
                    setBackgroundColor(Color.parseColor("#9E9E9E"))
                    setTextColor(Color.WHITE)
                    minimumHeight = 0; minimumWidth = 0
                    setPadding(24, 16, 24, 16)
                }
                pDel.setOnClickListener { config.paths.remove(p); WebDavManager.saveConfigs(this, configs); renderWebDavList() }
                
                pRow.addView(pText); pRow.addView(pCb); pRow.addView(pEditBtn); pRow.addView(pDel)
                pathContainer.addView(pRow)
            }

            val btnAddPath = Button(this).apply { 
                text = "🌐 浏览并添加此网盘中的文件夹"
                setBackgroundColor(Color.parseColor("#2196F3"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
            }
            btnAddPath.setOnClickListener { showWebDavBrowser(config, configs, null) }
            pathContainer.addView(btnAddPath)
            
            header.setOnClickListener { pathContainer.visibility = if (pathContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE }

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
                config.isConnected = false 
                WebDavManager.saveConfigs(this, configs)
                renderWebDavList()
            }
        }.setNegativeButton("取消", null).show()
    }

    private fun showWebDavBrowser(config: WebDavConfig, configs: List<WebDavConfig>, pathToEdit: WebDavPath?) {
        var currentUrl = if (pathToEdit != null) pathToEdit.path else config.url
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        val tvPath = TextView(this).apply { 
            setPadding(32,32,32,16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val listView = ListView(this)
        layout.addView(tvPath); layout.addView(listView)

        val dialog = AlertDialog.Builder(this).setTitle("浏览网络文件夹").setView(layout).setPositiveButton("选择当前目录") { _, _ ->
            if (pathToEdit != null) {
                pathToEdit.path = currentUrl
                Toast.makeText(this, "修改路径成功！", Toast.LENGTH_SHORT).show()
            } else {
                if (!config.paths.any { it.path == currentUrl }) config.paths.add(WebDavPath(currentUrl))
                Toast.makeText(this, "已添加抓取路径！", Toast.LENGTH_SHORT).show()
            }
            WebDavManager.saveConfigs(this, configs)
            renderWebDavList()
        }.setNegativeButton("取消", null).show()

        fun loadUrl(url: String) {
            currentUrl = url 

            val decodedPath = try {
                val fullPath = URI(url).path
                val rootPath = URI(config.url).path
                val relative = fullPath.removePrefix(rootPath)
                java.net.URLDecoder.decode(if (relative.startsWith("/")) relative else "/$relative", "UTF-8")
            } catch (e: Exception) { url }

            tvPath.text = "当前: $decodedPath"
            
            CoroutineScope(Dispatchers.IO).launch {
                val items = WebDavManager.listDirectory(config, url)
                withContext(Dispatchers.Main) {
                    if (items != null) {
                        val displayList = mutableListOf<WebDavItem>()
                        if (url != config.url) displayList.add(WebDavItem(url.substringBeforeLast('/', url.removeSuffix("/").substringBeforeLast('/') + "/"), true, ".. (返回上级)"))
                        
                        displayList.addAll(items.sortedBy { !it.isFolder })
                        
                        listView.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, displayList.map { 
                            if (it.isFolder) "📁 ${it.name}" else "🖼️ ${it.name}" 
                        })
                        listView.setOnItemClickListener { _, _, position, _ -> 
                            val item = displayList[position]
                            if (item.isFolder) {
                                loadUrl(item.href) 
                            } else {
                                Toast.makeText(this@MainActivity, "请点击底部的“选择当前目录”来抓取整个文件夹", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else Toast.makeText(this@MainActivity, "连接失败或目录为空", Toast.LENGTH_SHORT).show()
                }
            }
        }
        loadUrl(currentUrl)
    }
}
