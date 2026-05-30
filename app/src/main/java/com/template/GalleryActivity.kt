package com.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 彻底脱离 Compose 环境：直接在原生 Activity 里把数据准备好
        val fileManager = FileManager()
        val allFiles = fileManager.getWallpapers(true, this) + fileManager.getWallpapers(false, this)
        
        // 将所有图片按 3 张一组进行切分，为后续的“手拼网格”做准备
        val chunkedFiles = allFiles.chunked(3) 

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.background) {
                    
                    // 2. 彻底消灭自定义 @Composable 函数，全部代码直写在 setContent 里！
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text("已缓存的壁纸 (${allFiles.size}张)") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) { // 直接调用原生的 finish() 关闭页面
                                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                                }
                            },
                            backgroundColor = MaterialTheme.colors.surface
                        )
                        
                        // 3. 彻底弃用不稳定的 LazyVerticalGrid，改用最基础的 LazyColumn 手写网格！
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            items(chunkedFiles) { rowFiles ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    // 遍历这一行的图片
                                    for (file in rowFiles) {
                                        AsyncImage(
                                            model = file,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(4.dp)
                                                .aspectRatio(0.7f)
                                                .background(Color.LightGray, RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    // 补充空位：保证最后一行如果不满 3 张图片，也能完美左对齐且不拉伸
                                    for (i in 0 until (3 - rowFiles.size)) {
                                        Spacer(modifier = Modifier.weight(1f).padding(4.dp))
                                    }
                                }
                            }
                        }
                    }
                    
                }
            }
        }
    }
}
