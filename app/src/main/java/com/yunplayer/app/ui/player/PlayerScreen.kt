package com.yunplayer.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.yunplayer.app.data.model.PlayMode
import com.yunplayer.app.data.model.TrackSource
import com.yunplayer.app.data.model.UserPrefs
import com.yunplayer.app.ui.PlayerUiState
import com.yunplayer.app.ui.components.CoverFxOverlay
import com.yunplayer.app.ui.theme.YunColors

@Composable
fun PlayerScreen(
    player: PlayerUiState,
    prefs: UserPrefs,
    yun: YunColors,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onLike: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlFan: () -> Unit,
    onOpenAddMenu: () -> Unit,
) {
    var showLyrics by remember { mutableStateOf(false) }
    val track = player.track
    val progress = if (player.durationMs > 0) {
        (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
    } else 0f

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(bottom = 16.dp),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onOpenPlFan) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "歌单", tint = yun.inkSoft)
            }
            Text("播放", color = yun.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            IconButton(onClick = onOpenAddMenu) {
                Icon(Icons.Outlined.Add, contentDescription = "菜单", tint = yun.inkSoft)
            }
        }

        Spacer(Modifier.weight(0.08f))

        // Cover + FX
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            // 使用顶层 AnimatedVisibility（非 ColumnScope 扩展）
            androidx.compose.animation.AnimatedVisibility(
                visible = !showLyrics,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.88f)
                        .aspectRatio(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showLyrics = true },
                ) {
                    CoverFxOverlay(
                        playing = player.playing,
                        fx = prefs.playFx,
                        greenDeep = yun.greenDeep,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .shadow(28.dp, RoundedCornerShape(36.dp), ambientColor = yun.green.copy(0.25f))
                            .clip(RoundedCornerShape(36.dp))
                            .background(yun.greenPale),
                    ) {
                        if (track?.coverUri != null) {
                            AsyncImage(
                                model = track.coverUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(yun.greenPale, yun.green.copy(0.35f)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("♪", fontSize = 64.sp, color = yun.greenDeep.copy(0.5f))
                            }
                        }
                        // source badge
                        val src = track?.source
                        if (src != null) {
                            Text(
                                text = if (src == TrackSource.LOCAL) "本地" else "WebDAV",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(
                                        if (src == TrackSource.LOCAL) Color(0x8C3C6EC8)
                                        else Color(0x8CC06A28),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = showLyrics,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showLyrics = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = track?.lyrics?.ifBlank { null }
                            ?: "暂无歌词\n（本地文件可后续扩展内嵌歌词）",
                        color = yun.muted,
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Meta
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: "未在播放",
                    color = yun.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track?.artist ?: "添加音乐开始",
                        color = yun.muted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (track != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (track.source == TrackSource.LOCAL) "本地" else "WebDAV",
                            color = yun.greenDeep,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(yun.greenPale, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (!track?.album.isNullOrBlank()) {
                    Text(track!!.album, color = yun.muted.copy(0.8f), fontSize = 12.sp)
                }
            }
            IconButton(onClick = onLike, enabled = track != null) {
                Icon(
                    if (player.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "喜欢",
                    tint = if (player.liked) yun.green else yun.muted,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Progress
        var scrub by remember { mutableStateOf<Float?>(null) }
        val shown = scrub ?: progress
        Slider(
            value = shown,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                val d = player.durationMs
                if (d > 0 && scrub != null) onSeek((scrub!! * d).toLong())
                scrub = null
            },
            colors = SliderDefaults.colors(
                thumbColor = yun.green,
                activeTrackColor = yun.green,
                inactiveTrackColor = yun.line,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtMs(player.positionMs), color = yun.muted, fontSize = 11.sp)
            Text(fmtMs(player.durationMs), color = yun.muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(8.dp))

        // Transport
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Outlined.QueueMusic, null, tint = yun.inkSoft)
            }
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, null, tint = yun.inkSoft, modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier
                    .size(72.dp)
                    .shadow(12.dp, CircleShape, ambientColor = yun.green.copy(0.4f))
                    .clip(CircleShape)
                    .background(yun.green)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (player.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, null, tint = yun.inkSoft, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onCycleMode) {
                val icon = when (prefs.playMode) {
                    PlayMode.ORDER -> Icons.Filled.List
                    PlayMode.LOOP -> Icons.Filled.Repeat
                    PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                    PlayMode.ONE -> Icons.Filled.RepeatOne
                }
                Icon(
                    icon,
                    contentDescription = "播放模式",
                    tint = if (prefs.playMode == PlayMode.ORDER) yun.inkSoft else yun.green,
                )
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = (ms / 1000).toInt()
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
