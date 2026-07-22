package com.yunplayer.app.data.webdav

import com.yunplayer.app.data.model.WebDavConfig
import com.yunplayer.app.data.model.WebDavEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 原生 WebDAV 客户端 —— 无 CORS 限制。
 * 使用 PROPFIND 列目录，GET 下载/流式播放。
 */
class WebDavClient(
    private val http: OkHttpClient = defaultClient(),
) {
    data class Result<T>(val ok: Boolean, val data: T? = null, val error: String? = null)

    fun withConfig(config: WebDavConfig): Scoped = Scoped(config)

    inner class Scoped(private val config: WebDavConfig) {
        private fun authHeader(): String? {
            if (config.username.isBlank() && config.password.isBlank()) return null
            return Credentials.basic(config.username, config.password)
        }

        fun joinUrl(path: String): String {
            val base = config.baseUrl.trim().trimEnd('/')
            var p = path.ifBlank { "/" }
            if (!p.startsWith("/")) p = "/$p"
            val enc = p.split("/").joinToString("/") { seg ->
                if (seg.isEmpty()) {
                    ""
                } else {
                    try {
                        URLEncoder.encode(URLDecoder.decode(seg, "UTF-8"), "UTF-8").replace("+", "%20")
                    } catch (_: Exception) {
                        URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                    }
                }
            }
            return base + enc
        }

        suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                list(config.rootPath.ifBlank { "/" })
                Result(true, Unit)
            } catch (e: Exception) {
                Result(false, error = e.message ?: "连接失败")
            }
        }

        suspend fun list(path: String): List<WebDavEntry> = withContext(Dispatchers.IO) {
            val url = joinUrl(path)
            val body = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:displayname/>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                    <d:getcontenttype/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()
            val req = Request.Builder()
                .url(url)
                .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .header("Depth", "1")
                .header("Accept", "application/xml, text/xml, */*")
                .apply { authHeader()?.let { header("Authorization", it) } }
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    throw IllegalStateException("认证失败，请检查用户名和密码")
                }
                if (!resp.isSuccessful && resp.code != 207) {
                    throw IllegalStateException("PROPFIND 失败：HTTP ${resp.code}")
                }
                val xml = resp.body?.string().orEmpty()
                parsePropfind(xml, path)
            }
        }

        /** 供 ExoPlayer / 下载使用的完整 URL */
        fun mediaUrl(hrefOrPath: String): String {
            return if (hrefOrPath.startsWith("http://") || hrefOrPath.startsWith("https://")) {
                hrefOrPath
            } else {
                joinUrl(hrefOrPath)
            }
        }

        fun credential(): String? = authHeader()
    }

    private fun parsePropfind(xml: String, requestPath: String): List<WebDavEntry> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val reqNorm = normalizePath(requestPath).trimEnd('/').ifEmpty { "/" }
        val items = mutableListOf<WebDavEntry>()

        var event = parser.eventType
        var inResponse = false
        var href = ""
        var isCollection = false
        var contentLength = 0L
        var contentType = ""
        var text = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    text = ""
                    when {
                        name == "response" -> {
                            inResponse = true
                            href = ""
                            isCollection = false
                            contentLength = 0L
                            contentType = ""
                        }
                        name == "collection" && inResponse -> isCollection = true
                    }
                }
                XmlPullParser.TEXT -> text = parser.text ?: ""
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    if (inResponse) {
                        when (name) {
                            "href" -> href = text.trim()
                            "getcontentlength" -> contentLength = text.trim().toLongOrNull() ?: 0L
                            "getcontenttype" -> contentType = text.trim()
                            "response" -> {
                                if (href.isNotBlank()) {
                                    val path = hrefToPath(href, requestPath)
                                    val pathNoSlash = path.trimEnd('/').ifEmpty { "/" }
                                    if (pathNoSlash != reqNorm) {
                                        val isDir = isCollection || href.endsWith("/")
                                        val fileName = URLDecoder.decode(
                                            pathNoSlash.substringAfterLast('/').ifEmpty { pathNoSlash },
                                            "UTF-8",
                                        )
                                        val isAudio = !isDir && (
                                            AUDIO_EXT.contains(fileName.substringAfterLast('.', "").lowercase()) ||
                                                contentType.startsWith("audio/", true)
                                            )
                                        items += WebDavEntry(
                                            name = fileName,
                                            path = if (isDir) {
                                                if (pathNoSlash == "/") "/" else "$pathNoSlash/"
                                            } else pathNoSlash,
                                            href = pathNoSlash,
                                            isDirectory = isDir,
                                            isAudio = isAudio,
                                            size = contentLength,
                                        )
                                    }
                                }
                                inResponse = false
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }

        return items
            .distinctBy { (if (it.isDirectory) "d:" else "f:") + it.path }
            .sortedWith(compareBy<WebDavEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun hrefToPath(href: String, requestPath: String): String {
        return try {
            if (href.startsWith("http://") || href.startsWith("https://")) {
                normalizePath(URL(href).path)
            } else if (href.startsWith("/")) {
                normalizePath(href)
            } else {
                normalizePath(requestPath.trimEnd('/') + "/" + href)
            }
        } catch (_: Exception) {
            normalizePath(href)
        }
    }

    private fun normalizePath(p: String): String {
        var s = p.ifBlank { "/" }
        try {
            s = URLDecoder.decode(s, "UTF-8")
        } catch (_: Exception) {
        }
        if (!s.startsWith("/")) s = "/$s"
        return s.replace(Regex("/+"), "/")
    }

    companion object {
        private val AUDIO_EXT = setOf(
            "mp3", "m4a", "flac", "wav", "ogg", "aac", "opus", "wma", "aiff", "aif", "ape", "dsf",
        )

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

        fun parseBase(inputUrl: String, basePathHint: String = ""): WebDavConfig {
            var raw = inputUrl.trim()
            if (raw.isEmpty()) throw IllegalArgumentException("请填写 WebDAV 地址")
            if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
                raw = "https://$raw"
            }
            val u = URL(raw)
            val baseUrl = "${u.protocol}://${u.authority}"
            var root = basePathHint.trim().ifBlank { u.path.ifBlank { "/" } }
            if (!root.startsWith("/")) root = "/$root"
            root = root.replace(Regex("/+"), "/")
            if (root.length > 1 && !root.endsWith("/")) root += "/"
            return WebDavConfig(baseUrl = baseUrl, rootPath = root)
        }
    }
}
