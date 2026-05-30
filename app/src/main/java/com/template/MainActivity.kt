package com.template

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainScreen(
                        onStartServiceClick = {
                            // 核心逻辑：唤起安卓系统的动态壁纸选择器，并定位到我们的“尘尘的壁纸屋”
                            try {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(this@MainActivity, ChenWallpaperService::class.java)
                                    )
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "启动失败，请检查权限", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUpdateClick = {
                            // TODO: 触发网络 API 更新 (下一步连接)
                            Toast.makeText(this@MainActivity, "即将触发更新...", Toast.LENGTH_SHORT).show()
                        },
                        onImportClick = {
                            // TODO: 触发本地文件选择器 (下一步连接)
                            Toast.makeText(this@MainActivity, "即将打开文件选择器...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onStartServiceClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onImportClick: () -> Unit
) {
    // 居中布局
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 软件标题
        Text(
            text = "尘尘的壁纸屋",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        // 按钮 1：启动服务
        Button(
            onClick = onStartServiceClick,
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("🚀 启动壁纸服务", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 按钮 2：手动更新
        Button(
            onClick = onUpdateClick,
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50), contentColor = Color.White)
        ) {
            Text("🔄 手动更新壁纸", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 按钮 3：导入本地
        OutlinedButton(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("📁 导入本地壁纸", fontSize = 18.sp)
        }
    }
}
