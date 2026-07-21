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

/**
 * 原生播放服务（Media3）—— 无 WebView / 无 CORS。
 * WebDAV 通过 OkHttp DataSource + AuthInterceptor 拉流。
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as YunApp
        val okFactory = OkHttpDataSource.Factory(app.httpClient)
        val dsFactory = DefaultDataSource.Factory(this, okFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dsFactory)

        player = ExoPlayer.Builder(this)
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
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivity)
            .build()

        PlayerHolder.bind(this, player!!)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        PlayerHolder.unbind()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
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
                    track.coverUri?.let { setArtworkUri(android.net.Uri.parse(it)) }
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
}

class PlaybackController(private val app: YunApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun ensureService() {
        val intent = Intent(app, PlaybackService::class.java)
        try {
            app.startForegroundService(intent)
        } catch (_: Exception) {
            app.startService(intent)
        }
    }

    fun player(): ExoPlayer? = PlayerHolder.player

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        ensureService()
        scope.launch {
            val prefs = app.repo.userPrefs.first()
            AuthInterceptor.header =
                if (tracks.any { it.source == TrackSource.WEBDAV }) {
                    app.repo.webDavAuthHeader(prefs.webdav)
                } else null

            val pl = waitPlayer() ?: return@launch
            val items = tracks.map { PlaybackService.toMediaItem(it) }
            val idx = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            pl.setMediaItems(items, idx, 0L)
            PlaybackService.applyPlayMode(pl, prefs.playMode)
            pl.prepare()
            if (autoPlay) pl.play()
            tracks.getOrNull(idx)?.let { app.repo.bumpPlay(it.id) }
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val q = if (queue.isEmpty()) listOf(track) else queue
        val idx = q.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        setQueue(q, idx, true)
    }

    fun toggle() {
        val p = player() ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun next() {
        player()?.seekToNextMediaItem()
    }

    fun prev() {
        val p = player() ?: return
        if (p.currentPosition > 3000) p.seekTo(0) else p.seekToPreviousMediaItem()
    }

    fun seek(ms: Long) {
        player()?.seekTo(ms)
    }

    fun setPlayMode(mode: PlayMode) {
        scope.launch {
            app.repo.setPlayMode(mode)
            player()?.let { PlaybackService.applyPlayMode(it, mode) }
        }
    }

    private suspend fun waitPlayer(): ExoPlayer? {
        if (PlayerHolder.player != null) return PlayerHolder.player
        ensureService()
        repeat(40) {
            delay(50)
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
