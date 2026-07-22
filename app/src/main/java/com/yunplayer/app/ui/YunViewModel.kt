package com.yunplayer.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yunplayer.app.YunApp
import com.yunplayer.app.data.model.AppThemeId
import com.yunplayer.app.data.model.PlayFxId
import com.yunplayer.app.data.model.PlayMode
import com.yunplayer.app.data.model.Playlist
import com.yunplayer.app.data.model.Track
import com.yunplayer.app.data.model.TrackSource
import com.yunplayer.app.data.model.UserPrefs
import com.yunplayer.app.data.model.WebDavConfig
import com.yunplayer.app.data.model.WebDavEntry
import com.yunplayer.app.data.webdav.WebDavClient
import com.yunplayer.app.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val track: Track? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val liked: Boolean = false,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
)

data class WebDavUiState(
    val config: WebDavConfig = WebDavConfig(),
    val connected: Boolean = false,
    val cwd: String = "/",
    val entries: List<WebDavEntry> = emptyList(),
    val loading: Boolean = false,
    val message: String = "",
    val scanning: Boolean = false,
)

class YunViewModel(app: Application) : AndroidViewModel(app) {
    private val yun = app as YunApp
    private val repo get() = yun.repo
    private val playback get() = yun.playback

    val prefs: StateFlow<UserPrefs> = repo.userPrefs.stateIn(
        viewModelScope, SharingStarted.Eagerly, UserPrefs(),
    )
    val playlists: StateFlow<List<Playlist>> = repo.playlists.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val localTracks: StateFlow<List<Track>> = repo.localTracks.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val webdavTracks: StateFlow<List<Track>> = repo.webdavTracks.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )
    val allTracks: StateFlow<List<Track>> = repo.allTracks.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )

    private val _player = MutableStateFlow(PlayerUiState())
    val player: StateFlow<PlayerUiState> = _player.asStateFlow()

    private val _webdav = MutableStateFlow(WebDavUiState())
    val webdav: StateFlow<WebDavUiState> = _webdav.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _detailPlaylistId = MutableStateFlow<String?>(null)
    val detailPlaylistId: StateFlow<String?> = _detailPlaylistId.asStateFlow()

    private val _detailTracks = MutableStateFlow<List<Track>>(emptyList())
    val detailTracks: StateFlow<List<Track>> = _detailTracks.asStateFlow()

    /** 缓存 liked，避免每 200ms 打一次 Room */
    private var likedCacheId: String? = null
    private var likedCacheValue: Boolean = false

    init {
        viewModelScope.launch {
            try {
                repo.ensureSystemPlaylists()
            } catch (e: Exception) {
                android.util.Log.e("YunVM", "ensureSystemPlaylists", e)
            }
            try {
                repo.userPrefs.collect { p ->
                    _webdav.update {
                        it.copy(
                            config = p.webdav,
                            cwd = if (it.cwd == "/" && p.webdav.rootPath.isNotBlank()) p.webdav.rootPath else it.cwd,
                            connected = p.webdav.baseUrl.isNotBlank() && it.connected,
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("YunVM", "prefs collect", e)
            }
        }
        // 轮询播放进度（Media3 player），所有 player 访问必须 try-catch
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(250)
                try {
                    val snap = PlayerHolder.withPlayer { p ->
                        val id = p.currentMediaItem?.mediaId
                        val idx = p.currentMediaItemIndex.coerceAtLeast(0)
                        val pos = p.currentPosition.coerceAtLeast(0)
                        val durRaw = p.duration
                        val dur = if (durRaw > 0 && durRaw < Long.MAX_VALUE / 4) durRaw else 0L
                        val playing = p.isPlaying
                        PlayerSnap(id, idx, pos, dur, playing)
                    } ?: continue

                    val track = allTracks.value.find { it.id == snap.id }
                        ?: _player.value.queue.getOrNull(snap.idx)

                    val liked = if (track == null) {
                        false
                    } else if (likedCacheId == track.id) {
                        likedCacheValue
                    } else {
                        val v = try {
                            withContext(Dispatchers.IO) { repo.isLiked(track.id) }
                        } catch (_: Exception) {
                            false
                        }
                        likedCacheId = track.id
                        likedCacheValue = v
                        v
                    }

                    _player.update {
                        it.copy(
                            track = track,
                            playing = snap.playing,
                            positionMs = snap.pos,
                            durationMs = if (snap.dur > 0) snap.dur else (track?.durationMs ?: 0),
                            liked = liked,
                            queueIndex = snap.idx,
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.w("YunVM", "player poll", e)
                }
            }
        }
    }

    private data class PlayerSnap(
        val id: String?,
        val idx: Int,
        val pos: Long,
        val dur: Long,
        val playing: Boolean,
    )

    fun toast(msg: String) {
        _toast.value = msg
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun playTracks(tracks: List<Track>, index: Int = 0) {
        if (tracks.isEmpty()) {
            toast("没有可播放曲目")
            return
        }
        val safeIndex = index.coerceIn(0, tracks.lastIndex)
        _player.update { it.copy(queue = tracks, queueIndex = safeIndex) }
        playback.setQueue(tracks, safeIndex, true)
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val q = if (queue.isEmpty()) listOf(track) else queue
        playTracks(q, q.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun togglePlay() = playback.toggle()
    fun next() = playback.next()
    fun prev() = playback.prev()
    fun seek(ms: Long) = playback.seek(ms)

    fun cyclePlayMode() {
        val order = listOf(PlayMode.ORDER, PlayMode.LOOP, PlayMode.SHUFFLE, PlayMode.ONE)
        val cur = prefs.value.playMode
        val next = order[(order.indexOf(cur).coerceAtLeast(0) + 1) % order.size]
        playback.setPlayMode(next)
        toast(
            when (next) {
                PlayMode.ORDER -> "顺序播放"
                PlayMode.LOOP -> "列表循环"
                PlayMode.SHUFFLE -> "随机播放"
                PlayMode.ONE -> "单曲循环"
            },
        )
    }

    fun toggleLike() {
        val id = player.value.track?.id ?: return
        viewModelScope.launch {
            try {
                val on = repo.toggleLike(id)
                likedCacheId = id
                likedCacheValue = on
                _player.update { it.copy(liked = on) }
                toast(if (on) "已加入我喜欢的" else "已取消喜欢")
            } catch (e: Exception) {
                toast(e.message ?: "操作失败")
            }
        }
    }

    fun importLocal(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val n = repo.importLocalUris(uris)
                toast(if (n > 0) "已导入 $n 首本地音乐" else "未导入新文件")
            } catch (e: Exception) {
                toast(e.message ?: "导入失败")
            }
        }
    }

    fun clearLocal() {
        viewModelScope.launch {
            try {
                repo.clearSource(TrackSource.LOCAL)
                toast("已清空本地库")
            } catch (e: Exception) {
                toast(e.message ?: "清空失败")
            }
        }
    }

    fun clearWebDavLibrary() {
        viewModelScope.launch {
            try {
                repo.clearSource(TrackSource.WEBDAV)
                toast("已清空 WebDAV 库")
            } catch (e: Exception) {
                toast(e.message ?: "清空失败")
            }
        }
    }

    fun removeTrack(id: String) {
        viewModelScope.launch {
            try {
                repo.removeTrack(id)
                toast("已移除")
                _detailPlaylistId.value?.let { openPlaylist(it) }
            } catch (e: Exception) {
                toast(e.message ?: "移除失败")
            }
        }
    }

    fun openPlaylist(id: String) {
        _detailPlaylistId.value = id
        viewModelScope.launch {
            try {
                _detailTracks.value = repo.getPlaylistTracks(id)
            } catch (e: Exception) {
                _detailTracks.value = emptyList()
                toast(e.message ?: "打开歌单失败")
            }
        }
    }

    fun playPlaylist(id: String) {
        viewModelScope.launch {
            try {
                val tracks = repo.getPlaylistTracks(id)
                if (tracks.isEmpty()) {
                    toast("歌单里还没有歌曲")
                } else {
                    playTracks(tracks, 0)
                }
            } catch (e: Exception) {
                toast(e.message ?: "播放失败")
            }
        }
    }

    fun closePlaylistDetail() {
        _detailPlaylistId.value = null
        _detailTracks.value = emptyList()
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                val id = repo.createPlaylist(name)
                toast("已创建歌单")
                openPlaylist(id)
            } catch (e: Exception) {
                toast(e.message ?: "创建失败")
            }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            try {
                repo.deletePlaylist(id)
                closePlaylistDetail()
                toast("已删除歌单")
            } catch (e: Exception) {
                toast(e.message ?: "删除失败")
            }
        }
    }

    fun addCurrentToPlaylist(playlistId: String) {
        val id = player.value.track?.id ?: run {
            toast("当前没有播放曲目")
            return
        }
        viewModelScope.launch {
            try {
                repo.addToPlaylist(playlistId, id)
                toast("已加入歌单")
                if (_detailPlaylistId.value == playlistId) openPlaylist(playlistId)
            } catch (e: Exception) {
                toast(e.message ?: "加入失败")
            }
        }
    }

    fun setTheme(id: AppThemeId) = viewModelScope.launch {
        try {
            repo.setTheme(id)
        } catch (_: Exception) {
        }
    }

    fun setPlayFx(id: PlayFxId) = viewModelScope.launch {
        try {
            repo.setPlayFx(id)
        } catch (_: Exception) {
        }
    }

    // WebDAV
    fun updateWebDavForm(url: String, user: String, pass: String, root: String) {
        _webdav.update {
            it.copy(
                config = it.config.copy(
                    baseUrl = url.trim(),
                    username = user,
                    password = pass,
                    rootPath = root.trim().ifBlank { "/" },
                ),
            )
        }
    }

    fun connectWebDav() {
        viewModelScope.launch {
            _webdav.update { it.copy(loading = true, message = "正在连接…") }
            try {
                val raw = _webdav.value.config
                val parsed = try {
                    WebDavClient.parseBase(raw.baseUrl, raw.rootPath)
                        .copy(username = raw.username, password = raw.password)
                } catch (e: Exception) {
                    _webdav.update { it.copy(loading = false, message = e.message ?: "地址无效") }
                    toast(e.message ?: "地址无效")
                    return@launch
                }
                val r = repo.testWebDav(parsed)
                if (r.isSuccess) {
                    repo.saveWebDav(parsed)
                    val entries = repo.listWebDav(parsed, parsed.rootPath)
                    _webdav.update {
                        it.copy(
                            config = parsed,
                            connected = true,
                            cwd = parsed.rootPath,
                            entries = entries,
                            loading = false,
                            message = "已连接",
                        )
                    }
                    toast("WebDAV 已连接")
                } else {
                    _webdav.update {
                        it.copy(loading = false, message = r.exceptionOrNull()?.message ?: "连接失败")
                    }
                    toast(r.exceptionOrNull()?.message ?: "连接失败")
                }
            } catch (e: Exception) {
                _webdav.update { it.copy(loading = false, message = e.message ?: "连接失败") }
                toast(e.message ?: "连接失败")
            }
        }
    }

    fun openWebDavPath(path: String) {
        viewModelScope.launch {
            val cfg = prefs.value.webdav
            if (cfg.baseUrl.isBlank()) {
                toast("请先连接 WebDAV")
                return@launch
            }
            _webdav.update { it.copy(loading = true) }
            try {
                val entries = repo.listWebDav(cfg, path)
                _webdav.update {
                    it.copy(cwd = path, entries = entries, loading = false, connected = true, message = "")
                }
            } catch (e: Exception) {
                _webdav.update { it.copy(loading = false, message = e.message ?: "打开失败") }
                toast(e.message ?: "打开失败")
            }
        }
    }

    fun scanWebDav(deep: Boolean) {
        viewModelScope.launch {
            val cfg = prefs.value.webdav
            if (cfg.baseUrl.isBlank()) {
                toast("请先连接 WebDAV")
                return@launch
            }
            _webdav.update { it.copy(scanning = true, message = "扫描中…") }
            try {
                val n = repo.scanWebDav(cfg, _webdav.value.cwd.ifBlank { cfg.rootPath }, deep) { msg ->
                    _webdav.update { it.copy(message = msg) }
                }
                _webdav.update { it.copy(scanning = false) }
                toast(if (n > 0) "扫描完成，新增 $n 首" else "未发现新音频")
            } catch (e: Exception) {
                _webdav.update { it.copy(scanning = false, message = e.message ?: "扫描失败") }
                toast(e.message ?: "扫描失败")
            }
        }
    }

    fun playWebDavEntry(entry: WebDavEntry) {
        if (!entry.isAudio) return
        viewModelScope.launch {
            try {
                val cfg = prefs.value.webdav
                if (cfg.baseUrl.isBlank()) {
                    toast("请先连接 WebDAV")
                    return@launch
                }
                val url = repo.webDavMediaUrl(cfg, entry.href)
                val id = "wd_" + entry.href.hashCode().toUInt().toString(16)
                val track = Track(
                    id = id,
                    title = entry.name.substringBeforeLast('.').ifBlank { entry.name },
                    artist = "WebDAV",
                    album = _webdav.value.cwd.trimEnd('/').substringAfterLast('/').ifBlank { "WebDAV" },
                    durationMs = 0L,
                    source = TrackSource.WEBDAV,
                    uri = url,
                    webdavHref = entry.href,
                    fileName = entry.name,
                )
                // 尽量入库；失败也不阻塞播放
                runCatching {
                    repo.scanWebDav(cfg, _webdav.value.cwd, deep = false) {}
                }
                val existing = webdavTracks.value
                val queue = (listOf(track) + existing.filter { it.id != track.id })
                playTrack(track, queue)
            } catch (e: Exception) {
                toast(e.message ?: "播放失败")
            }
        }
    }
}
