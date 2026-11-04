package com.communicate.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onInitializationComplete: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.85f) }

    val infiniteTransition = rememberInfiniteTransition(label = "SplashPulsing")
    
    // Pulsing radar ripple animation
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleScale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleAlpha"
    )

    // Sequential initialization steps animation
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        scaleAnim.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        
        delay(400L)
        stepIndex = 1 // Audio engine ready
        delay(400L)
        stepIndex = 2 // GPS engine ready
        delay(400L)
        stepIndex = 3 // P2P mesh ready
        delay(600L)
        
        onInitializationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // CENTER LOGO & ANIMATED RADAR GLOW
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(alphaAnim.value)
                    .scale(scaleAnim.value)
            ) {
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Radar Background Waves
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.width / 2f

                        // Outer glowing ripple
                        drawCircle(
                            color = Color(0xFF06B6D4).copy(alpha = rippleAlpha * 0.4f),
                            radius = maxRadius * rippleScale.coerceAtMost(1.3f),
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Static background radar rings
                        drawCircle(
                            color = Color(0xFF1E293B),
                            radius = maxRadius * 0.85f,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF0F172A),
                            radius = maxRadius * 0.55f,
                            center = center
                        )
                    }

                    // Cybernetic Communicator Emblem
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF020617)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val w = size.width
                            val h = size.height

                            // Radar Arc
                            drawArc(
                                color = Color(0xFF38BDF8),
                                startAngle = -45f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Color(0xFF10B981),
                                startAngle = -35f,
                                sweepAngle = 70f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Dynamic Equalizer Sound Wave
                            val wavePath = Path().apply {
                                moveTo(w * 0.15f, h * 0.5f)
                                lineTo(w * 0.30f, h * 0.5f)
                                lineTo(w * 0.40f, h * 0.25f)
                                lineTo(w * 0.50f, h * 0.75f)
                                lineTo(w * 0.60f, h * 0.15f)
                                lineTo(w * 0.70f, h * 0.80f)
                                lineTo(w * 0.80f, h * 0.5f)
                                lineTo(w * 0.88f, h * 0.5f)
                            }
                            drawPath(
                                path = wavePath,
                                color = Color(0xFF10B981),
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Brand Headline
                Text(
                    text = "COMMUNICATE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "OFFLINE BIKE COMMS & TELEMETRY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFF38BDF8)
                )
            }

            // BOTTOM SYSTEM DIAGNOSTIC / INITIALIZATION STATUS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alphaAnim.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Diagnostics checklist
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticItem(
                        label = "High-Accuracy GPS & Speedometer",
                        isReady = stepIndex >= 1
                    )
                    DiagnosticItem(
                        label = "AEC / Voice Communication Engine",
                        isReady = stepIndex >= 2
                    )
                    DiagnosticItem(
                        label = "P2P Wireless Mesh Transceiver",
                        isReady = stepIndex >= 3
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sleek progress line
                val progress = (stepIndex / 3f).coerceIn(0.1f, 1.0f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (stepIndex >= 3) "SYSTEM READY" else "INITIALIZING SUBSYSTEMS...",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (stepIndex >= 3) Color(0xFF10B981) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    isReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconColor = if (isReady) Color(0xFF10B981) else Color(0xFF475569)
        val textColor = if (isReady) Color.White else Color(0xFF64748B)

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
