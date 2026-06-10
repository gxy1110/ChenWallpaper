package com.template

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

data class WebDavPath(val path: String, var isEnabled: Boolean = true)

data class WebDavConfig(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var url: String,
    var user: String,
    var pass: String,
    var isEnabled: Boolean = true,
    var isConnected: Boolean = false,
    var paths: MutableList<WebDavPath> = mutableListOf()
)

data class WebDavItem(val href: String, val isFolder: Boolean, val name: String)

object WebDavManager {
    val client = OkHttpClient()

    fun loadConfigs(context: Context): MutableList<WebDavConfig> {
        val prefs = context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("webdav_configs", "[]") ?: "[]"
        val list = mutableListOf<WebDavConfig>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pathsArray = obj.getJSONArray("paths")
                val paths = mutableListOf<WebDavPath>()
                for (j in 0 until pathsArray.length()) {
                    val pObj = pathsArray.getJSONObject(j)
                    paths.add(WebDavPath(pObj.getString("path"), pObj.getBoolean("isEnabled")))
                }
                list.add(WebDavConfig(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.getString("name"),
                    url = obj.getString("url"),
                    user = obj.getString("user"),
                    pass = obj.getString("pass"),
                    isEnabled = obj.getBoolean("isEnabled"),
                    isConnected = obj.getBoolean("isConnected"),
                    paths = paths
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun saveConfigs(context: Context, configs: List<WebDavConfig>) {
        val array = JSONArray()
        configs.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("url", c.url)
            obj.put("user", c.user)
            obj.put("pass", c.pass)
            obj.put("isEnabled", c.isEnabled)
            obj.put("isConnected", c.isConnected)
            val pathsArray = JSONArray()
            c.paths.forEach { p ->
                val pObj = JSONObject()
                pObj.put("path", p.path)
                pObj.put("isEnabled", p.isEnabled)
                pathsArray.put(pObj)
            }
            obj.put("paths", pathsArray)
            array.put(obj)
        }
        context.getSharedPreferences("WallPrefs", Context.MODE_PRIVATE)
            .edit().putString("webdav_configs", array.toString()).apply()
    }

    // 核心 PROPFIND 解析引擎：向 WebDAV 发送探测包并解析 XML
    fun listDirectory(config: WebDavConfig, targetUrl: String): List<WebDavItem>? {
        try {
            val auth = if (config.user.isNotEmpty()) "Basic " + Base64.encodeToString("${config.user}:${config.pass}".toByteArray(), Base64.NO_WRAP) else ""
            val xml = """<?xml version="1.0" encoding="utf-8" ?><D:propfind xmlns:D="DAV:"><D:prop><D:resourcetype/></D:prop></D:propfind>"""
            val reqBuilder = Request.Builder().url(targetUrl).method("PROPFIND", xml.toRequestBody("application/xml".toMediaType())).header("Depth", "1")
            if (auth.isNotEmpty()) reqBuilder.header("Authorization", auth)
            
            val resp = client.newCall(reqBuilder.build()).execute()
            if (!resp.isSuccessful) return null
            
            val body = resp.body?.string() ?: return null
            val items = mutableListOf<WebDavItem>()
            val blocks = body.split(Regex("<[dD]:response|<response", RegexOption.IGNORE_CASE)).drop(1)
            
            val baseUri = URI(config.url)
            for (block in blocks) {
                val hrefMatch = Regex("<[a-zA-Z0-9:]*href>([^<]+)</", RegexOption.IGNORE_CASE).find(block)
                var href = hrefMatch?.groupValues?.get(1) ?: continue
                if (!href.startsWith("http")) href = baseUri.resolve(href).toString()
                
                // 去除自身文件夹的返回结果
                if (href.trimEnd('/') == targetUrl.trimEnd('/')) continue
                
                val isFolder = block.contains("collection/>", true) || block.contains("collection></", true) || href.endsWith("/")
                val name = href.trimEnd('/').substringAfterLast('/')
                items.add(WebDavItem(href, isFolder, java.net.URLDecoder.decode(name, "UTF-8")))
            }
            return items
        } catch (e: Exception) { return null }
    }

    fun downloadFile(config: WebDavConfig, fileUrl: String): ByteArray? {
        try {
            val auth = if (config.user.isNotEmpty()) "Basic " + Base64.encodeToString("${config.user}:${config.pass}".toByteArray(), Base64.NO_WRAP) else ""
            val req = Request.Builder().url(fileUrl).get()
            if (auth.isNotEmpty()) req.header("Authorization", auth)
            return client.newCall(req.build()).execute().body?.bytes()
        } catch (e: Exception) { return null }
    }
}
