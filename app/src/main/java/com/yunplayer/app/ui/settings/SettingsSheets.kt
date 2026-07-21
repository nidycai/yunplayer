package com.yunplayer.app.ui.settings

import androidx.compose.runtime.Composable
import com.yunplayer.app.data.model.AppThemeId
import com.yunplayer.app.data.model.PlayFxId
import com.yunplayer.app.data.model.UserPrefs
import com.yunplayer.app.ui.theme.YunColors

/** 占位：设置 UI 已合并进 LibrarySheets.SettingsContent */
@Composable
fun SettingsSheets(
    visible: Boolean,
    yun: YunColors,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onTheme: (AppThemeId) -> Unit,
    onPlayFx: (PlayFxId) -> Unit,
) {
    // no-op placeholder to keep imports stable
}
