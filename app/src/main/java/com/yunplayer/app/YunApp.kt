package com.yunplayer.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yunplayer.app.data.repository.MusicRepository
import com.yunplayer.app.data.webdav.WebDavClient
import com.yunplayer.app.player.AuthInterceptor
import com.yunplayer.app.player.PlaybackController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class YunApp : Application() {
    lateinit var repo: MusicRepository
        private set
    lateinit var playback: PlaybackController
        private set

    /** 带 WebDAV Auth 拦截器的 OkHttp，供 ExoPlayer 使用 */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor)
            .followRedirects(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 未捕获异常写日志，便于定位闪退（不吞异常，继续抛出）
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            android.util.Log.e("YunCrash", "Uncaught on ${t.name}", e)
            prev?.uncaughtException(t, e)
        }
        repo = MusicRepository(this, webDav = WebDavClient(httpClient))
        playback = PlaybackController(this)
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                "playback",
                getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_playback_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        lateinit var instance: YunApp
            private set
    }
}
