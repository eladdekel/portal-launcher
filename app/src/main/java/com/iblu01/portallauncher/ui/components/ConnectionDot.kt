package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.supportsLiveBlur

/**
 * Minimal iOS status-bar-style indicator: a small green/gray dot with a soft glow.
 */
@Composable
fun ConnectionDot(
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (connected) AppleColors.active else AppleColors.inactive,
        animationSpec = AppleMotion.spring(),
        label = "connectionColor"
    )
    // Glow halo (only where live blur exists) sitting behind the solid dot.
    if (supportsLiveBlur) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .size(10.dp)
                .blur(6.dp)
                .background(color.copy(alpha = 0.6f), CircleShape)
        )
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .padding(2.dp)
            .size(6.dp)
            .background(color, CircleShape)
    )
}

@Preview(backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun ConnectionDotPreview() {
    PortalTheme { ConnectionDot(connected = true) }
}
