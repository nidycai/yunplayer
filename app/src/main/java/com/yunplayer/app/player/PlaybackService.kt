@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.yunplayer.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yunplayer.app.MainActivity
import com.yunplayer.app.YunApp
import com.yunplayer.app.data.model.PlayMode
import com.yunplayer.app.data.model.Track
import com.yunplayer.app.data.model.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 原生播放服务（Media3）。
 *
 * 冷启动用 [startService]，由 Media3 在真正播放时升前台出媒体通知。
 * 不要提前 [startForegroundService]，否则 5s 内未 startForeground 会闪退。
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e(TAG, "player error code=${error.errorCode} ${error.message}", error)
            PlaybackControllerHub.onPlayerError(error)
            // 尝试自动切到下一首；没有下一首则停在错误态供 UI 提示
            mainHandler.post {
                val p = player ?: return@post
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    p.prepare()
                    p.play()
                } else {
                    // 单曲/队尾：尝试重新 prepare 当前项
                    try {
                        val idx = p.currentMediaItemIndex.coerceAtLeast(0)
                        val pos = p.currentPosition.coerceAtLeast(0)
                        p.seekTo(idx, pos)
                        p.prepare()
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "recover after error failed", e)
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            android.util.Log.d(TAG, "state=$playbackState playWhenReady=${player?.playWhenReady}")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.d(TAG, "isPlaying=$isPlaying")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            if (id != null) {
                PlaybackControllerHub.onMediaItemTransition(id)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "onCreate")
        try {
            val app = application as YunApp
            val okFactory = OkHttpDataSource.Factory(app.httpClient)
            val dsFactory = DefaultDataSource.Factory(this, okFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dsFactory)

            val exo = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            exo.addListener(playerListener)

            val sessionActivity = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            session = MediaSession.Builder(this, exo)
                .setSessionActivity(sessionActivity)
                .build()

            player = exo
            PlayerHolder.bind(this, exo)
            PlaybackControllerHub.onServiceReady()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "PlaybackService onCreate failed", e)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 仍在播放时不要自杀；只有空闲才停
        val p = player
        val keep = p != null &&
            p.mediaItemCount > 0 &&
            (p.isPlaying || p.playWhenReady) &&
            p.playbackState != Player.STATE_ENDED &&
            p.playbackState != Player.STATE_IDLE
        if (!keep) {
            android.util.Log.i(TAG, "onTaskRemoved → stopSelf")
            p?.stop()
            stopSelf()
        } else {
            android.util.Log.i(TAG, "onTaskRemoved → keep running")
        }
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "onDestroy")
        player?.removeListener(playerListener)
        PlayerHolder.unbind()
        try {
            session?.release()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "session release", e)
        }
        try {
            player?.release()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "player release", e)
        }
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"

        fun applyPlayMode(player: Player, mode: PlayMode) {
            when (mode) {
                PlayMode.ORDER -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_OFF
                }
                PlayMode.LOOP -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                PlayMode.SHUFFLE -> {
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                PlayMode.ONE -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_ONE
                }
            }
        }

        fun toMediaItem(track: Track): MediaItem {
            val meta = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .apply {
                    track.coverUri?.takeIf { it.isNotBlank() }?.let {
                        runCatching { setArtworkUri(android.net.Uri.parse(it)) }
                    }
                }
                .build()
            return MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uri)
                .setMediaMetadata(meta)
                .build()
        }
    }
}

object PlayerHolder {
    @Volatile var service: PlaybackService? = null
        private set
    @Volatile var player: ExoPlayer? = null
        private set

    fun bind(service: PlaybackService, player: ExoPlayer) {
        this.service = service
        this.player = player
    }

    fun unbind() {
        service = null
        player = null
    }

    inline fun <T> withPlayer(block: (ExoPlayer) -> T): T? {
        val p = player ?: return null
        return try {
            block(p)
        } catch (e: Exception) {
            android.util.Log.w("PlayerHolder", "player access failed", e)
            null
        }
    }
}

/** 给 Service listener 回调 Controller 用（避免循环引用构造） */
internal object PlaybackControllerHub {
    @Volatile var controller: PlaybackController? = null

    fun onServiceReady() {
        controller?.onServiceReady()
    }

    fun onPlayerError(error: PlaybackException) {
        controller?.onPlayerError(error)
    }

    fun onMediaItemTransition(mediaId: String) {
        controller?.onMediaItemTransition(mediaId)
    }
}

