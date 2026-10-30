package net.nekomo.nex.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import net.nekomo.nex.data.ThemeMode

val NexBg = Color(0xFF101114)
val NexSurface = Color(0xFF1B1D22)
val NexSurfaceHigh = Color(0xFF24272E)
val NexBorder = Color(0xFF30333A)
val NexMuted = Color(0xFF9A9DA6)
val NexAccent = Color(0xFFE5E7EB)
val NexPurple = Color(0xFF8B5CF6)
val NexPurpleSoft = Color(0xFFB9A2FF)
val NexGood = Color(0xFFB8D7C0)
val NexWarn = Color(0xFFE7C98D)
val NexBad = Color(0xFFF2A5A5)

/** True on Android TV / Google TV: bigger type, navigation rail, strong focus rings. */
val LocalIsTv = compositionLocalOf { false }

fun isTv(context: Context): Boolean {
    val ui = context.getSystemService(UiModeManager::class.java)
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature("android.software.leanback")
}

@Composable
fun NexTheme(mode: ThemeMode, tv: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    val bg = if (mode == ThemeMode.AMOLED) Color.Black else NexBg
    val scheme = if (dark) darkColorScheme(
        background = bg, surface = if (mode == ThemeMode.AMOLED) Color(0xFF121316) else NexSurface,
        surfaceVariant = NexSurfaceHigh, surfaceContainer = NexSurface, surfaceContainerHigh = NexSurfaceHigh,
        surfaceContainerHighest = NexSurfaceHigh, primary = NexPurpleSoft, onPrimary = Color(0xFF111216),
        primaryContainer = Color(0xFF2E2547), onPrimaryContainer = Color.White, secondary = NexMuted,
        secondaryContainer = Color(0xFF343840), onSecondaryContainer = Color.White, tertiary = NexAccent,
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = NexMuted, outline = NexBorder,
        outlineVariant = NexBorder, error = NexBad,
    ) else lightColorScheme(
        background = Color(0xFFF7F7F9), surface = Color.White, surfaceVariant = Color(0xFFEDEDF1),
        primary = Color(0xFF5B34D6), onPrimary = Color.White, secondary = Color(0xFF5F6370),
        onSurfaceVariant = Color(0xFF5F6370), outline = Color(0xFFD6D7DD),
    )
    val base = Typography()
    val scale = if (tv) 1.25f else 1f
    fun androidx.compose.ui.text.TextStyle.s() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    val typo = Typography(
        headlineSmall = base.headlineSmall.copy(fontSize = 24.sp).s(),
        titleLarge = base.titleLarge.s(), titleMedium = base.titleMedium.s(), titleSmall = base.titleSmall.s(),
        bodyLarge = base.bodyLarge.s(), bodyMedium = base.bodyMedium.s(), bodySmall = base.bodySmall.s(),
        labelLarge = base.labelLarge.s(), labelMedium = base.labelMedium.s(), labelSmall = base.labelSmall.s(),
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}
