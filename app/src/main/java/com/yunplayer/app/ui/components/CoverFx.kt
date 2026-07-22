package com.yunplayer.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunplayer.app.data.model.PlayFxId

/**
 * 封面播放动效：涟漪 / 呼吸 / 脉冲 / 光晕 / 关闭。
 * 缩放一律从 1.0 向外扩，避免与封面之间出现空隙。
 */
@Composable
fun CoverFxOverlay(
    playing: Boolean,
    fx: PlayFxId,
    greenDeep: Color,
    modifier: Modifier = Modifier,
) {
    if (!playing || fx == PlayFxId.NONE) return
    val shape = RoundedCornerShape(36.dp)
    val transition = rememberInfiniteTransition(label = "coverFx")

    when (fx) {
        PlayFxId.RIPPLE -> RippleRings(transition, greenDeep, shape, modifier, slow = true)
        PlayFxId.PULSE -> RippleRings(transition, greenDeep, shape, modifier, slow = false)
        PlayFxId.BREATH -> {
            // 从 1.0 扩到 1.14，贴着封面外缘呼吸
            val p by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breath",
            )
            val a by transition.animateFloat(
                initialValue = 0.18f,
                targetValue = 0.48f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breathA",
            )
            Box(
                modifier
                    .fillMaxSize()
                    .scale(p)
                    .alpha(a)
                    .border(3.dp, greenDeep.copy(alpha = 0.35f), shape),
            )
        }
        PlayFxId.GLOW -> {
            val rot by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(10000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "glow",
            )
            Box(
                modifier
                    .fillMaxSize()
                    .scale(1.06f)
                    .rotate(rot)
                    .alpha(0.35f)
                    .border(6.dp, greenDeep.copy(alpha = 0.28f), shape),
            )
        }
        PlayFxId.NONE -> Unit
    }
}

@Composable
private fun RippleRings(
    transition: androidx.compose.animation.core.InfiniteTransition,
    greenDeep: Color,
    shape: RoundedCornerShape,
    modifier: Modifier,
    slow: Boolean,
) {
    val period = if (slow) 7200 else 2800
    // 最大扩张幅度；从 1.0 起，与封面贴边
    val amp = if (slow) 0.28f else 0.22f
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = period, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple",
    )
    listOf(0f, 0.33f, 0.66f).forEachIndexed { i, phase ->
        val local = (p + phase) % 1f
        val scale = 1f + local * amp
        val alpha = ((1f - local) * if (slow) 0.55f else 0.6f).coerceIn(0f, 1f)
        Box(
            modifier
                .fillMaxSize()
                .scale(scale)
                .alpha(alpha)
                .border(
                    width = (2.4f - i * 0.35f).dp,
                    color = greenDeep.copy(alpha = (0.5f - i * 0.1f).coerceIn(0.12f, 1f)),
                    shape = shape,
                ),
        )
    }
}