/**
 * UI / ViewModel 侧的播放控制。
 * - 缓存最近队列，服务被杀或 player 丢失后可自动恢复
 * - 控制操作（暂停/切歌/seek）在 player 不可用或处于 ERROR/IDLE 时会自动 re-prepare
 */
class PlaybackController(private val app: YunApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueMutex = Mutex()

    @Volatile private var lastQueue: List<Track> = emptyList()
    @Volatile private var lastIndex: Int = 0
    @Volatile private var lastPositionMs: Long = 0L

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        PlaybackControllerHub.controller = this
    }

    fun clearError() {
        _lastError.value = null
    }

    fun ensureService() {
        val intent = Intent(app, PlaybackService::class.java)
        try {
            app.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "ensureService failed: ${e.message}", e)
        }
    }

    fun player(): ExoPlayer? = PlayerHolder.player

    fun lastQueueSnapshot(): List<Track> = lastQueue

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        lastQueue = tracks
        lastIndex = idx
        lastPositionMs = 0L
        ensureService()
        scope.launch {
            applyQueueLocked(tracks, idx, positionMs = 0L, autoPlay = autoPlay)
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val q = if (queue.isEmpty()) listOf(track) else queue
        val idx = q.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        setQueue(q, idx, true)
    }

    fun toggle() {
        scope.launch {
            val p = ensureReadyPlayer(resumeIfNeeded = false) ?: return@launch
            try {
                if (p.isPlaying) {
                    lastPositionMs = p.currentPosition.coerceAtLeast(0)
                    lastIndex = p.currentMediaItemIndex.coerceAtLeast(0)
                    p.pause()
                } else {
                    // ERROR / IDLE / ENDED 时单纯 play() 无效，需要 prepare
                    recoverIfNeeded(p)
                    p.play()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "toggle failed", e)
                // 彻底重建队列
                restoreQueue(autoPlay = true)
            }
        }
    }

    fun next() {
        scope.launch {
            val p = ensureReadyPlayer(resumeIfNeeded = false) ?: return@launch
            try {
                recoverIfNeeded(p)
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    p.play()
                } else if (lastQueue.size > 1) {
                    // ORDER 模式到队尾：绕回第一首
                    p.seekTo(0, 0L)
                    p.play()
                } else {
                    // 单曲：重头播
                    p.seekTo(0)
                    p.play()
                }
                lastIndex = p.currentMediaItemIndex.coerceAtLeast(0)
                lastPositionMs = 0L
            } catch (e: Exception) {
                android.util.Log.e(TAG, "next failed", e)
                val nextIdx = if (lastQueue.isEmpty()) 0 else (lastIndex + 1) % lastQueue.size
                restoreQueue(index = nextIdx, positionMs = 0L, autoPlay = true)
            }
        }
    }

    fun prev() {
        scope.launch {
            val p = ensureReadyPlayer(resumeIfNeeded = false) ?: return@launch
            try {
                recoverIfNeeded(p)
                if (p.currentPosition > 3000) {
                    p.seekTo(0)
                } else if (p.hasPreviousMediaItem()) {
                    p.seekToPreviousMediaItem()
                    p.play()
                } else if (lastQueue.size > 1) {
                    p.seekTo(lastQueue.lastIndex, 0L)
                    p.play()
                } else {
                    p.seekTo(0)
                }
                lastIndex = p.currentMediaItemIndex.coerceAtLeast(0)
                lastPositionMs = p.currentPosition.coerceAtLeast(0)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "prev failed", e)
                val prevIdx = if (lastQueue.isEmpty()) 0
                else if (lastIndex <= 0) lastQueue.lastIndex else lastIndex - 1
                restoreQueue(index = prevIdx, positionMs = 0L, autoPlay = true)
            }
        }
    }

    fun seek(ms: Long) {
        if (ms < 0) return
        scope.launch {
            val p = ensureReadyPlayer(resumeIfNeeded = false) ?: return@launch
            try {
                recoverIfNeeded(p)
                p.seekTo(ms)
                lastPositionMs = ms
                // 拖进度后若已暂停，不强制 play；若之前在播则保持 playWhenReady
                if (p.playWhenReady) p.play()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "seek failed", e)
                restoreQueue(index = lastIndex, positionMs = ms, autoPlay = true)
            }
        }
    }

    fun setPlayMode(mode: PlayMode) {
        scope.launch {
            try {
                app.repo.setPlayMode(mode)
                ensureReadyPlayer(resumeIfNeeded = false)?.let {
                    PlaybackService.applyPlayMode(it, mode)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "setPlayMode", e)
            }
        }
    }

    internal fun onServiceReady() {
        android.util.Log.i(TAG, "service ready, lastQueue=${lastQueue.size}")
    }

    internal fun onPlayerError(error: PlaybackException) {
        val msg = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "网络中断，播放失败"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            -> "无法读取音频文件"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            -> "不支持的音频格式"
            else -> error.message?.take(80) ?: "播放出错"
        }
        _lastError.value = msg
    }

    internal fun onMediaItemTransition(mediaId: String) {
        val idx = lastQueue.indexOfFirst { it.id == mediaId }
        if (idx >= 0) {
            lastIndex = idx
            lastPositionMs = 0L
            scope.launch {
                runCatching { app.repo.bumpPlay(mediaId) }
            }
        }
        clearError()
    }

    private suspend fun ensureReadyPlayer(resumeIfNeeded: Boolean): ExoPlayer? {
        PlayerHolder.player?.let { return it }
        // 服务挂了：重启并尽量恢复队列
        android.util.Log.w(TAG, "player missing → restart service & restore queue")
        ensureService()
        val p = waitPlayer()
        if (p == null) {
            _lastError.value = "播放服务启动失败"
            return null
        }
        if (lastQueue.isNotEmpty() && p.mediaItemCount == 0) {
            applyQueueLocked(
                lastQueue,
                lastIndex.coerceIn(0, lastQueue.lastIndex),
                lastPositionMs,
                autoPlay = resumeIfNeeded,
            )
        }
        return PlayerHolder.player
    }

    private fun recoverIfNeeded(p: ExoPlayer) {
        val state = p.playbackState
        if (state == Player.STATE_IDLE || p.playerError != null) {
            android.util.Log.w(TAG, "recover prepare state=$state err=${p.playerError?.errorCode}")
            p.prepare()
        } else if (state == Player.STATE_ENDED) {
            // 播完后点暂停/播放：从当前项重头再来
            p.seekTo(p.currentMediaItemIndex.coerceAtLeast(0), 0L)
            p.prepare()
        }
    }

    private suspend fun restoreQueue(
        index: Int = lastIndex,
        positionMs: Long = lastPositionMs,
        autoPlay: Boolean = true,
    ) {
        if (lastQueue.isEmpty()) return
        val idx = index.coerceIn(0, lastQueue.lastIndex)
        applyQueueLocked(lastQueue, idx, positionMs, autoPlay)
    }

    private suspend fun applyQueueLocked(
        tracks: List<Track>,
        startIndex: Int,
        positionMs: Long,
        autoPlay: Boolean,
    ) {
        queueMutex.withLock {
            try {
                ensureService()
                val prefs = withContext(Dispatchers.IO) { app.repo.userPrefs.first() }
                AuthInterceptor.header =
                    if (tracks.any { it.source == TrackSource.WEBDAV }) {
                        app.repo.webDavAuthHeader(prefs.webdav)
                    } else {
                        null
                    }

                val pl = waitPlayer()
                if (pl == null) {
                    _lastError.value = "播放器未就绪"
                    return@withLock
                }
                val items = tracks.map { PlaybackService.toMediaItem(it) }
                if (items.isEmpty()) return@withLock
                val idx = startIndex.coerceIn(0, items.lastIndex)
                lastQueue = tracks
                lastIndex = idx
                lastPositionMs = positionMs.coerceAtLeast(0)

                pl.setMediaItems(items, idx, positionMs.coerceAtLeast(0))
                PlaybackService.applyPlayMode(pl, prefs.playMode)
                pl.prepare()
                if (autoPlay) pl.play() else pl.pause()
                clearError()
                tracks.getOrNull(idx)?.let {
                    runCatching { app.repo.bumpPlay(it.id) }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "applyQueue failed", e)
                _lastError.value = e.message ?: "设置播放队列失败"
            }
        }
    }

    private suspend fun waitPlayer(): ExoPlayer? {
        PlayerHolder.player?.let { return it }
        ensureService()
        repeat(60) {
            delay(50)
            PlayerHolder.player?.let { return it }
        }
        return PlayerHolder.player
    }

    companion object {
        private const val TAG = "PlaybackController"
    }
}

/** 全局 WebDAV Authorization（ExoPlayer OkHttp DataSource 用） */
object AuthInterceptor : okhttp3.Interceptor {
    @Volatile
    var header: String? = null

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val h = header
        val req = if (h.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", h).build()
        }
        return chain.proceed(req)
    }
}
