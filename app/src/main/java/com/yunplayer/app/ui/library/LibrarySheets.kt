package com.yunplayer.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yunplayer.app.data.model.AppThemeId
import com.yunplayer.app.data.model.PlayFxId
import com.yunplayer.app.data.model.Playlist
import com.yunplayer.app.data.model.Track
import com.yunplayer.app.data.model.TrackSource
import com.yunplayer.app.data.model.UserPrefs
import com.yunplayer.app.data.model.WebDavEntry
import com.yunplayer.app.ui.PlayerUiState
import com.yunplayer.app.ui.SheetKind
import com.yunplayer.app.ui.WebDavUiState
import com.yunplayer.app.ui.theme.YunColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheets(
    sheet: SheetKind,
    showPlFan: Boolean,
    showAddMenu: Boolean,
    playlists: List<Playlist>,
    localTracks: List<Track>,
    webdavTracks: List<Track>,
    detailId: String?,
    detailTracks: List<Track>,
    player: PlayerUiState,
    webdav: WebDavUiState,
    prefs: UserPrefs,
    yun: YunColors,
    onDismissSheet: () -> Unit,
    onDismissPlFan: () -> Unit,
    onDismissAddMenu: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddCurrent: (String) -> Unit,
    onRemoveTrack: (String) -> Unit, // reserved for swipe-to-delete later
    onClearLocal: () -> Unit,
    onClearWebDavLib: () -> Unit,
    onPickLocal: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebDav: () -> Unit,
    onOpenStats: () -> Unit,
    onWebDavForm: (String, String, String, String) -> Unit,
    onConnectWebDav: () -> Unit,
    onOpenWebDavPath: (String) -> Unit,
    onScanWebDav: (Boolean) -> Unit,
    onPlayWebDavEntry: (WebDavEntry) -> Unit,
    onTheme: (AppThemeId) -> Unit,
    onPlayFx: (PlayFxId) -> Unit,
) {
    // 左上角歌单扇出
    AnimatedVisibility(
        visible = showPlFan,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.25f))
                .clickable { onDismissPlFan() },
        ) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 100.dp)
                    .width(260.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                playlists.forEach { pl ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = yun.card),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenPlaylist(pl.id)
                            },
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverThumb(pl.coverUri, yun)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, color = yun.ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${pl.trackIds.size} 首", color = yun.muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onPlayPlaylist(pl.id) }) {
                                Icon(Icons.Default.PlayArrow, null, tint = yun.greenDeep)
                            }
                        }
                    }
                }
                // 新建
                var showNew by remember { mutableStateOf(false) }
                var newName by remember { mutableStateOf("") }
                Card(
                    colors = CardDefaults.cardColors(containerColor = yun.card),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNew = true },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(yun.greenPale, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("+", color = yun.greenDeep, fontSize = 22.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text("新建歌单", color = yun.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (showNew) {
                    Card(colors = CardDefaults.cardColors(yun.card), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                placeholder = { Text("歌单名称") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { showNew = false }) { Text("取消") }
                                TextButton(onClick = {
                                    onCreatePlaylist(newName)
                                    newName = ""
                                    showNew = false
                                    onDismissPlFan()
                                }) { Text("创建") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 右上角 + 菜单
    AnimatedVisibility(visible = showAddMenu, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.18f))
                .clickable { onDismissAddMenu() },
        ) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 100.dp, end = 14.dp)
                    .width(200.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MenuCard("音效", "均衡器在播放页长按可后续扩展", yun) {
                    onDismissAddMenu()
                }
                MenuCard("歌曲源", "本地 / WebDAV", yun, onOpenSources)
                MenuCard("设置", "主题 · 动效 · 统计", yun, onOpenSettings)
            }
        }
    }

    // Bottom sheets
    if (sheet != SheetKind.None && sheet != SheetKind.PlaylistDetail) {
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = yun.card,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            when (sheet) {
                SheetKind.Queue -> QueueSheet(player, yun, onPlayTracks, onDismissSheet)
                SheetKind.Playlists -> PlaylistsBrowseSheet(playlists, yun, onOpenPlaylist)
                SheetKind.Sources -> SourcesSheet(
                    localTracks, webdavTracks, yun,
                    onPickLocal, onClearLocal, onClearWebDavLib,
                    onPlayTracks, onOpenWebDav, onRemoveTrack,
                )
                SheetKind.WebDav -> WebDavSheet(
                    webdav, yun, onWebDavForm, onConnectWebDav,
                    onOpenWebDavPath, onScanWebDav, onPlayWebDavEntry,
                )
                SheetKind.Settings -> SettingsContent(prefs, yun, onTheme, onPlayFx, onOpenStats, onOpenSources)
                SheetKind.Stats -> StatsSheet(localTracks + webdavTracks, yun)
                else -> {}
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    if (sheet == SheetKind.PlaylistDetail && detailId != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = yun.card,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            val pl = playlists.find { it.id == detailId }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(pl?.name ?: "歌单", color = yun.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${detailTracks.size} 首", color = yun.muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPlayTracks(detailTracks, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = yun.green),
                        modifier = Modifier.weight(1f),
                    ) { Text("播放全部") }
                    OutlinedButton(onClick = { onAddCurrent(detailId) }, modifier = Modifier.weight(1f)) {
                        Text("加入当前曲")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    itemsIndexed(detailTracks) { i, t ->
                        TrackRow(t, yun, player.track?.id == t.id) {
                            onPlayTracks(detailTracks, i)
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun MenuCard(title: String, sub: String, yun: YunColors, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(yun.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = yun.ink, fontWeight = FontWeight.SemiBold)
            Text(sub, color = yun.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CoverThumb(uri: String?, yun: YunColors) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(yun.greenPale),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.MusicNote, null, tint = yun.greenDeep)
        }
    }
}

@Composable
private fun TrackRow(track: Track, yun: YunColors, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(track.coverUri, yun)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (active) yun.greenDeep else yun.ink,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${track.artist} · ${if (track.source == TrackSource.LOCAL) "本地" else "WebDAV"}",
                color = yun.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QueueSheet(
    player: PlayerUiState,
    yun: YunColors,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("播放列表 · ${player.queue.size} 首", color = yun.ink, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            itemsIndexed(player.queue) { i, t ->
                TrackRow(t, yun, player.track?.id == t.id) { onPlayTracks(player.queue, i) }
            }
        }
    }
}

@Composable
private fun PlaylistsBrowseSheet(
    playlists: List<Playlist>,
    yun: YunColors,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("歌单", color = yun.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        LazyColumn {
            items(playlists) { pl ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(pl.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverThumb(pl.coverUri, yun)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, color = yun.ink, fontWeight = FontWeight.Medium)
                        Text("${pl.trackIds.size} 首", color = yun.muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = yun.muted)
                }
            }
        }
    }
}

@Composable
private fun SourcesSheet(
    local: List<Track>,
    webdav: List<Track>,
    yun: YunColors,
    onPickLocal: () -> Unit,
    onClearLocal: () -> Unit,
    onClearWebDav: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenWebDav: () -> Unit,
    onRemoveTrack: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("歌曲源管理", color = yun.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("本地 ${local.size}", tab == 0, yun) { tab = 0 }
            TabChip("WebDAV ${webdav.size}", tab == 1, yun) { tab = 1 }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickLocal, colors = ButtonDefaults.buttonColors(yun.green)) {
                    Text("导入本地")
                }
                OutlinedButton(onClick = { if (local.isNotEmpty()) onPlayTracks(local, 0) }) {
                    Text("播放全部")
                }
                OutlinedButton(onClick = onClearLocal) { Text("清空") }
            }
            LazyColumn {
                itemsIndexed(local) { i, t ->
                    TrackRow(t, yun, false) { onPlayTracks(local, i) }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenWebDav, colors = ButtonDefaults.buttonColors(yun.green)) {
                    Text("连接 / 浏览")
                }
                OutlinedButton(onClick = { if (webdav.isNotEmpty()) onPlayTracks(webdav, 0) }) {
                    Text("播放全部")
                }
                OutlinedButton(onClick = onClearWebDav) { Text("清空") }
            }
            LazyColumn {
                itemsIndexed(webdav) { i, t ->
                    TrackRow(t, yun, false) { onPlayTracks(webdav, i) }
                }
            }
        }
    }
}

@Composable
private fun TabChip(text: String, active: Boolean, yun: YunColors, onClick: () -> Unit) {
    Text(
        text,
        color = if (active) Color.White else yun.inkSoft,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) yun.green else yun.line.copy(0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun WebDavSheet(
    state: WebDavUiState,
    yun: YunColors,
    onForm: (String, String, String, String) -> Unit,
    onConnect: () -> Unit,
    onOpenPath: (String) -> Unit,
    onScan: (Boolean) -> Unit,
    onPlayEntry: (WebDavEntry) -> Unit,
) {
    var url by remember(state.config.baseUrl) { mutableStateOf(state.config.baseUrl.ifBlank { "" }) }
    var user by remember(state.config.username) { mutableStateOf(state.config.username) }
    var pass by remember(state.config.password) { mutableStateOf(state.config.password) }
    var root by remember(state.config.rootPath) { mutableStateOf(state.config.rootPath) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("WebDAV", color = yun.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("原生网络请求 · 无 CORS 限制", color = yun.muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        if (!state.connected) {
            OutlinedTextField(url, { url = it; onForm(url, user, pass, root) }, label = { Text("服务器地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it; onForm(url, user, pass, root) }, label = { Text("用户名") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(
                    pass, { pass = it; onForm(url, user, pass, root) },
                    label = { Text("密码") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            OutlinedTextField(root, { root = it; onForm(url, user, pass, root) }, label = { Text("起始路径") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onForm(url, user, pass, root)
                    onConnect()
                },
                colors = ButtonDefaults.buttonColors(yun.green),
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.loading) "连接中…" else "连接") }
        } else {
            Text("当前：${state.cwd}", color = yun.muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onScan(false) }, colors = ButtonDefaults.buttonColors(yun.green), enabled = !state.scanning) {
                    Text("扫描此文件夹")
                }
                OutlinedButton(onClick = { onScan(true) }, enabled = !state.scanning) {
                    Text("深度扫描")
                }
            }
            if (state.message.isNotBlank()) {
                Text(state.message, color = yun.greenDeep, fontSize = 12.sp)
            }
            LazyColumn {
                items(state.entries) { e ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (e.isDirectory) onOpenPath(e.path)
                                else if (e.isAudio) onPlayEntry(e)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (e.isDirectory) Icons.Default.Folder else Icons.Default.MusicNote,
                            null,
                            tint = if (e.isAudio) yun.greenDeep else yun.muted,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(e.name, color = yun.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // 返回上级
            val parent = state.cwd.trimEnd('/').substringBeforeLast('/', "").let {
                if (it.isEmpty()) "/" else "$it/"
            }
            if (state.cwd != "/" && state.cwd != state.config.rootPath) {
                TextButton(onClick = { onOpenPath(parent) }) { Text("返回上级") }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    prefs: UserPrefs,
    yun: YunColors,
    onTheme: (AppThemeId) -> Unit,
    onPlayFx: (PlayFxId) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSources: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("设置", color = yun.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text("主题", color = yun.ink, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeId.entries.forEach { t ->
                val label = when (t) {
                    AppThemeId.MINT -> "清茶"
                    AppThemeId.NIGHT -> "夜墨"
                    AppThemeId.SAND -> "暖沙"
                    AppThemeId.VIOLET -> "紫霞"
                    AppThemeId.PAPER -> "极简"
                }
                TabChip(label, prefs.theme == t, yun) { onTheme(t) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("播放动效", color = yun.ink, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayFxId.entries.forEach { f ->
                val label = when (f) {
                    PlayFxId.RIPPLE -> "涟漪"
                    PlayFxId.BREATH -> "呼吸"
                    PlayFxId.PULSE -> "脉冲"
                    PlayFxId.GLOW -> "光晕"
                    PlayFxId.NONE -> "关闭"
                }
                TabChip(label, prefs.playFx == f, yun) { onPlayFx(f) }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenSources, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Source, null)
            Spacer(Modifier.width(8.dp))
            Text("歌曲源管理")
        }
        OutlinedButton(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
            Text("播放统计")
        }
    }
}

@Composable
private fun StatsSheet(tracks: List<Track>, yun: YunColors) {
    val top = tracks.sortedByDescending { it.playCount }.take(10)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("播放统计", color = yun.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        if (top.isEmpty()) {
            Text("播几首歌后这里会显示最爱榜", color = yun.muted)
        } else {
            top.forEachIndexed { i, t ->
                Text(
                    "#${i + 1}  ${t.title}  ·  ${t.playCount} 次",
                    color = yun.inkSoft,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}
