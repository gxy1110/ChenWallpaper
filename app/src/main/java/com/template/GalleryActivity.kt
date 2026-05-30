package com.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.background) {
                    // 传入 finish()，点击返回按钮时直接销毁当前页面，退回主页
                    GalleryScreen(onBack = { finish() }) 
                }
            }
        }
    }
}

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileManager = remember { FileManager() }
    
    val landscapeFiles = remember { fileManager.getWallpapers(true, context) }
    val portraitFiles = remember { fileManager.getWallpapers(false, context) }
    val allFiles = landscapeFiles + portraitFiles

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("已缓存的壁纸 (${allFiles.size}张)") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            backgroundColor = MaterialTheme.colors.surface
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(4.dp)
        ) {
            items(allFiles) { file ->
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(0.7f)
                        .background(Color.LightGray, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
