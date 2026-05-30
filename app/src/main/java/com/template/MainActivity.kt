package com.template

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.background) {
                    AppNavigation()
                }
            }
        }
    }
}

// 简单的页面导航路由
@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf("Home") }

    if (currentScreen == "Home") {
        MainScreen(onNavigateToGallery = { currentScreen = "Gallery" })
    } else {
        GalleryScreen(onBack = { currentScreen = "Home" })
    }
}

@Composable
fun MainScreen(onNavigateToGallery: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fileManager = FileManager()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        // 导入的本地图片暂存为横屏测试，你可以在此后完善文件解析逻辑
                        fileManager.saveWallpaper(bytes, true, context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导入成功！", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("尘尘的壁纸屋", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = {
                try {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context, ChenWallpaperService::class.java)
                        )
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "启动失败", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("🚀 启动壁纸服务", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToGallery,
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3), contentColor = Color.White)
        ) {
            Text("🖼️ 查看已缓存图库", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { filePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("📁 导入本地壁纸", fontSize = 18.sp)
        }
    }
}

@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fileManager = remember { FileManager() }
    
    // 读取所有的缓存文件供展示
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
        
        // 瀑布流/网格图库展示
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
