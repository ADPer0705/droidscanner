package com.idiots3.riskscanner.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.idiots3.riskscanner.model.InstallProvenance
import com.idiots3.riskscanner.model.PermissionCategory
import com.idiots3.riskscanner.model.PostureStatus
import com.idiots3.riskscanner.model.RiskLevel
import com.idiots3.riskscanner.ui.theme.RiskColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun riskColor(level: RiskLevel): Color {
    val dark = isSystemInDarkTheme()
    return when (level) {
        RiskLevel.CRITICAL -> if (dark) RiskColors.CriticalDark else RiskColors.Critical
        RiskLevel.HIGH -> if (dark) RiskColors.HighDark else RiskColors.High
        RiskLevel.MEDIUM -> if (dark) RiskColors.MediumDark else RiskColors.Medium
        RiskLevel.LOW -> if (dark) RiskColors.LowDark else RiskColors.Low
    }
}

@Composable
fun riskBgColor(level: RiskLevel): Color {
    val dark = isSystemInDarkTheme()
    return when (level) {
        RiskLevel.CRITICAL -> if (dark) RiskColors.CriticalBackgroundDark else RiskColors.CriticalBackground
        RiskLevel.HIGH -> if (dark) RiskColors.HighBackgroundDark else RiskColors.HighBackground
        RiskLevel.MEDIUM -> if (dark) RiskColors.MediumBackgroundDark else RiskColors.MediumBackground
        RiskLevel.LOW -> if (dark) RiskColors.LowBackgroundDark else RiskColors.LowBackground
    }
}

@Composable
fun postureStatusColor(status: PostureStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        PostureStatus.PROTECTED -> if (dark) RiskColors.LowDark else RiskColors.Low
        PostureStatus.REVIEW -> if (dark) RiskColors.HighDark else RiskColors.High
        PostureStatus.AT_RISK -> if (dark) RiskColors.CriticalDark else RiskColors.Critical
        PostureStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun healthScoreColor(score: Int): Color = when {
    score >= 75 -> riskColor(RiskLevel.LOW)
    score >= 50 -> riskColor(RiskLevel.MEDIUM)
    score >= 25 -> riskColor(RiskLevel.HIGH)
    else -> riskColor(RiskLevel.CRITICAL)
}

fun riskLevelForScore(score: Int): RiskLevel = when {
    score >= 76 -> RiskLevel.CRITICAL
    score >= 51 -> RiskLevel.HIGH
    score >= 26 -> RiskLevel.MEDIUM
    else -> RiskLevel.LOW
}

@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    Surface(
        color = riskBgColor(riskLevel),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = riskLevel.label.uppercase(),
            color = riskColor(riskLevel),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SmallRiskCircle(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val color = riskColor(riskLevel)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(42.dp)
    ) {
        CircularProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeWidth = 3.5.dp
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun InstallerTag(
    source: String,
    provenance: InstallProvenance? = null,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val effective = provenance ?: AppInfoCompatProvenance(source)
    val (bgColor, textColor) = when (effective) {
        InstallProvenance.OFFICIAL_STORE ->
            Pair(
                if (dark) RiskColors.LowBackgroundDark else RiskColors.LowBackground,
                if (dark) RiskColors.LowDark else RiskColors.Low
            )
        InstallProvenance.ALTERNATIVE_STORE ->
            Pair(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer
            )
        InstallProvenance.PREINSTALLED_SYSTEM ->
            Pair(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        InstallProvenance.DEVICE_MIGRATION ->
            Pair(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
        InstallProvenance.BROWSER_OR_APP_SIDELOAD,
        InstallProvenance.PACKAGE_INSTALLER,
        InstallProvenance.ADB_OR_SHELL,
        InstallProvenance.UNKNOWN ->
            Pair(
                if (dark) RiskColors.CriticalBackgroundDark else RiskColors.CriticalBackground,
                if (dark) RiskColors.CriticalDark else RiskColors.Critical
            )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = source,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun AppInfoCompatProvenance(source: String): InstallProvenance =
    com.idiots3.riskscanner.model.AppInfo.inferProvenance(source, isSystemApp = false)

/**
 * Lazy-loading app icon LRU cache (96 entries) so hundreds of apps never fill memory.
 */
object AppIconCache {
    private val cache = LruCache<String, Drawable>(96)

    fun get(context: Context, packageName: String): Drawable? {
        cache.get(packageName)?.let { return it }
        return try {
            context.packageManager.getApplicationIcon(packageName).also { cache.put(packageName, it) }
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun AppIconImage(packageName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(initialValue = null, key1 = packageName) {
        value = packageName?.let { withContext(Dispatchers.IO) { AppIconCache.get(context, it) } }
    }
    val current = drawable
    if (current != null) {
        val bitmap = remember(current) { current.toBitmap(96, 96).asImageBitmap() }
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = "App icon",
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Android,
                contentDescription = "App icon",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun getCategoryIcon(category: PermissionCategory): ImageVector = when (category) {
    PermissionCategory.SPECIAL_ACCESS -> Icons.Filled.Security
    PermissionCategory.SMS -> Icons.Filled.Sms
    PermissionCategory.CONTACTS_CALL_LOGS -> Icons.Filled.Contacts
    PermissionCategory.CAMERA_MICROPHONE -> Icons.Filled.Videocam
    PermissionCategory.LOCATION -> Icons.Filled.LocationOn
    PermissionCategory.PHONE_IDENTITY -> Icons.Filled.PhoneAndroid
    PermissionCategory.STORAGE_MEDIA -> Icons.Filled.Folder
    PermissionCategory.BACKGROUND_PERSISTENCE -> Icons.Filled.Sync
    PermissionCategory.NETWORK -> Icons.Filled.Wifi
    PermissionCategory.OTHER -> Icons.Filled.MoreHoriz
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown"
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "${"%.2f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000L -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000L -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
