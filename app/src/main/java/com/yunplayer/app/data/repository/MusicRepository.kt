package com.yunplayer.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.yunplayer.app.data.local.AppDatabase
import com.yunplayer.app.data.local.PrefsStore
import com.yunplayer.app.data.model.Playlist
import com.yunplayer.app.data.model.PlaylistEntity
import com.yunplayer.app.data.model.PlaylistTrackCrossRef
import com.yunplayer.app.data.model.Track
import com.yunplayer.app.data.model.TrackEntity
import com.yunplayer.app.data.model.TrackSource
import com.yunplayer.app.data.model.UserPrefs
import com.yunplayer.app.data.model.WebDavConfig
import com.yunplayer.app.data.model.WebDavEntry
import com.yunplayer.app.data.model.toEntity
import com.yunplayer.app.data.model.toTrack
import com.yunplayer.app.data.webdav.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class MusicRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context),
    private val prefs: PrefsStore = PrefsStore(context),
    private val webDav: WebDavClient = WebDavClient(),
) {
    val allTracks: Flow<List<Track>> = db.tracks().observeAll().map { list -> list.map { it.toTrack() } }
    val localTracks: Flow<List<Track>> =
        db.tracks().observeBySource(TrackSource.LOCAL.name).map { list -> list.map { it.toTrack() } }
    val webdavTracks: Flow<List<Track>> =
        db.tracks().observeBySource(TrackSource.WEBDAV.name).map { list -> list.map { it.toTrack() } }
    val userPrefs: Flow<UserPrefs> = prefs.prefs

    /** 歌单列表（系统 local/webdav 与曲库同步；其余用交叉表） */
    val playlists: Flow<List<Playlist>> = combine(
        db.playlists().observeAll(),
        db.tracks().observeAll(),
        db.playlists().observeAllCrossRefs(),
    ) { pls, tracks, refs ->
        val refsByPl = refs.groupBy { it.playlistId }
        val localIds = tracks.filter { it.source == TrackSource.LOCAL.name }.map { it.id }
        val webdavIds = tracks.filter { it.source == TrackSource.WEBDAV.name }.map { it.id }
        pls.map { pl ->
            val ids = when (pl.id) {
                "local" -> localIds
                "webdav" -> webdavIds
                else -> refsByPl[pl.id]?.sortedBy { it.sortOrder }?.map { it.trackId }.orEmpty()
            }
            Playlist(
                id = pl.id,
                name = pl.name,
                isSystem = pl.isSystem,
                trackIds = ids,
                coverUri = ids.firstOrNull()?.let { tid -> tracks.find { it.id == tid }?.coverUri },
            )
        }
    }

    suspend fun ensureSystemPlaylists() {
        val systems = listOf(
            PlaylistEntity("liked", "我喜欢的", isSystem = true, sortOrder = 0),
            PlaylistEntity("local", "本地音乐", isSystem = true, sortOrder = 1),
            PlaylistEntity("webdav", "WebDAV 音乐", isSystem = true, sortOrder = 2),
        )
        systems.forEach { db.playlists().upsert(it) }
    }

    suspend fun importLocalUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var added = 0
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            val track = readLocalMeta(uri) ?: return@forEach
            db.tracks().upsert(track.toEntity())
            db.playlists().addTrack(PlaylistTrackCrossRef("local", track.id, 0))
            added++
        }
        added
    }

    private fun readLocalMeta(uri: Uri): Track? {
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "未知曲目"
        val titleFallback = name.substringBeforeLast('.').ifBlank { name }
        var title = titleFallback
        var artist = "本地文件"
        var album = ""
        var duration = 0L
        var coverUri: String? = null
        var lyrics: String? = null
        val id = "local_" + sha1(uri.toString()).take(16)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.ifBlank { null }
                ?: titleFallback
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { null }
                ?: "本地文件"
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            // 内嵌封面 → 写到 app filesDir/covers/
            coverUri = extractEmbeddedCover(retriever, id)
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        // 旁路歌词：同名 .lrc（content URI 不一定可读到同目录，仅尝试 open）
        lyrics = tryReadSidecarLrc(uri, name)
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            source = TrackSource.LOCAL,
            uri = uri.toString(),
            fileName = name,
            coverUri = coverUri,
            lyrics = lyrics,
        )
    }

    /** 从 MediaMetadataRetriever 抽出内嵌专辑图，返回 file:// URI */
    private fun extractEmbeddedCover(retriever: MediaMetadataRetriever, trackId: String): String? {
        return try {
            val bytes = retriever.embeddedPicture ?: return null
            // 验证是合法图片
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val out = File(dir, "$trackId.jpg")
            FileOutputStream(out).use { it.write(bytes) }
            Uri.fromFile(out).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun tryReadSidecarLrc(audioUri: Uri, displayName: String): String? {
        // SAF content:// 通常没有同目录权限；仅当能猜到 .lrc 时再扩展
        return null
    }

    /**
     * 播放时补全元数据：封面 / 歌词 / 艺人专辑。
     * 本地用 content URI；WebDAV 拉文件头几百 KB 试着读 embedded picture（小文件）或旁路 cover.jpg / .lrc。
     */
    suspend fun enrichTrackMeta(track: Track): Track = withContext(Dispatchers.IO) {
        if (!track.coverUri.isNullOrBlank() && !track.lyrics.isNullOrBlank()) return@withContext track
        try {
            when (track.source) {
                TrackSource.LOCAL -> {
                    val uri = Uri.parse(track.uri)
                    val retriever = MediaMetadataRetriever()
                    var cover = track.coverUri
                    var lyrics = track.lyrics
                    var artist = track.artist
                    var album = track.album
                    var title = track.title
                    try {
                        retriever.setDataSource(context, uri)
                        if (cover.isNullOrBlank()) cover = extractEmbeddedCover(retriever, track.id)
                        if (artist == "本地文件" || artist.isBlank()) {
                            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?.ifBlank { null } ?: artist
                        }
                        if (album.isBlank()) {
                            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
                        }
                        val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        if (!t.isNullOrBlank()) title = t
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever.release()
                        } catch (_: Exception) {
                        }
                    }
                    val updated = track.copy(
                        coverUri = cover,
                        lyrics = lyrics,
                        artist = artist,
                        album = album,
                        title = title,
                    )
                    if (updated != track) {
                        upsertPreservingStats(updated)
                    }
                    updated
                }
                TrackSource.WEBDAV -> enrichWebDavMeta(track)
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicRepo", "enrichTrackMeta ${track.id}", e)
            track
        }
    }

    private suspend fun upsertPreservingStats(track: Track) {
        val old = db.tracks().getById(track.id)
        db.tracks().upsert(
            track.toEntity().copy(
                dateAdded = old?.dateAdded ?: System.currentTimeMillis(),
                playCount = old?.playCount ?: track.playCount,
                lastPlayed = old?.lastPlayed ?: 0L,
            ),
        )
    }

    private suspend fun enrichWebDavMeta(track: Track): Track {
        val cfg = prefs.prefs.first().webdav
        if (cfg.baseUrl.isBlank()) return track
        val scoped = webDav.withConfig(cfg)
        var cover = track.coverUri
        var lyrics = track.lyrics
        var artist = track.artist
        var album = track.album

        // 1) 同目录 cover.jpg / folder.jpg / 同名 .jpg
        if (cover.isNullOrBlank()) {
            val href = track.webdavHref ?: return track
            val dir = href.substringBeforeLast('/', "")
            val baseName = href.substringAfterLast('/').substringBeforeLast('.')
            val candidates = listOf(
                "$dir/cover.jpg", "$dir/cover.png", "$dir/folder.jpg", "$dir/Folder.jpg",
                "$dir/$baseName.jpg", "$dir/$baseName.png",
            )
            for (c in candidates) {
                val url = scoped.mediaUrl(c)
                val local = downloadCoverToFile(url, cfg, track.id)
                if (local != null) {
                    cover = local
                    break
                }
            }
        }

        // 2) 同名 .lrc
        if (lyrics.isNullOrBlank()) {
            val href = track.webdavHref
            if (href != null) {
                val lrcHref = href.substringBeforeLast('.') + ".lrc"
                val lrcUrl = scoped.mediaUrl(lrcHref)
                lyrics = downloadText(lrcUrl, cfg)
            }
        }

        // 3) 艺人默认仍是 WebDAV 时，用父文件夹名当专辑（扫描时已写），艺人用上一级目录
        if (artist == "WebDAV" || artist.isBlank()) {
            val href = track.webdavHref.orEmpty()
            val parts = href.trim('/').split('/').filter { it.isNotBlank() }
            if (parts.size >= 2) {
                artist = parts[parts.size - 2]
            }
            if (album.isBlank() && parts.size >= 3) {
                album = parts[parts.size - 2]
                artist = parts[parts.size - 3]
            }
        }

        val updated = track.copy(
            coverUri = cover,
            lyrics = lyrics,
            artist = artist.ifBlank { track.artist },
            album = album.ifBlank { track.album },
        )
        if (updated != track) {
            upsertPreservingStats(updated)
        }
        return updated
    }

    /** 下载封面到本地 filesDir，避免 Coil 无法带 WebDAV 鉴权 */
    private fun downloadCoverToFile(url: String, cfg: WebDavConfig, trackId: String): String? {
        return try {
            val client = OkHttpClient.Builder().followRedirects(true).build()
            val req = Request.Builder().url(url).get()
                .apply {
                    if (cfg.username.isNotBlank() || cfg.password.isNotBlank()) {
                        header("Authorization", Credentials.basic(cfg.username, cfg.password))
                    }
                }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size < 100 || bytes.size > 8_000_000) return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val dir = File(context.filesDir, "covers").apply { mkdirs() }
                val out = File(dir, "$trackId.jpg")
                FileOutputStream(out).use { it.write(bytes) }
                Uri.fromFile(out).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadText(url: String, cfg: WebDavConfig): String? {
        return try {
            val client = OkHttpClient.Builder().followRedirects(true).build()
            val req = Request.Builder().url(url).get()
                .apply {
                    if (cfg.username.isNotBlank() || cfg.password.isNotBlank()) {
                        header("Authorization", Credentials.basic(cfg.username, cfg.password))
                    }
                }
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                body.takeIf { it.isNotBlank() && it.length < 500_000 }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        trackIds.forEach { tid ->
            db.playlists().addTrack(PlaylistTrackCrossRef(playlistId, tid, 0))
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearSource(source: TrackSource) {
        val tracks = db.tracks().observeBySource(source.name).first()
        tracks.forEach { t ->
            db.playlists().removeTrackFromAll(t.id)
            db.tracks().delete(t.id)
        }
        val plId = if (source == TrackSource.LOCAL) "local" else "webdav"
        db.playlists().clearPlaylist(plId)
    }

    suspend fun removeTrack(trackId: String) {
        db.playlists().removeTrackFromAll(trackId)
        db.tracks().delete(trackId)
    }

    suspend fun createPlaylist(name: String): String {
        val id = "pl_" + UUID.randomUUID().toString().take(8)
        db.playlists().upsert(
            PlaylistEntity(id = id, name = name.trim().ifBlank { "未命名歌单" }, isSystem = false, sortOrder = 100),
        )
        return id
    }

    suspend fun deletePlaylist(id: String) {
        db.playlists().clearPlaylist(id)
        db.playlists().deleteUserPlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: String, trackId: String) {
        db.playlists().addTrack(PlaylistTrackCrossRef(playlistId, trackId, 0))
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        db.playlists().removeTrack(playlistId, trackId)
    }

    suspend fun clearPlaylist(playlistId: String) {
        db.playlists().clearPlaylist(playlistId)
    }

    suspend fun toggleLike(trackId: String): Boolean {
        val ids = db.playlists().getTrackIds("liked")
        return if (ids.contains(trackId)) {
            db.playlists().removeTrack("liked", trackId)
            false
        } else {
            db.playlists().addTrack(PlaylistTrackCrossRef("liked", trackId, 0))
            true
        }
    }

    suspend fun isLiked(trackId: String): Boolean =
        db.playlists().getTrackIds("liked").contains(trackId)

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        // 系统歌单 local / webdav 与库同步
        when (playlistId) {
            "local" -> {
                val tracks = db.tracks().observeBySource(TrackSource.LOCAL.name).first()
                db.playlists().setPlaylistTracks(playlistId, tracks.map { it.id })
                return tracks.map { it.toTrack() }
            }
            "webdav" -> {
                val tracks = db.tracks().observeBySource(TrackSource.WEBDAV.name).first()
                db.playlists().setPlaylistTracks(playlistId, tracks.map { it.id })
                return tracks.map { it.toTrack() }
            }
        }
        val ids = db.playlists().getTrackIds(playlistId)
        if (ids.isEmpty()) return emptyList()
        // Room IN () 空列表在部分版本会崩；上面已挡。大批次分片更稳
        val map = ids.chunked(400)
            .flatMap { chunk -> db.tracks().getByIds(chunk) }
            .associateBy { it.id }
        return ids.mapNotNull { map[it]?.toTrack() }
    }

    suspend fun bumpPlay(trackId: String) {
        db.tracks().bumpPlay(trackId)
    }

    // --- WebDAV（原生，无 CORS） ---
    suspend fun saveWebDav(config: WebDavConfig) = prefs.setWebDav(config)

    suspend fun testWebDav(config: WebDavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // WebDavClient.Result：ok=false 时不会抛异常，必须检查字段
            val r = webDav.withConfig(config).testConnection()
            if (r.ok) Result.success(Unit)
            else Result.failure(IllegalStateException(r.error ?: "连接失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listWebDav(config: WebDavConfig, path: String): List<WebDavEntry> =
        webDav.withConfig(config).list(path)

    suspend fun scanWebDav(
        config: WebDavConfig,
        startPath: String,
        deep: Boolean,
        onProgress: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val scoped = webDav.withConfig(config)
        val queue = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        queue.add(startPath.ifBlank { config.rootPath.ifBlank { "/" } })
        var found = 0
        var dirs = 0
        val maxDirs = if (deep) 80 else 1
        val newTracks = mutableListOf<TrackEntity>()

        while (queue.isNotEmpty() && dirs < maxDirs) {
            val dir = queue.removeFirst()
            if (!seen.add(dir)) continue
            dirs++
            onProgress("扫描中… 目录 $dirs · 已发现 $found 首")
            val items = try {
                scoped.list(dir)
            } catch (e: Exception) {
                onProgress("跳过目录：$dir（${e.message}）")
                continue
            }
            val folderName = dir.trimEnd('/').substringAfterLast('/').ifBlank { "WebDAV" }
            for (it in items) {
                if (it.isDirectory && deep) {
                    val next = if (it.path.endsWith("/")) it.path else it.path + "/"
                    if (next !in seen) queue.add(next)
                } else if (it.isAudio) {
                    val id = "wd_" + sha1(it.href).take(20)
                    val mediaUrl = scoped.mediaUrl(it.href)
                    newTracks += TrackEntity(
                        id = id,
                        title = it.name.substringBeforeLast('.').ifBlank { it.name },
                        artist = "WebDAV",
                        album = folderName,
                        durationMs = 0L,
                        source = TrackSource.WEBDAV.name,
                        uri = mediaUrl,
                        webdavHref = it.href,
                        fileName = it.name,
                    )
                    found++
                }
            }
        }
        if (newTracks.isNotEmpty()) {
            db.tracks().upsertAll(newTracks)
            // 合并进 webdav 歌单
            val existing = db.playlists().getTrackIds("webdav").toMutableList()
            newTracks.forEach { t ->
                if (t.id !in existing) existing.add(t.id)
            }
            db.playlists().setPlaylistTracks("webdav", existing)
        }
        onProgress("完成：本轮 $found 首 · 扫描 $dirs 个目录")
        found
    }

    fun webDavMediaUrl(config: WebDavConfig, href: String): String =
        webDav.withConfig(config).mediaUrl(href)

    fun webDavAuthHeader(config: WebDavConfig): String? =
        webDav.withConfig(config).credential()

    suspend fun setTheme(id: com.yunplayer.app.data.model.AppThemeId) = prefs.setTheme(id)
    suspend fun setPlayFx(id: com.yunplayer.app.data.model.PlayFxId) = prefs.setPlayFx(id)
    suspend fun setPlayMode(mode: com.yunplayer.app.data.model.PlayMode) = prefs.setPlayMode(mode)

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
