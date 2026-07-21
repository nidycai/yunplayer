package com.yunplayer.app.ui.components

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
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yunplayer.app.data.model.PlayFxId
import kotlin.math.max

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
    val t = rememberInfiniteTransition(label = "coverFx")

    when (fx) {
        PlayFxId.RIPPLE, PlayFxId.PULSE -> {
            val period = if (fx == PlayFxId.RIPPLE) 7200 else 2800
            repeat(3) { i ->
                val p by t.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(period, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = androidx.compose.animation.core.StartOffset(-(period / 3) * i),
                    ),
                    label = "ring$i",
                )
                val scale = 1f + p * (if (fx == PlayFxId.RIPPLE) 0.62f else 0.48f)
                val alpha = (1f - p) * (if (fx == PlayFxId.RIPPLE) 0.5f else 0.55f)
                Box(
                    modifier
                        .fillMaxSize()
                        .scale(scale)
                        .graphicsLayer { this.alpha = max(0f, alpha) }
                        .border(
                            width = (2.2f - i * 0.4f).dp,
                            color = greenDeep.copy(alpha = 0.55f - i * 0.1f),
                            shape = shape,
                        ),
                )
            }
        }
        PlayFxId.BREATH -> {
            val p by t.animateFloat(
                0.92f, 1.18f,
                infiniteRepeatable(tween(4200), RepeatMode.Reverse),
                label = "breath",
            )
            val a by t.animateFloat(
                0.22f, 0.55f,
                infiniteRepeatable(tween(4200), RepeatMode.Reverse),
                label = "breathA",
            )
            Box(
                modifier
                    .fillMaxSize()
                    .scale(p)
                    .graphicsLayer { alpha = a }
                    .border(0.dp, Color.Transparent, shape)
                    .then(
                        Modifier.border(
                            24.dp,
                            greenDeep.copy(alpha = 0.15f),
                            shape,
                        ),
                    ),
            )
        }
        PlayFxId.GLOW -> {
            val rot by t.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(10000, easing = LinearEasing)),
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
        else -> Unit
    }
}
