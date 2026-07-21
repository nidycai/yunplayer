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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yunplayer.app.data.model.PlayFxId

/** 封面播放动效：涟漪 / 呼吸 / 脉冲 / 光晕 / 关闭 */
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
            val p by transition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "breath",
            )
            val a by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 0.55f,
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
                    .graphicsLayer { alpha = a }
                    .border(20.dp, greenDeep.copy(alpha = 0.18f), shape),
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
                    .scale(1.15f)
                    .graphicsLayer {
                        rotationZ = rot
                        alpha = 0.4f
                    }
                    .border(28.dp, greenDeep.copy(alpha = 0.25f), shape),
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
    val amp = if (slow) 0.62f else 0.48f
    // 三圈共用同一动画相位，用固定相位偏移近似错开
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
        val alpha = (1f - local) * if (slow) 0.5f else 0.55f
        Box(
            modifier
                .fillMaxSize()
                .scale(scale)
                .graphicsLayer { this.alpha = alpha.coerceIn(0f, 1f) }
                .border(
                    width = (2.2f - i * 0.4f).dp,
                    color = greenDeep.copy(alpha = (0.55f - i * 0.1f).coerceIn(0.1f, 1f)),
                    shape = shape,
                ),
        )
    }
}
