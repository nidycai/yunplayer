@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.yunplayer.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 原生播放服务（Media3）—— 无 WebView / 无 CORS。
 * WebDAV 通过 OkHttp DataSource + AuthInterceptor 拉流。
 *
 * 注意：不要用 startForegroundService 冷启动本服务。
 * MediaSessionService 只在真正播放并弹出媒体通知时才进入前台；
 * 提前 FGS 会在 5s 内未 startForeground 时直接闪退。
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
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
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()

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
        } catch (e: Exception) {
            android.util.Log.e(TAG, "PlaybackService onCreate failed", e)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            p?.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
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

    /** 安全读取：player 已释放或异常时返回 null，避免闪退 */
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

class PlaybackController(private val app: YunApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueMutex = Mutex()

    fun ensureService() {
        val intent = Intent(app, PlaybackService::class.java)
        // 禁止 startForegroundService：MediaSessionService 在未真正播放时不会立刻
        // startForeground，会触发 ForegroundServiceDidNotStartInTimeException 闪退。
        // 由 Media3 在播放时自行升为前台并弹出媒体通知。
        try {
            app.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackController", "ensureService failed: ${e.message}", e)
        }
    }

    fun player(): ExoPlayer? = PlayerHolder.player

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        ensureService()
        scope.launch {
            queueMutex.withLock {
                try {
                    val prefs = app.repo.userPrefs.first()
                    AuthInterceptor.header =
                        if (tracks.any { it.source == TrackSource.WEBDAV }) {
                            app.repo.webDavAuthHeader(prefs.webdav)
                        } else {
                            null
                        }

                    val pl = waitPlayer() ?: return@withLock
                    val items = tracks.map { PlaybackService.toMediaItem(it) }
                    if (items.isEmpty()) return@withLock
                    val idx = startIndex.coerceIn(0, items.lastIndex)
                    pl.setMediaItems(items, idx, 0L)
                    PlaybackService.applyPlayMode(pl, prefs.playMode)
                    pl.prepare()
                    if (autoPlay) pl.play()
                    tracks.getOrNull(idx)?.let { app.repo.bumpPlay(it.id) }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackController", "setQueue failed", e)
                }
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val q = if (queue.isEmpty()) listOf(track) else queue
        val idx = q.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        setQueue(q, idx, true)
    }

    fun toggle() {
        PlayerHolder.withPlayer { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun next() {
        PlayerHolder.withPlayer { it.seekToNextMediaItem() }
    }

    fun prev() {
        PlayerHolder.withPlayer { p ->
            if (p.currentPosition > 3000) p.seekTo(0) else p.seekToPreviousMediaItem()
        }
    }

    fun seek(ms: Long) {
        if (ms < 0) return
        PlayerHolder.withPlayer { it.seekTo(ms) }
    }

    fun setPlayMode(mode: PlayMode) {
        scope.launch {
            try {
                app.repo.setPlayMode(mode)
                PlayerHolder.withPlayer { PlaybackService.applyPlayMode(it, mode) }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackController", "setPlayMode", e)
            }
        }
    }

    private suspend fun waitPlayer(): ExoPlayer? {
        PlayerHolder.player?.let { return it }
        ensureService()
        repeat(50) {
            delay(40)
            PlayerHolder.player?.let { return it }
        }
        return PlayerHolder.player
    }
}

/** 全局 WebDAV Authorization */
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
