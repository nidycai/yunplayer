package com.yunplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yunplayer.app.data.model.AppThemeId

data class YunColors(
    val bgTop: Color,
    val bgMid: Color,
    val bgBot: Color,
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val line: Color,
    val green: Color,
    val greenDeep: Color,
    val greenPale: Color,
    val card: Color,
)

fun colorsFor(theme: AppThemeId): YunColors = when (theme) {
    AppThemeId.MINT -> YunColors(
        bgTop = Color(0xFFEEF8F9),
        bgMid = Color(0xFFF2F9F8),
        bgBot = Color(0xFFF7FBFC),
        ink = Color(0xFF1C1F1C),
        inkSoft = Color(0xFF2A2E2A),
        muted = Color(0xFF8F9A96),
        line = Color(0xFFDFEAE6),
        green = Color(0xFF73A07D),
        greenDeep = Color(0xFF5F8D69),
        greenPale = Color(0xFFD8E8DC),
        card = Color.White,
    )
    AppThemeId.NIGHT -> YunColors(
        bgTop = Color(0xFF1A1F24),
        bgMid = Color(0xFF151A1F),
        bgBot = Color(0xFF101418),
        ink = Color(0xFFE8EEF2),
        inkSoft = Color(0xFFC5D0D8),
        muted = Color(0xFF8A9AA6),
        line = Color(0xFF2A343C),
        green = Color(0xFF6BBF8A),
        greenDeep = Color(0xFF8FD4A8),
        greenPale = Color(0xFF24352C),
        card = Color(0xFF1C242C),
    )
    AppThemeId.SAND -> YunColors(
        bgTop = Color(0xFFFAF4EB),
        bgMid = Color(0xFFF7EFE3),
        bgBot = Color(0xFFF3E8DA),
        ink = Color(0xFF2C241C),
        inkSoft = Color(0xFF3D342A),
        muted = Color(0xFF9A8B78),
        line = Color(0xFFE8DCC8),
        green = Color(0xFFC48A4A),
        greenDeep = Color(0xFFA67238),
        greenPale = Color(0xFFF0E0C8),
        card = Color(0xFFFFFAF3),
    )
    AppThemeId.VIOLET -> YunColors(
        bgTop = Color(0xFFF4F0FB),
        bgMid = Color(0xFFEFEAF8),
        bgBot = Color(0xFFEBE4F5),
        ink = Color(0xFF221C2E),
        inkSoft = Color(0xFF342C42),
        muted = Color(0xFF8E849E),
        line = Color(0xFFDDD4EA),
        green = Color(0xFF8B6BC4),
        greenDeep = Color(0xFF6F52A8),
        greenPale = Color(0xFFE8DFF5),
        card = Color(0xFFFBF8FF),
    )
    AppThemeId.PAPER -> YunColors(
        bgTop = Color(0xFFFAFAFA),
        bgMid = Color(0xFFF7F7F7),
        bgBot = Color(0xFFF3F3F3),
        ink = Color(0xFF1A1A1A),
        inkSoft = Color(0xFF333333),
        muted = Color(0xFF888888),
        line = Color(0xFFE5E5E5),
        green = Color(0xFF333333),
        greenDeep = Color(0xFF111111),
        greenPale = Color(0xFFEEEEEE),
        card = Color.White,
    )
}

@Composable
fun YunPlayerTheme(
    themeId: AppThemeId = AppThemeId.MINT,
    content: @Composable () -> Unit,
) {
    val c = colorsFor(themeId)
    val dark = themeId == AppThemeId.NIGHT || isSystemInDarkTheme() && themeId == AppThemeId.PAPER
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.green,
            onPrimary = Color.White,
            secondary = c.greenDeep,
            background = c.bgBot,
            surface = c.card,
            onBackground = c.ink,
            onSurface = c.ink,
            outline = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.green,
            onPrimary = Color.White,
            secondary = c.greenDeep,
            background = c.bgBot,
            surface = c.card,
            onBackground = c.ink,
            onSurface = c.ink,
            outline = c.line,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
