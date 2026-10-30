package net.nekomo.nex.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.nekomo.nex.data.AppId
import java.text.DateFormat
import java.util.Date

fun AppId.icon(): ImageVector = when (this) {
    AppId.NEKOMO -> Icons.Default.Pets
    AppId.MIHON -> Icons.AutoMirrored.Filled.MenuBook
    AppId.ANIYOMI -> Icons.Default.PlayCircle
    AppId.CLOUDSTREAM -> Icons.Default.Movie
    AppId.ZANGETSU -> Icons.Default.Layers
}

fun formatTime(t: Long?): String = if (t == null || t == 0L) "never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

fun formatBytes(b: Long): String = when {
    b >= 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/** Rounded, subtly bordered row/card. Focusable for D-pad; shows a purple ring when focused. */
@Composable
fun NexCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = src, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick) else Modifier)
            .border(if (focused) BorderStroke(2.dp, NexPurple) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)), shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
    ) { content() }
}

@Composable
fun ScreenIntro(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp * (if (LocalIsTv.current) 1.25f else 1f), letterSpacing = 1.3.sp, modifier = modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
fun Pill(text: String, color: Color = NexMuted) {
    Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun ExtIcon(url: String?, fallback: ImageVector = Icons.Default.Extension, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        else Icon(fallback, null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    NexCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    ListRow(title, subtitle, trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }, onClick = if (enabled) ({ onChecked(!checked) }) else null)
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        action?.invoke()
    }
}

@Suppress("unused")
private fun Modifier.dpadFocusable() = focusable()
