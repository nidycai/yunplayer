package com.yunplayer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunplayer.app.ui.library.LibrarySheets
import com.yunplayer.app.ui.player.PlayerScreen
import com.yunplayer.app.ui.theme.colorsFor
import kotlinx.coroutines.delay

enum class SheetKind {
    None, Queue, Playlists, PlaylistDetail, Sources, WebDav, Settings, Stats
}

@Composable
fun YunAppRoot(
    vm: YunViewModel,
    onPickLocalAudio: () -> Unit,
) {
    val prefs by vm.prefs.collectAsState()
    val player by vm.player.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val localTracks by vm.localTracks.collectAsState()
    val webdavTracks by vm.webdavTracks.collectAsState()
    val webdav by vm.webdav.collectAsState()
    val detailId by vm.detailPlaylistId.collectAsState()
    val detailTracks by vm.detailTracks.collectAsState()
    val allTracks by vm.allTracks.collectAsState()
    val addingToPlaylistId by vm.addingToPlaylistId.collectAsState()
    val toast by vm.toast.collectAsState()
    val yun = colorsFor(prefs.theme)

    var sheet by remember { mutableStateOf(SheetKind.None) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showPlFan by remember { mutableStateOf(false) }

    // 打开歌单详情时切 sheet
    LaunchedEffect(detailId) {
        if (detailId != null) sheet = SheetKind.PlaylistDetail
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(yun.bgTop, yun.bgMid, yun.bgBot)),
            ),
    ) {
        PlayerScreen(
            player = player,
            prefs = prefs,
            yun = yun,
            onTogglePlay = vm::togglePlay,
            onNext = vm::next,
            onPrev = vm::prev,
            onSeek = vm::seek,
            onCycleMode = vm::cyclePlayMode,
            onLike = vm::toggleLike,
            onOpenQueue = { sheet = SheetKind.Queue },
            onOpenPlFan = { showPlFan = true },
            onOpenAddMenu = { showAddMenu = true },
        )

        LibrarySheets(
            sheet = sheet,
            showPlFan = showPlFan,
            showAddMenu = showAddMenu,
            playlists = playlists,
            localTracks = localTracks,
            webdavTracks = webdavTracks,
            detailId = detailId,
            detailTracks = detailTracks,
            player = player,
            webdav = webdav,
            yun = yun,
            onDismissSheet = {
                if (sheet == SheetKind.PlaylistDetail) vm.closePlaylistDetail()
                sheet = SheetKind.None
            },
            onDismissPlFan = { showPlFan = false },
            onDismissAddMenu = { showAddMenu = false },
            onOpenPlaylist = { id ->
                showPlFan = false
                vm.openPlaylist(id)
            },
            onPlayPlaylist = { id ->
                showPlFan = false
                vm.playPlaylist(id)
            },
            onPlayTracks = { tracks, index ->
                sheet = SheetKind.None
                vm.closePlaylistDetail()
                vm.playTracks(tracks, index)
            },
            onPlayTrack = { track, queue ->
                sheet = SheetKind.None
                vm.closePlaylistDetail()
                vm.playTrack(track, queue)
            },
            onCreatePlaylist = { name -> vm.createPlaylist(name) },
            onDeletePlaylist = { id -> vm.deletePlaylist(id) },
            onAddCurrent = { id -> vm.addCurrentToPlaylist(id) },
            onRemoveTrack = { id -> vm.removeTrack(id) },
            onClearLocal = vm::clearLocal,
            onClearWebDavLib = vm::clearWebDavLibrary,
            onPickLocal = onPickLocalAudio,
            onOpenSources = {
                showAddMenu = false
                sheet = SheetKind.Sources
            },
            onOpenSettings = {
                showAddMenu = false
                sheet = SheetKind.Settings
            },
            onOpenWebDav = { sheet = SheetKind.WebDav },
            onOpenStats = { sheet = SheetKind.Stats },
            onWebDavForm = vm::updateWebDavForm,
            onConnectWebDav = vm::connectWebDav,
            onOpenWebDavPath = vm::openWebDavPath,
            onScanWebDav = vm::scanWebDav,
            onPlayWebDavEntry = { e ->
                sheet = SheetKind.None
                vm.playWebDavEntry(e)
            },
            prefs = prefs,
            onTheme = vm::setTheme,
            onPlayFx = vm::setPlayFx,
            allTracks = allTracks,
            addingToPlaylistId = addingToPlaylistId,
            onOpenAddTracks = { id ->
                sheet = SheetKind.PlaylistDetail
                vm.openPlaylist(id)
                vm.openAddTracks(id)
            },
            onCloseAddTracks = vm::closeAddTracks,
            onConfirmAddTracks = { ids -> vm.addTracksToCurrentPlaylist(ids) },
        )

        // Toast
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        ) {
            toast?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2200)
                    vm.consumeToast()
                }
                Text(
                    text = msg,
                    color = yun.card,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(yun.ink.copy(alpha = 0.82f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
