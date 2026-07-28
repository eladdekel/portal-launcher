package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.PortalTheme

@Composable
fun GlassButton(
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) AppleColors.active else AppleColors.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .appleClickable(onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = AppleTypography.bodySmall.copy(fontSize = 12.sp), color = tint)
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true, widthDp = 380)
@Composable
private fun GlassButtonPreview() {
    PortalTheme {
        GlassButton("Ouvrir", Icons.Outlined.PlayArrow, active = true, onClick = {})
    }
}
