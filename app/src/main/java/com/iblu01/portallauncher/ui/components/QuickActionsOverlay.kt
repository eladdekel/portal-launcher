package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.blurCompat

/** One launchable app surfaced in the overlay. */
data class AppEntry(val label: String, val packageName: String, val activityName: String)

/**
 * Long-press quick actions. A blurred backdrop with a centered frosted panel that
 * scales up on entry. Tapping the backdrop or swiping the panel down dismisses it.
 */
@Composable
fun QuickActionsOverlay(
    visible: Boolean,
    apps: List<AppEntry>,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onOpenPlayground: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
) {
    var showingApps by remember { mutableStateOf(false) }
    // Reset to the root menu whenever the overlay is re-opened.
    LaunchedEffect(visible) { if (!visible) showingApps = false }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppleMotion.spring()) + scaleIn(initialScale = 0.92f, animationSpec = AppleMotion.spring()),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred, dimmed backdrop — a SIBLING of the panel so the blur never
            // bleeds onto the menu. Tapping it dismisses.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blurCompat(40.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )

            // Panel layer, drawn on top and never blurred. Empty area lets taps fall
            // through to the backdrop below.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(AppleShapes.panel)
                    .background(AppleColors.elevated.copy(alpha = 0.96f), AppleShapes.panel)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* swallow */ }
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { if (drag > 120f) onDismiss(); drag = 0f },
                            onVerticalDrag = { _, dy -> if (dy > 0) drag += dy }
                        )
                    }
                    .padding(8.dp)
            ) {
                if (!showingApps) {
                    MenuRow(Icons.Outlined.Settings, "Réglages") { onDismiss(); onSettings() }
                    MenuDivider()
                    MenuRow(Icons.Outlined.Apps, "Applications") { showingApps = true }
                    MenuDivider()
                    MenuRow(Icons.Outlined.Tune, "Composants (test)") { onDismiss(); onOpenPlayground() }
                } else {
                    MenuRow(Icons.Outlined.ChevronLeft, "Retour") { showingApps = false }
                    MenuDivider()
                    if (apps.isEmpty()) {
                        Text(
                            "Aucune application trouvée",
                            style = AppleTypography.titleMedium,
                            color = AppleColors.secondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        val context = LocalContext.current
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.heightIn(max = 360.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(apps) { app ->
                                val icon = remember(app.packageName) {
                                    try {
                                        context.packageManager.getApplicationIcon(app.packageName)
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .appleClickable { onDismiss(); onLaunchApp(app) }
                                        .padding(4.dp)
                                ) {
                                    AsyncImage(
                                        model = icon,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = app.label,
                                        style = AppleTypography.bodySmall,
                                        color = AppleColors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = AppleColors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 0.5.dp, max = 0.5.dp)
            .background(AppleColors.quaternary)
    )
}
