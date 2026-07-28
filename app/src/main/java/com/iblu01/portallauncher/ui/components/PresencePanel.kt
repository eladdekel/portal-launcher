package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalHaStates
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Ambient top-left presence badge: just the overlapping avatars of whoever is home, no chrome.
 * Renders nothing when the house is empty. Tapping it opens the full [PresenceActions] panel.
 */
@Composable
fun PresenceIndicator(chip: LauncherChip?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val home = chip?.details?.filter { it.entityId.isNotBlank() && it.active }.orEmpty()
    if (home.isEmpty()) return

    Row(
        modifier = modifier
            .clip(CircleShape)
            .appleClickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        home.take(4).forEachIndexed { i, p ->
            // Overlap each avatar onto the previous; later ones sit on top for a clean stack.
            Box(Modifier.offset(x = (i * -11).dp).zIndex(i.toFloat())) {
                PersonAvatar(p.entityId, p.label, size = 34.dp, prefs = prefs)
            }
        }
    }
}

/** Who is home: overlapping avatars up top, then a row per person with status. */
@Composable
fun PresenceActions(chip: LauncherChip) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val people = chip.details.filter { it.entityId.isNotBlank() }
    val home = people.filter { it.active }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (home.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                home.take(5).forEachIndexed { i, p ->
                    Box(Modifier.offset(x = (i * -12).dp)) {
                        PersonAvatar(p.entityId, p.label, size = 44.dp, prefs = prefs)
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.height(4.dp))
        }
        people.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppleShapes.card)
                    .background(AppleColors.frostedFill, AppleShapes.card)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(p.entityId, p.label, size = 36.dp, prefs = prefs)
                Spacer(Modifier.width(12.dp))
                Text(p.label, style = AppleTypography.bodyLarge, color = AppleColors.primary, modifier = Modifier.weight(1f))
                Text(p.value, style = AppleTypography.bodySmall.copy(fontSize = 13.sp), color = if (p.active) AppleColors.active else AppleColors.secondary)
            }
        }
    }
}

@Composable
private fun PersonAvatar(entityId: String, name: String, size: androidx.compose.ui.unit.Dp, prefs: Prefs) {
    val context = LocalContext.current
    val picture = LocalHaStates.current[entityId]?.attributes?.optString("entity_picture")?.takeIf { it.isNotBlank() }
    val url = picture?.let { if (it.startsWith("http")) it else prefs.haUrl.trimEnd('/') + it }
    Box(
        Modifier.size(size).clip(CircleShape).background(AppleColors.accent.copy(alpha = 0.25f))
            .border(1.5.dp, AppleColors.frostedBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(url).addHeader("Authorization", "Bearer ${prefs.haToken}").crossfade(true).build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(name.take(1).uppercase(), style = AppleTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = AppleColors.primary)
        }
    }
}
