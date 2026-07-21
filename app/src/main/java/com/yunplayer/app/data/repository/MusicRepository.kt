package com.yunplayer.app.data.repository

import android.content.Context
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

    val playlists: Flow<List<Playlist>> = combine(
        db.playlists().observeAll(),
        db.tracks().observeAll(),
    ) { pls, tracks ->
        pls.map { pl ->
            val ids = db.playlists().getTrackIds(pl.id)
            val cover = ids.firstOrNull()?.let { tid -> tracks.find { it.id == tid }?.coverUri }
            Playlist(
                id = pl.id,
                name = pl.name,
                isSystem = pl.isSystem,
                trackIds = ids,
                coverUri = cover,
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
        try {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.ifBlank { null }
                    ?: titleFallback
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { null }
                    ?: "本地文件"
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
                duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
        }
        val id = "local_" + sha1(uri.toString()).take(16)
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            source = TrackSource.LOCAL,
            uri = uri.toString(),
            fileName = name,
        )
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
        val map = db.tracks().getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { map[it]?.toTrack() }
    }

    suspend fun bumpPlay(trackId: String) {
        db.tracks().bumpPlay(trackId)
    }

    // --- WebDAV（原生，无 CORS） ---
    suspend fun saveWebDav(config: WebDavConfig) = prefs.setWebDav(config)

    suspend fun testWebDav(config: WebDavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            webDav.withConfig(config).testConnection()
            Result.success(Unit)
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
