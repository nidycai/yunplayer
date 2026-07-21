package com.yunplayer.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrackSource { LOCAL, WEBDAV }

enum class PlayMode { ORDER, LOOP, SHUFFLE, ONE }

enum class AppThemeId { MINT, NIGHT, SAND, VIOLET, PAPER }

enum class PlayFxId { RIPPLE, BREATH, PULSE, GLOW, NONE }

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String = "未知艺人",
    val album: String = "",
    val durationMs: Long = 0L,
    val source: String, // LOCAL / WEBDAV
    val uri: String, // content:// or webdav path/url
    val webdavHref: String? = null,
    val fileName: String? = null,
    val coverUri: String? = null,
    val lyrics: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isSystem: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val sortOrder: Int = 0,
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val source: TrackSource,
    val uri: String,
    val webdavHref: String? = null,
    val fileName: String? = null,
    val coverUri: String? = null,
    val lyrics: String? = null,
    val playCount: Int = 0,
)

data class Playlist(
    val id: String,
    val name: String,
    val isSystem: Boolean,
    val trackIds: List<String> = emptyList(),
    val coverUri: String? = null,
)

data class WebDavConfig(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val rootPath: String = "/",
)

data class WebDavEntry(
    val name: String,
    val path: String,
    val href: String,
    val isDirectory: Boolean,
    val isAudio: Boolean,
    val size: Long = 0L,
)

data class UserPrefs(
    val theme: AppThemeId = AppThemeId.MINT,
    val playFx: PlayFxId = PlayFxId.RIPPLE,
    val playMode: PlayMode = PlayMode.ORDER,
    val webdav: WebDavConfig = WebDavConfig(),
)

fun TrackEntity.toTrack() = Track(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.LOCAL),
    uri = uri,
    webdavHref = webdavHref,
    fileName = fileName,
    coverUri = coverUri,
    lyrics = lyrics,
    playCount = playCount,
)

fun Track.toEntity() = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    source = source.name,
    uri = uri,
    webdavHref = webdavHref,
    fileName = fileName,
    coverUri = coverUri,
    lyrics = lyrics,
    playCount = playCount,
)
