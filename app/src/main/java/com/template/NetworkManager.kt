package com.template

import okhttp3.OkHttpClient
import okhttp3.Request

class NetworkManager {
    private val client = OkHttpClient()
    
    // 👇👇👇 极其重要：一定要把这里的网址改成你真实的随机图片API地址！
    private val API_URL = "https://tpapi.chenchen.one/" 

    // 伪装成 PC 浏览器获取横屏壁纸
    fun fetchLandscapeWallpaper(): ByteArray? {
        val request = Request.Builder()
            .url(API_URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()
        return client.newCall(request).execute().body?.bytes()
    }

    // 伪装成手机浏览器获取竖屏壁纸
    fun fetchPortraitWallpaper(): ByteArray? {
        val request = Request.Builder()
            .url(API_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        return client.newCall(request).execute().body?.bytes()
    }
}
