package com.cleantube.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.Reco
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shown right after the first sign-in while the app reads the whole watch history and works out what you like:
 * pulsing rings, dots orbiting a play button, and the step it is on. It fades away when the feed is ready
 * (or when you tap "Continue": the work goes on in the background).
 */
@Composable
fun AnalysingOverlay() {
    AnimatedVisibility(
        visible = Reco.seeding && !Reco.seedHidden,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(500))
    ) {
        val inf = rememberInfiniteTransition(label = "analyse")
        val spin by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "spin")
        val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse")
        val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
        val progress by animateFloatAsState(Reco.seedProgress, tween(500), label = "progress")
        val ring = Accent
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg.copy(alpha = 0.97f))
                .pointerInput(Unit) { }   // swallow touches
        ) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        // three rings growing outwards one after the other
                        for (k in 0..2) {
                            val f = (pulse + k / 3f) % 1f
                            drawCircle(ring.copy(alpha = (1f - f) * 0.35f), radius = 44.dp.toPx() + f * 66.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                        }
                        // dots orbiting, each on its own radius and speed
                        for (i in 0 until 8) {
                            val a = (spin * (if (i % 2 == 0) 1f else -1f) + i / 8f) * 2f * PI.toFloat()
                            val r = (62 + (i % 3) * 14).dp.toPx()
                            val d = (3 + (i % 3)).dp.toPx()
                            drawCircle(ring.copy(alpha = 0.9f - (i % 3) * 0.2f), radius = d, center = Offset(c.x + cos(a) * r, c.y + sin(a) * r))
                        }
                    }
                    Box(
                        Modifier.size(64.dp).padding(top = (bob * 4f).dp).clip(CircleShape).background(ring),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = OnAccent, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Getting to know you", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                AnimatedContent(
                    targetState = Reco.seedStage,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                    label = "stage"
                ) { t ->
                    Text(t, color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(22.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = ring,
                    trackColor = Color(0x22888888)
                )
                Spacer(Modifier.height(26.dp))
                Text(
                    "Continue",
                    color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).bouncyClickable(pressedScale = 0.94f, onClick = { Reco.seedHidden = true })
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}
