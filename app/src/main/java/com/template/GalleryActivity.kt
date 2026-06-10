package com.template

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import coil.load
import java.io.File

class GalleryActivity : ComponentActivity() {
    private lateinit var fileManager: FileManager
    private var currentFiles = listOf<File>()
    private var currentDetailFile: File? = null
    private var currentType = 0
    private var isTrashMode = false

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    private lateinit var adapter: BaseAdapter

    @SuppressLint("ClickableViewAccessibility")
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
        val detailImage = findViewById<ImageView>(R.id.detailImage)
        val touchOverlay = findViewById<View>(R.id.touchOverlay)
        val batchActionContainer = findViewById<HorizontalScrollView>(R.id.batchActionContainer)
        
        val btnSaveBatch = findViewById<Button>(R.id.btnSaveBatch)
        val btnDeleteBatch = findViewById<Button>(R.id.btnDeleteBatch)
        
        tvTitle.text = if (isTrashMode) "回收站" else "已缓存图库"
        if (isTrashMode) {
            btnBatchRestore.visibility = View.VISIBLE
            btnSetWall.text = "恢复到图库"
            btnDelete.text = "永久删除"
        } else {
            btnSetWall.text = "设为系统壁纸"
            btnDelete.text = "移至回收站"
        }

        fun updateBatchUI() {
            if (isSelectionMode) {
                batchActionContainer.visibility = View.VISIBLE
                btnSaveBatch.text = "保存(${selectedFiles.size})"
                btnDeleteBatch.text = if (isTrashMode) "彻底删除(${selectedFiles.size})" else "移至回收站(${selectedFiles.size})"
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
                val frameLayout = FrameLayout(this@GalleryActivity)
                frameLayout.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
                
                val imageView = ImageView(this@GalleryActivity)
                imageView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.load(file)
                frameLayout.addView(imageView)

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
        val gridView = findViewById<GridView>(R.id.galleryGridView)
        gridView.adapter = adapter

        var scaleFactor = 1f
        var translateX = 0f
        var translateY = 0f
        detailImage.pivotX = 0f
        detailImage.pivotY = 0f

        fun resetZoom() {
            scaleFactor = 1f
            translateX = 0f
            translateY = 0f
            detailImage.scaleX = 1f
            detailImage.scaleY = 1f
            detailImage.translationX = 0f
            detailImage.translationY = 0f
        }

        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (detailContainer.visibility == View.VISIBLE) {
                    detailContainer.visibility = View.GONE
                    resetZoom() 
                } else if (isSelectionMode) {
                    isSelectionMode = false
                    updateBatchUI() 
                } else {
                    finish() 
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        findViewById<Button>(R.id.btnBack).setOnClickListener { backPressedCallback.handleOnBackPressed() }

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1f, 10f) 
                val scaleChange = scaleFactor / prevScale
                translateX = detector.focusX - (detector.focusX - translateX) * scaleChange
                translateY = detector.focusY - (detector.focusY - translateY) * scaleChange
                detailImage.scaleX = scaleFactor
                detailImage.scaleY = scaleFactor
                detailImage.translationX = translateX
                detailImage.translationY = translateY
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (scaleFactor > 1f && !scaleDetector.isInProgress) { 
                    translateX -= distanceX
                    translateY -= distanceY
                    detailImage.translationX = translateX
                    detailImage.translationY = translateY
                }
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scaleFactor > 1f) resetZoom() else {
                    scaleFactor = 3f
                    translateX = e.x - (e.x - translateX) * 3f
                    translateY = e.y - (e.y - translateY) * 3f
                    detailImage.scaleX = scaleFactor
                    detailImage.scaleY = scaleFactor
                    detailImage.translationX = translateX
                    detailImage.translationY = translateY
                }
                return true
            }
        })

        touchOverlay.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && scaleFactor <= 1f) resetZoom()
            true
        }

        gridView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isSelectionMode) {
                isSelectionMode = true
                selectedFiles.add(currentFiles[position])
                updateBatchUI()
            }
            true
        }

        gridView.setOnItemClickListener { _, _, position, _ ->
            if (isSelectionMode) {
                val file = currentFiles[position]
                if (selectedFiles.contains(file)) selectedFiles.remove(file) else selectedFiles.add(file)
                updateBatchUI()
            } else {
                currentDetailFile = currentFiles[position]
                resetZoom() 
                detailImage.load(currentDetailFile)
                detailContainer.visibility = View.VISIBLE
            }
        }

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
                        Thread.sleep(10)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                runOnUiThread { Toast.makeText(this, "成功保存 $successCount 张图片到相册！", Toast.LENGTH_SHORT).show() }
            }.start()
        }

        findViewById<Button>(R.id.btnCancelBatch).setOnClickListener { isSelectionMode = false; updateBatchUI() }
        findViewById<Button>(R.id.btnSelectAll).setOnClickListener { selectedFiles.addAll(currentFiles); updateBatchUI() }
        findViewById<Button>(R.id.btnSaveBatch).setOnClickListener { saveToAlbum(selectedFiles.toList()); isSelectionMode = false; updateBatchUI() }

        val tabButtons = listOf<Button>(
            findViewById(R.id.tabNetPort), findViewById(R.id.tabNetLand), 
            findViewById(R.id.tabLocPort), findViewById(R.id.tabLocLand),
            findViewById(R.id.tabDavPort), findViewById(R.id.tabDavLand)
        )

        fun updateTabsAndLoad(type: Int) {
            currentType = type
            isSelectionMode = false
            selectedFiles.clear()
            updateBatchUI()
            
            tabButtons[0].text = "网络竖屏 (${fileManager.getWallpapers(0, isTrashMode, this).size})"
            tabButtons[1].text = "网络横屏 (${fileManager.getWallpapers(1, isTrashMode, this).size})"
            tabButtons[2].text = "本地竖屏 (${fileManager.getWallpapers(2, isTrashMode, this).size})"
            tabButtons[3].text = "本地横屏 (${fileManager.getWallpapers(3, isTrashMode, this).size})"
            tabButtons[4].text = "云盘竖屏 (${fileManager.getWallpapers(4, isTrashMode, this).size})"
            tabButtons[5].text = "云盘横屏 (${fileManager.getWallpapers(5, isTrashMode, this).size})"

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

        btnDeleteBatch.setOnClickListener {
            if (selectedFiles.isEmpty()) return@setOnClickListener
            val count = selectedFiles.size
            if (isTrashMode) {
                selectedFiles.forEach { it.delete() }
                Toast.makeText(this, "已彻底粉碎 $count 张图片", Toast.LENGTH_SHORT).show()
            } else {
                selectedFiles.forEach { fileManager.moveToTrash(it, currentType, this) }
                Toast.makeText(this, "已将 $count 张图片移至回收站", Toast.LENGTH_SHORT).show()
            }
            isSelectionMode = false
            updateTabsAndLoad(currentType)
        }

        tabButtons[0].setOnClickListener { updateTabsAndLoad(0) }
        tabButtons[1].setOnClickListener { updateTabsAndLoad(1) }
        tabButtons[2].setOnClickListener { updateTabsAndLoad(2) }
        tabButtons[3].setOnClickListener { updateTabsAndLoad(3) }
        tabButtons[4].setOnClickListener { updateTabsAndLoad(4) }
        tabButtons[5].setOnClickListener { updateTabsAndLoad(5) }

        btnBatchRestore.setOnClickListener {
            if (currentFiles.isNotEmpty()) {
                currentFiles.forEach { file -> fileManager.restoreFromTrash(file, currentType, this) }
                Toast.makeText(this, "批量恢复成功", Toast.LENGTH_SHORT).show()
                updateTabsAndLoad(currentType)
            }
        }

        findViewById<Button>(R.id.btnSaveDetail).setOnClickListener { currentDetailFile?.let { file -> saveToAlbum(listOf(file)) } }

        btnSetWall.setOnClickListener {
            currentDetailFile?.let { file ->
                if (isTrashMode) {
                    fileManager.restoreFromTrash(file, currentType, this)
                    Toast.makeText(this, "已恢复", Toast.LENGTH_SHORT).show()
                    detailContainer.visibility = View.GONE
                    resetZoom() 
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
                if (isTrashMode) { file.delete(); Toast.makeText(this, "彻底删除", Toast.LENGTH_SHORT).show() } 
                else { fileManager.moveToTrash(file, currentType, this); Toast.makeText(this, "已移至回收站", Toast.LENGTH_SHORT).show() }
                detailContainer.visibility = View.GONE
                resetZoom() 
                updateTabsAndLoad(currentType)
            }
        }

        findViewById<Button>(R.id.btnCloseDetail).setOnClickListener { backPressedCallback.handleOnBackPressed() }

        updateTabsAndLoad(0)
    }
}
