package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.iblu01.portallauncher.CameraPair
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Full-screen camera popup shown when a mapped trigger sensor fires. Streams a
 * refreshed camera_proxy snapshot (auth token). Dismisses on the X, or 10s after the
 * trigger clears.
 */
@Composable
fun CameraOverlay(pair: CameraPair, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val trigger = rememberEntity(pair.trigger)
    val camera = rememberEntity(pair.camera)

    LaunchedEffect(trigger?.state) {
        if (trigger != null && !trigger.state.equals("on", true)) {
            kotlinx.coroutines.delay(10_000)
            onDismiss()
        }
    }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2_000); tick++ } }
    val url = "${prefs.haUrl.trimEnd('/')}/api/camera_proxy/${pair.camera}?_t=$tick"

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.8f).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(camera?.name ?: "Caméra", style = AppleTypography.titleLarge, color = AppleColors.primary)
            Spacer(Modifier.height(14.dp))
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .addHeader("Authorization", "Bearer ${prefs.haToken}")
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .crossfade(false)
                    .build(),
                contentDescription = camera?.name ?: "Caméra",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(AppleShapes.panel).background(Color.Black),
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(AppleColors.frostedFill)
                    .border(0.5.dp, AppleColors.frostedBorder, CircleShape).appleClickable(onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Fermer", tint = AppleColors.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}
