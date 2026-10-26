package com.cleantube.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colours of ONE video's card: taken from its own thumbnail (same pipeline as the app theme), so every video in a
 * list has its own tint. Until the thumbnail is read (or when "colours from the video" is off in Settings) these are
 * simply the normal app colours, and they fade to the video's colours when ready.
 */
class VideoTint(
    val container: Color, val onContainer: Color, val muted: Color,
    val primary: Color, val track: Color, val badge: Color, val onBadge: Color
)

@Composable
fun rememberVideoTint(videoId: String): VideoTint {
    val s = rememberThumbScheme(videoId)
    val spec = tween<Color>(500)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "videoTint").value
    return VideoTint(
        container = a(s?.surfaceContainerHigh ?: Card),
        onContainer = a(s?.onSurface ?: TextPrimary),
        muted = a(s?.onSurfaceVariant ?: TextMuted),
        primary = a(s?.primary ?: Accent),
        track = a(s?.secondaryContainer ?: SecondaryContainer),
        badge = a(s?.primaryContainer ?: SecondaryContainer),
        onBadge = a(s?.onPrimaryContainer ?: OnSecondaryContainer)
    )
}
