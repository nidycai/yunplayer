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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            repo.ensureSystemPlaylists()
            repo.userPrefs.collect { p ->
                _webdav.update {
                    it.copy(
                        config = p.webdav,
                        cwd = if (it.cwd == "/" && p.webdav.rootPath.isNotBlank()) p.webdav.rootPath else it.cwd,
                        connected = p.webdav.baseUrl.isNotBlank() && it.connected,
                    )
                }
            }
        }
        // 轮询播放进度（Media3 player）
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(200)
                val p = playback.player() ?: continue
                val id = p.currentMediaItem?.mediaId
                val track = allTracks.value.find { it.id == id }
                    ?: _player.value.queue.getOrNull(p.currentMediaItemIndex)
                val liked = track?.let { repo.isLiked(it.id) } ?: false
                _player.update {
                    it.copy(
                        track = track,
                        playing = p.isPlaying,
                        positionMs = p.currentPosition.coerceAtLeast(0),
                        durationMs = p.duration.takeIf { d -> d > 0 } ?: (track?.durationMs ?: 0),
                        liked = liked,
                        queueIndex = p.currentMediaItemIndex.coerceAtLeast(0),
                    )
                }
            }
        }
    }

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
        _player.update { it.copy(queue = tracks, queueIndex = index) }
        playback.setQueue(tracks, index, true)
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
        val next = order[(order.indexOf(cur) + 1) % order.size]
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
            val on = repo.toggleLike(id)
            _player.update { it.copy(liked = on) }
            toast(if (on) "已加入我喜欢的" else "已取消喜欢")
        }
    }

    fun importLocal(uris: List<Uri>) {
        viewModelScope.launch {
            val n = repo.importLocalUris(uris)
            toast(if (n > 0) "已导入 $n 首本地音乐" else "未导入新文件")
        }
    }

    fun clearLocal() {
        viewModelScope.launch {
            repo.clearSource(TrackSource.LOCAL)
            toast("已清空本地库")
        }
    }

    fun clearWebDavLibrary() {
        viewModelScope.launch {
            repo.clearSource(TrackSource.WEBDAV)
            toast("已清空 WebDAV 库")
        }
    }

    fun removeTrack(id: String) {
        viewModelScope.launch {
            repo.removeTrack(id)
            toast("已移除")
            _detailPlaylistId.value?.let { openPlaylist(it) }
        }
    }

    fun openPlaylist(id: String) {
        _detailPlaylistId.value = id
        viewModelScope.launch {
            _detailTracks.value = repo.getPlaylistTracks(id)
        }
    }

    fun playPlaylist(id: String) {
        viewModelScope.launch {
            val tracks = repo.getPlaylistTracks(id)
            if (tracks.isEmpty()) {
                toast("歌单里还没有歌曲")
            } else {
                playTracks(tracks, 0)
            }
        }
    }

    fun closePlaylistDetail() {
        _detailPlaylistId.value = null
        _detailTracks.value = emptyList()
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = repo.createPlaylist(name)
            toast("已创建歌单")
            openPlaylist(id)
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            repo.deletePlaylist(id)
            closePlaylistDetail()
            toast("已删除歌单")
        }
    }

    fun addCurrentToPlaylist(playlistId: String) {
        val id = player.value.track?.id ?: run {
            toast("当前没有播放曲目")
            return
        }
        viewModelScope.launch {
            repo.addToPlaylist(playlistId, id)
            toast("已加入歌单")
            if (_detailPlaylistId.value == playlistId) openPlaylist(playlistId)
        }
    }

    fun setTheme(id: AppThemeId) = viewModelScope.launch { repo.setTheme(id) }
    fun setPlayFx(id: PlayFxId) = viewModelScope.launch { repo.setPlayFx(id) }

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
                // 允许用户把完整 URL 填在 baseUrl
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
            val cfg = prefs.value.webdav
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
            // 入库并加入 webdav 歌单
            repo.scanWebDav(cfg, _webdav.value.cwd, deep = false) {}
            val existing = webdavTracks.value
            val queue = (listOf(track) + existing.filter { it.id != track.id })
            playTrack(track, queue)
        }
    }
}
