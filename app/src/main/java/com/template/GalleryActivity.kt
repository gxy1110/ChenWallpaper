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
    private lateinit var tabButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        fileManager = FileManager()
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val gridView = findViewById<GridView>(R.id.galleryGridView)
        val detailContainer = findViewById<FrameLayout>(R.id.detailContainer)
        val detailImage = findViewById<ImageView>(R.id.detailImage)

        // 收集分类按钮组
        val tabNetPort = findViewById<Button>(R.id.tabNetPort)
        val tabNetLand = findViewById<Button>(R.id.tabNetLand)
        val tabLocPort = findViewById<Button>(R.id.tabLocPort)
        val tabLocLand = findViewById<Button>(R.id.tabLocLand)
        tabButtons = listOf(tabNetPort, tabNetLand, tabLocPort, tabLocLand)

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
            detailImage.load(currentDetailFile)
            detailContainer.visibility = View.VISIBLE
        }

        // 核心：加载分类并渲染选项卡颜色
        fun loadCategory(type: Int) {
            currentFiles = fileManager.getWallpapers(type, this)
            adapter.notifyDataSetChanged()
            
            // 选中的按钮变成蓝色高亮，未选中的变成灰色
            for (i in tabButtons.indices) {
                if (i == type) {
                    tabButtons[i].setBackgroundColor(Color.parseColor("#2196F3"))
                    tabButtons[i].setTextColor(Color.WHITE)
                } else {
                    tabButtons[i].setBackgroundColor(Color.parseColor("#E0E0E0"))
                    tabButtons[i].setTextColor(Color.BLACK)
                }
            }
        }

        tabNetPort.setOnClickListener { loadCategory(0) }
        tabNetLand.setOnClickListener { loadCategory(1) }
        tabLocPort.setOnClickListener { loadCategory(2) }
        tabLocLand.setOnClickListener { loadCategory(3) }

        findViewById<Button>(R.id.btnSetWall).setOnClickListener {
            currentDetailFile?.let { file ->
                Toast.makeText(this, "正在手动覆盖系统壁纸...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        WallpaperManager.getInstance(this).setBitmap(bitmap)
                        runOnUiThread { Toast.makeText(this, "设置成功！", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { e.printStackTrace() }
                }.start()
            }
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            currentDetailFile?.let { file ->
                file.delete()
                detailContainer.visibility = View.GONE
                currentFiles = currentFiles.filter { it.absolutePath != file.absolutePath }
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "已彻底移出缓存池", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnCloseDetail).setOnClickListener { detailContainer.visibility = View.GONE }

        // 默认初始化进入网络竖屏
        loadCategory(0)
    }
}
