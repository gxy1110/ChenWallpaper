package com.template

import android.app.WallpaperManager
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import coil.load
import java.io.File

class GalleryActivity : ComponentActivity() {
    private lateinit var fileManager: FileManager
    private var currentFiles = listOf<File>()
    private var currentDetailFile: File? = null
    private var currentType = 0
    private var isTrashMode = false

    // 新增：批量选择状态
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        fileManager = FileManager()
        isTrashMode = intent.getBooleanExtra("IS_TRASH", false)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val btnBatchRestore = findViewById<Button>(R.id.btnBatchRestore)
        val btnSetWall = findViewById<Button>(R.id.btnSetWall)
        val btnDelete = findViewById<Button>(R.id.btnDelete)
        val detailContainer = findViewById<FrameLayout>(R.id.detailContainer)
        val batchActionContainer = findViewById<LinearLayout>(R.id.batchActionContainer)
        
        tvTitle.text = if (isTrashMode) "回收站" else "已缓存图库"
        if (isTrashMode) {
            btnBatchRestore.visibility = View.VISIBLE
            btnSetWall.text = "恢复到图库"
            btnDelete.text = "永久删除"
        } else {
            btnSetWall.text = "设为系统壁纸"
            btnDelete.text = "移至回收站"
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val gridView = findViewById<GridView>(R.id.galleryGridView)
        
        // 更新 UI 界面的刷新函数
        fun updateBatchUI() {
            if (isSelectionMode) {
                batchActionContainer.visibility = View.VISIBLE
                findViewById<Button>(R.id.btnSaveBatch).text = "保存选中(${selectedFiles.size})"
            } else {
                batchActionContainer.visibility = View.GONE
                selectedFiles.clear()
            }
            adapter.notifyDataSetChanged()
        }

        adapter = object : BaseAdapter() {
            override fun getCount() = currentFiles.size
            override fun getItem(position: Int) = currentFiles[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val file = currentFiles[position]
                
                // 使用 FrameLayout 组合原图与选中状态的遮罩层
                val frameLayout = FrameLayout(this@GalleryActivity)
                frameLayout.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
                
                val imageView = ImageView(this@GalleryActivity)
                imageView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.load(file)
                frameLayout.addView(imageView)

                // 批量选择模式下的 UI 渲染
                if (isSelectionMode) {
                    val isSelected = selectedFiles.contains(file)
                    
                    val overlay = View(this@GalleryActivity)
                    overlay.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    overlay.setBackgroundColor(if (isSelected) Color.parseColor("#662196F3") else Color.parseColor("#44000000"))
                    frameLayout.addView(overlay)
                    
                    val checkIcon = TextView(this@GalleryActivity)
                    checkIcon.text = if (isSelected) "✅" else "⚪"
                    checkIcon.textSize = 24f
                    val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.TOP or Gravity.END
                        setMargins(16, 16, 16, 16)
                    }
                    checkIcon.layoutParams = params
                    frameLayout.addView(checkIcon)
                }
                return frameLayout
            }
        }
        gridView.adapter = adapter

