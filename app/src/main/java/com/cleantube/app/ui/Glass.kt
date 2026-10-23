package com.cleantube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Frosted-glass surface: a see-through tint, a soft light sheen from the top and a hairline edge that is bright on
 * the top-left and fades towards the bottom-right (the way light catches the rim of a glass pill).
 * [strength] 0..1 = how milky it is (0.5 is normal; selected / raised pieces use more).
 */
fun Modifier.glass(shape: Shape, strength: Float = 0.5f, tint: Color? = null): Modifier = composed {
    val dark = isSystemInDarkTheme()
    val base = tint ?: if (dark) Color.White else Color.White
    val fillA = if (dark) 0.05f + 0.14f * strength else 0.40f + 0.38f * strength
    val sheenTop = if (dark) 0.10f else 0.35f
    val rimA = if (dark) 0.42f else 0.95f
    val rimB = if (dark) 0.06f else 0.10f
    val rimEnd = if (dark) Color.White else Color.Black
    this
        .clip(shape)
        .background(base.copy(alpha = fillA))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = sheenTop), Color.Transparent)))
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = rimA), rimEnd.copy(alpha = rimB), Color.White.copy(alpha = rimA * 0.45f))),
            shape
        )
}

/** Glass tinted with the theme's own surface colour instead of white (for panels over plain backgrounds). */
fun Modifier.glassSurface(shape: Shape, alpha: Float = 0.78f): Modifier = composed {
    this
        .clip(shape)
        .background(NavBg.copy(alpha = alpha))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.07f else 0.30f), Color.Transparent)))
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.38f else 0.9f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.16f else 0.4f)
                )
            ),
            shape
        )
}
