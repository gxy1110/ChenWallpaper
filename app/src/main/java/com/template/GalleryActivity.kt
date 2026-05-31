package com.template

import android.app.WallpaperManager
import android.graphics.BitmapFactory
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        fileManager = FileManager()
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val gridView = findViewById<GridView>(R.id.galleryGridView)
        val detailContainer = findViewById<FrameLayout>(R.id.detailContainer)
        val detailImage = findViewById<ImageView>(R.id.detailImage)

        // 适配器逻辑
        val adapter = object : BaseAdapter() {
            override fun getCount() = currentFiles.size
            override fun getItem(position: Int) = currentFiles[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val imageView = ImageView(this@GalleryActivity)
                imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.load(currentFiles[position])
                return imageView
            }
        }
        gridView.adapter = adapter

        // 点击网格图片，打开沉浸式预览
        gridView.setOnItemClickListener { _, _, position, _ ->
            currentDetailFile = currentFiles[position]
            detailImage.load(currentDetailFile)
            detailContainer.visibility = View.VISIBLE
        }

        // 分类按钮点击逻辑
        fun loadCategory(type: Int) {
            currentFiles = fileManager.getWallpapers(type, this)
            adapter.notifyDataSetChanged()
        }
        findViewById<Button>(R.id.tabNetPort).setOnClickListener { loadCategory(0) }
        findViewById<Button>(R.id.tabNetLand).setOnClickListener { loadCategory(1) }
        findViewById<Button>(R.id.tabLocPort).setOnClickListener { loadCategory(2) }
        findViewById<Button>(R.id.tabLocLand).setOnClickListener { loadCategory(3) }

        // 详情页：设为系统壁纸
        findViewById<Button>(R.id.btnSetWall).setOnClickListener {
            currentDetailFile?.let { file ->
                Toast.makeText(this, "正在设置系统壁纸...", Toast.LENGTH_SHORT).show()
                Thread {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    WallpaperManager.getInstance(this).setBitmap(bitmap)
                    runOnUiThread { Toast.makeText(this, "设置成功！", Toast.LENGTH_SHORT).show() }
                }.start()
            }
        }

        // 详情页：删除图片
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            currentDetailFile?.let { file ->
                file.delete()
                detailContainer.visibility = View.GONE
                // 刷新当前列表
                currentFiles = currentFiles.filter { it.absolutePath != file.absolutePath }
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
        }

        // 详情页：关闭预览
        findViewById<Button>(R.id.btnCloseDetail).setOnClickListener {
            detailContainer.visibility = View.GONE
        }

        // 初始加载网络竖屏
        loadCategory(0)
    }
}
