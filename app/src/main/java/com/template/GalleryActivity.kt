package com.template

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
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
        val adapter = object : BaseAdapter() {
            override fun getCount() = currentFiles.size
            override fun getItem(position: Int) = currentFiles[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val imageView = ImageView(this@GalleryActivity)
                imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.load(currentFiles[position])
                return imageView
            }
        }
        gridView.adapter = adapter

        gridView.setOnItemClickListener { _, _, position, _ ->
            currentDetailFile = currentFiles[position]
            findViewById<ImageView>(R.id.detailImage).load(currentDetailFile)
            detailContainer.visibility = View.VISIBLE
        }

        val tabNetPort = findViewById<Button>(R.id.tabNetPort)
        val tabNetLand = findViewById<Button>(R.id.tabNetLand)
        val tabLocPort = findViewById<Button>(R.id.tabLocPort)
        val tabLocLand = findViewById<Button>(R.id.tabLocLand)
        val tabButtons = listOf(tabNetPort, tabNetLand, tabLocPort, tabLocLand)

        // 核心：动态更新所有 Tab 的数量显示
        fun updateTabsAndLoad(type: Int) {
            currentType = type
            tabNetPort.text = "网络竖屏 (${fileManager.getWallpapers(0, isTrashMode, this).size})"
            tabNetLand.text = "网络横屏 (${fileManager.getWallpapers(1, isTrashMode, this).size})"
            tabLocPort.text = "本地竖屏 (${fileManager.getWallpapers(2, isTrashMode, this).size})"
            tabLocLand.text = "本地横屏 (${fileManager.getWallpapers(3, isTrashMode, this).size})"

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

        tabNetPort.setOnClickListener { updateTabsAndLoad(0) }
        tabNetLand.setOnClickListener { updateTabsAndLoad(1) }
        tabLocPort.setOnClickListener { updateTabsAndLoad(2) }
        tabLocLand.setOnClickListener { updateTabsAndLoad(3) }

        // 批量恢复逻辑
        btnBatchRestore.setOnClickListener {
            if (currentFiles.isNotEmpty()) {
                currentFiles.forEach { file -> fileManager.restoreFromTrash(file, currentType, this) }
                Toast.makeText(this, "批量恢复成功", Toast.LENGTH_SHORT).show()
                updateTabsAndLoad(currentType)
            }
        }

        // 详情操作 1: 设壁纸 或 恢复单张
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

        // 详情操作 2: 移入回收站 或 永久删除
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

        // 初始化
        updateTabsAndLoad(0)
    }
}
