package com.communicate.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.communicate.app.data.model.PeerRiderState
import java.util.Locale

@Composable
fun RiderMapWidget(
    localLat: Double,
    localLng: Double,
    localBearing: Float,
    peerState: PeerRiderState,
    modifier: Modifier = Modifier
) {
    val isPeerConnected = peerState.isConnected
    val peerLat = peerState.latitude
    val peerLng = peerState.longitude

    // Calculate distance between local and peer rider in meters & feet
    val distanceMeters = if (isPeerConnected && localLat != 0.0 && peerLat != 0.0) {
        calculateDistanceMeters(localLat, localLng, peerLat, peerLng)
    } else 0f

    val distanceFeet = (distanceMeters * 3.28084f).toInt()
    val distanceText = if (!isPeerConnected) {
        "Peer Disconnected"
    } else if (distanceMeters < 1000) {
        "$distanceFeet ft away"
    } else {
        String.format(Locale.US, "%.2f mi away", distanceMeters * 0.000621371f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F172A))
            .padding(12.dp)
    ) {
        // Offline Radar Canvas Map
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = Math.min(size.width, size.height) / 2f - 16.dp.toPx()

            // Concentric radar rings (representing distance grid)
            drawCircle(
                color = Color(0xFF334155),
                radius = maxRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF1E293B),
                radius = maxRadius * 0.66f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF1E293B),
                radius = maxRadius * 0.33f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Crosshair lines
            drawLine(
                color = Color(0xFF334155),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = Color(0xFF334155),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.5.dp.toPx()
            )

            // 1. Draw Local Rider (Center Emerald Triangle)
            val userPath = Path().apply {
                moveTo(center.x, center.y - 12.dp.toPx())
                lineTo(center.x - 8.dp.toPx(), center.y + 10.dp.toPx())
                lineTo(center.x + 8.dp.toPx(), center.y + 10.dp.toPx())
                close()
            }
            drawPath(path = userPath, color = Color(0xFF10B981)) // Emerald Green
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = center)

            // 2. Draw Peer Rider position relative to local center (Heading-Up)
            if (isPeerConnected && localLat != 0.0 && peerLat != 0.0) {
                val dLat = peerLat - localLat
                val latRad = Math.toRadians(localLat)
                val dLng = (peerLng - localLng) * Math.cos(latRad)

                // Map lat/lng offset to radar screen coordinates (500 meter range scaling)
                val scaleFactor = maxRadius / 0.0045 // Approx 500 meters lat degree
                val eastPx = dLng * scaleFactor
                val northPx = dLat * scaleFactor

                // Rotate by localBearing so "Up" on the radar matches the rider's heading
                val bearingRad = Math.toRadians(localBearing.toDouble())
                val sinB = Math.sin(bearingRad)
                val cosB = Math.cos(bearingRad)

                val rightPx = (eastPx * cosB - northPx * sinB).toFloat()
                val forwardPx = (eastPx * sinB + northPx * cosB).toFloat()

                val rawDx = rightPx
                val rawDy = -forwardPx
                val distFromCenter = Math.hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()
                val clampScale = if (distFromCenter > maxRadius && distFromCenter > 0f) {
                    maxRadius / distFromCenter
                } else {
                    1f
                }

                val peerPos = Offset(center.x + rawDx * clampScale, center.y + rawDy * clampScale)

                // Peer connection line
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = 0.6f),
                    start = center,
                    end = peerPos,
                    strokeWidth = 2.dp.toPx()
                )

                // Peer Pulse Outer Ring
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.3f),
                    radius = 16.dp.toPx(),
                    center = peerPos
                )
                // Peer Cyan Dot
                drawCircle(
                    color = Color(0xFF38BDF8), // Cyan
                    radius = 8.dp.toPx(),
                    center = peerPos
                )
            }
        }

        // Rider Distance Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color(0xFF1E293B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = peerState.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = distanceText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPeerConnected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
            )
        }

        // Live P2P Telemetry Freshness Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(
                    if (isPeerConnected) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isPeerConnected) Color(0xFF10B981) else Color(0xFF64748B))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isPeerConnected) "LIVE" else "OFFLINE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = if (isPeerConnected) Color(0xFF10B981) else Color(0xFF94A3B8)
            )
        }
    }
}

private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val r = 6371000f // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (r * c).toFloat()
}
