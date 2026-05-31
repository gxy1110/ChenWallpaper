package com.template

import android.os.Bundle
import android.widget.GridView
import android.widget.ImageView
import android.widget.BaseAdapter
import android.widget.Button
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import coil.load

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        // 👇 绑定我们刚刚在 XML 里写的返回按钮
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val fileManager = FileManager()
        val allFiles = fileManager.getWallpapers(true, this) + fileManager.getWallpapers(false, this)

        val gridView = findViewById<GridView>(R.id.galleryGridView)
        gridView.adapter = object : BaseAdapter() {
            override fun getCount() = allFiles.size
            override fun getItem(position: Int) = allFiles[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val imageView = ImageView(this@GalleryActivity)
                // 👇 使用 MATCH_PARENT 自适应宽度，高度设为 800 像素以适配你的高分屏设备
                imageView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                // 使用 Coil 加载图片
                imageView.load(allFiles[position])
                return imageView
            }
        }
    }
}
