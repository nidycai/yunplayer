package com.yunplayer.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yunplayer.app.data.model.AppThemeId
import com.yunplayer.app.data.model.PlayFxId
import com.yunplayer.app.data.model.PlayMode
import com.yunplayer.app.data.model.UserPrefs
import com.yunplayer.app.data.model.WebDavConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("yunplayer_prefs")

class PrefsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val playFx = stringPreferencesKey("play_fx")
        val playMode = stringPreferencesKey("play_mode")
        val wdUrl = stringPreferencesKey("wd_url")
        val wdUser = stringPreferencesKey("wd_user")
        val wdPass = stringPreferencesKey("wd_pass")
        val wdRoot = stringPreferencesKey("wd_root")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            theme = runCatching { AppThemeId.valueOf(p[Keys.theme] ?: "MINT") }.getOrDefault(AppThemeId.MINT),
            playFx = runCatching { PlayFxId.valueOf(p[Keys.playFx] ?: "RIPPLE") }.getOrDefault(PlayFxId.RIPPLE),
            playMode = runCatching { PlayMode.valueOf(p[Keys.playMode] ?: "ORDER") }.getOrDefault(PlayMode.ORDER),
            webdav = WebDavConfig(
                baseUrl = p[Keys.wdUrl].orEmpty(),
                username = p[Keys.wdUser].orEmpty(),
                password = p[Keys.wdPass].orEmpty(),
                rootPath = p[Keys.wdRoot] ?: "/",
            ),
        )
    }

    suspend fun setTheme(id: AppThemeId) {
        context.dataStore.edit { it[Keys.theme] = id.name }
    }

    suspend fun setPlayFx(id: PlayFxId) {
        context.dataStore.edit { it[Keys.playFx] = id.name }
    }

    suspend fun setPlayMode(mode: PlayMode) {
        context.dataStore.edit { it[Keys.playMode] = mode.name }
    }

    suspend fun setWebDav(config: WebDavConfig) {
        context.dataStore.edit {
            it[Keys.wdUrl] = config.baseUrl
            it[Keys.wdUser] = config.username
            it[Keys.wdPass] = config.password
            it[Keys.wdRoot] = config.rootPath
        }
    }
}