        // 长按进入批量选择模式
        gridView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isSelectionMode) {
                isSelectionMode = true
                selectedFiles.add(currentFiles[position])
                updateBatchUI()
            }
            true
        }

        // 短按逻辑（选择模式 vs 预览模式）
        gridView.setOnItemClickListener { _, _, position, _ ->
            if (isSelectionMode) {
                val file = currentFiles[position]
                if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                updateBatchUI()
            } else {
                currentDetailFile = currentFiles[position]
                findViewById<ImageView>(R.id.detailImage).load(currentDetailFile)
                detailContainer.visibility = View.VISIBLE
            }
        }

        // --- 保存到相册核心逻辑 ---
        fun saveToAlbum(filesToSave: List<File>) {
            if (filesToSave.isEmpty()) return
            Toast.makeText(this, "正在保存 ${filesToSave.size} 张图片...", Toast.LENGTH_SHORT).show()
            
            Thread {
                var successCount = 0
                for (file in filesToSave) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "ChenWall_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            // 针对现代安卓版本(Android 10+)，自动归类到 Pictures/ChenWallpaper 文件夹
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChenWallpaper")
                            }
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                            successCount++
                        }
                        Thread.sleep(10) // 防止文件名生成过快重复
                    } catch (e: Exception) { e.printStackTrace() }
                }
                runOnUiThread {
                    Toast.makeText(this, "成功保存 $successCount 张图片到相册！", Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        // --- 批量操作栏按钮事件 ---
        findViewById<Button>(R.id.btnCancelBatch).setOnClickListener {
            isSelectionMode = false
            updateBatchUI()
        }
        findViewById<Button>(R.id.btnSelectAll).setOnClickListener {
            selectedFiles.addAll(currentFiles)
            updateBatchUI()
        }
        findViewById<Button>(R.id.btnSaveBatch).setOnClickListener {
            saveToAlbum(selectedFiles.toList())
            isSelectionMode = false
            updateBatchUI()
        }

        // --- 原有逻辑代码 ---
        val tabButtons = listOf<Button>(
            findViewById(R.id.tabNetPort), findViewById(R.id.tabNetLand), 
            findViewById(R.id.tabLocPort), findViewById(R.id.tabLocLand)
        )

        fun updateTabsAndLoad(type: Int) {
            currentType = type
            // 切换分类时，自动退出选择模式
            isSelectionMode = false
            selectedFiles.clear()
            updateBatchUI()
            
            tabButtons[0].text = "网络竖屏 (${fileManager.getWallpapers(0, isTrashMode, this).size})"
            tabButtons[1].text = "网络横屏 (${fileManager.getWallpapers(1, isTrashMode, this).size})"
            tabButtons[2].text = "本地竖屏 (${fileManager.getWallpapers(2, isTrashMode, this).size})"
            tabButtons[3].text = "本地横屏 (${fileManager.getWallpapers(3, isTrashMode, this).size})"

            for (i in tabButtons.indices) {
                if (i == type) {
                    tabButtons[i].setBackgroundColor(Color.parseColor("#2196F3"))
                    tabButtons[i].setTextColor(Color.WHITE)
                } else {
                    tabButtons[i].setBackgroundColor(Color.parseColor("#E0E0E0"))
                    tabButtons[i].setTextColor(Color.BLACK)
                }
            }

            currentFiles = fileManager.getWallpapers(type, isTrashMode, this)
            adapter.notifyDataSetChanged()
        }

        tabButtons[0].setOnClickListener { updateTabsAndLoad(0) }
        tabButtons[1].setOnClickListener { updateTabsAndLoad(1) }
        tabButtons[2].setOnClickListener { updateTabsAndLoad(2) }
        tabButtons[3].setOnClickListener { updateTabsAndLoad(3) }

        btnBatchRestore.setOnClickListener {
            if (currentFiles.isNotEmpty()) {
                currentFiles.forEach { file -> fileManager.restoreFromTrash(file, currentType, this) }
                Toast.makeText(this, "批量恢复成功", Toast.LENGTH_SHORT).show()
                updateTabsAndLoad(currentType)
            }
        }

        // 单张操作：保存到相册
        findViewById<Button>(R.id.btnSaveDetail).setOnClickListener {
            currentDetailFile?.let { file -> saveToAlbum(listOf(file)) }
        }

        btnSetWall.setOnClickListener {
            currentDetailFile?.let { file ->
                if (isTrashMode) {
                    fileManager.restoreFromTrash(file, currentType, this)
                    Toast.makeText(this, "已恢复", Toast.LENGTH_SHORT).show()
                    detailContainer.visibility = View.GONE
                    updateTabsAndLoad(currentType)
                } else {
                    Toast.makeText(this, "正在设置系统壁纸...", Toast.LENGTH_SHORT).show()
                    Thread {
                        try {
                            WallpaperManager.getInstance(this).setBitmap(BitmapFactory.decodeFile(file.absolutePath))
                            runOnUiThread { Toast.makeText(this, "设置成功！", Toast.LENGTH_SHORT).show() }
                        } catch (e: Exception) { e.printStackTrace() }
                    }.start()
                }
            }
        }

        btnDelete.setOnClickListener {
            currentDetailFile?.let { file ->
                if (isTrashMode) {
                    file.delete()
                    Toast.makeText(this, "彻底删除", Toast.LENGTH_SHORT).show()
                } else {
                    fileManager.moveToTrash(file, currentType, this)
                    Toast.makeText(this, "已移至回收站", Toast.LENGTH_SHORT).show()
                }
                detailContainer.visibility = View.GONE
                updateTabsAndLoad(currentType)
            }
        }

        findViewById<Button>(R.id.btnCloseDetail).setOnClickListener { detailContainer.visibility = View.GONE }

        updateTabsAndLoad(0)
    }
}
