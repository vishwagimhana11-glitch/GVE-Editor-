package com.gima.gve.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GveColors {
    val Canvas = Color(0xFF0E1015)
    val Surface = Color(0xFF161922)
    val SurfaceElevated = Color(0xFF1E222E)
    val SurfacePressed = Color(0xFF262B3A)
    val Border = Color(0xFF2A3040)

    val AccentPrimary = Color(0xFF6366F1) // Electric Indigo
    val AccentSecondary = Color(0xFF38BDF8) // Electric Blue
    val TrackVideo = Color(0xFF1E3A5F)
    val TrackOverlay = Color(0xFF134E4A)
    val TrackAudio = Color(0xFF452707)
    val TrackText = Color(0xFF3B1D5E)

    val AudioAmber = Color(0xFFF59E0B)
    val TextViolet = Color(0xFFA855F7)
    val OverlayTeal = Color(0xFF14B8A6)
    val Danger = Color(0xFFEF4444)
    val Success = Color(0xFF10B981)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextMuted = Color(0xFF94A3B8)
}

private val GveDarkColorScheme = darkColorScheme(
    primary = GveColors.AccentPrimary,
    onPrimary = Color.White,
    secondary = GveColors.AccentSecondary,
    onSecondary = Color(0xFF082F49),
    background = GveColors.Canvas,
    onBackground = GveColors.TextPrimary,
    surface = GveColors.Surface,
    onSurface = GveColors.TextPrimary,
    surfaceVariant = GveColors.SurfaceElevated,
    onSurfaceVariant = GveColors.TextMuted,
    outline = GveColors.Border,
    error = GveColors.Danger
)

@Composable
fun GveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GveDarkColorScheme,
        content = content
    )
}

/**
 * Original GVE Editor vector emblem: dual-frame aperture with a timeline playhead notch.
 */
@Composable
fun GveBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(GveColors.AccentPrimary, GveColors.AccentSecondary)
            ),
            topLeft = Offset(stroke * 0.5f, stroke * 0.5f),
            size = Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f),
            style = Stroke(width = stroke)
        )

        // Inner timeline bar
        drawRoundRect(
            color = GveColors.AccentSecondary,
            topLeft = Offset(w * 0.22f, h * 0.56f),
            size = Size(w * 0.56f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // Vertical playhead needle
        drawLine(
            color = Color.White,
            start = Offset(w * 0.5f, h * 0.22f),
            end = Offset(w * 0.5f, h * 0.80f),
            strokeWidth = stroke * 0.9f
        )
    }
}
