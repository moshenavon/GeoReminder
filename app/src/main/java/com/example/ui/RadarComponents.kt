package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GeoZoneEntity
import com.example.data.ZoneOccupancyState
import com.example.ui.theme.LocalRadarAccents
import com.example.ui.theme.RadarAmber
import com.example.ui.theme.RadarCoral
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import com.example.util.AlertBannerEvent
import com.example.util.LocationEvaluator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HeroStatusBanner(
    radarState: RadarUiState,
    isPlayingSound: Boolean,
    activeSoundLabel: String?,
    onToggleMonitoring: (Boolean) -> Unit,
    onSampleNow: () -> Unit,
    onStopSound: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_status_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        val accents = LocalRadarAccents.current
        Box(modifier = Modifier.fillMaxWidth()) {
            // matchParentSize: the background follows the content height, so there is no
            // empty strip below the last row.
            Image(
                painter = painterResource(id = R.drawable.img_radar_hero_1794737610243),
                contentDescription = "רקע רדאר",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(colors = accents.heroScrim))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (radarState.isMonitoringActive) RadarEmerald else RadarAmber)
                        )
                        Column {
                            Text(
                                text = if (radarState.isMonitoringActive) "ניטור מיקום פעיל" else "ניטור מיקום מושהה",
                                style = MaterialTheme.typography.titleLarge,
                                color = accents.heroTitle
                            )
                            Text(
                                text = radarState.statusMessageHe,
                                style = MaterialTheme.typography.bodyMedium,
                                color = accents.heroSubtitle
                            )
                        }
                    }

                    Switch(
                        checked = radarState.isMonitoringActive,
                        onCheckedChange = onToggleMonitoring,
                        modifier = Modifier.testTag("monitoring_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = String.format(
                                Locale.US,
                                "קואורדינטות: %.4f, %.4f",
                                radarState.currentLat,
                                radarState.currentLon
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = RadarCyan
                        )
                        val sampleTimeStr = radarState.lastSampleTimeMillis?.let {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "טרם נדגם"
                        Text(
                            text = "דגימה אחרונה: $sampleTimeStr • הבאה בעוד ${radarState.secondsToNextSample} שנ׳",
                            style = MaterialTheme.typography.labelMedium,
                            color = accents.heroMeta
                        )
                    }

                    FilledTonalButton(
                        onClick = onSampleNow,
                        modifier = Modifier.testTag("sample_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "דגום מיקום עכשיו",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("דגום כעת")
                    }
                }

                AnimatedVisibility(
                    visible = isPlayingSound,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        onClick = onStopSound,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadarCoral,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stop_sound_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "השתק צלצול"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "השתק צלצול מתנגן (${activeSoundLabel ?: "התראה"})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveAlertBannerCard(
    alert: AlertBannerEvent,
    onDismiss: () -> Unit,
    onStopSound: () -> Unit
) {
    val accentColor = if (alert.isEntry) RadarEmerald else RadarCoral
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, accentColor, RoundedCornerShape(20.dp))
            .testTag("active_alert_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (alert.isEntry) Icons.Default.Login else Icons.Default.Logout,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (alert.isEntry) {
                                "התראת כניסה לרדיוס: ${alert.zoneName}"
                            } else {
                                "התראת יציאה מהרדיוס: ${alert.zoneName}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "מרחק מדוד: ${alert.distanceMeters.toInt()} מ׳ (רדיוס: ${alert.radiusMeters.toInt()} מ׳)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onStopSound()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dismiss_alert_banner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "סגור התראה"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "צלצול מופעל: ${alert.ringtoneName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onStopSound,
                    modifier = Modifier.testTag("banner_stop_sound_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("עצור צלצול")
                }
            }
        }
    }
}

@Composable
fun InteractiveRadarVisualizerCard(
    zone: GeoZoneEntity,
    currentLat: Double,
    currentLon: Double,
    onSimulateEntry: () -> Unit,
    onSimulateExit: () -> Unit,
    onTestEntrySound: () -> Unit,
    onTestExitSound: () -> Unit
) {
    val distanceMeters = LocationEvaluator.calculateDistanceMeters(
        currentLat,
        currentLon,
        zone.latitude,
        zone.longitude
    )
    val isInside = distanceMeters <= zone.radiusMeters
    val isInTimeWindow = zone.isWithinTimeWindow(System.currentTimeMillis())

    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("radar_visualizer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "רדאר אזור: ${zone.name}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isInTimeWindow) RadarEmerald else RadarAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "חלון דגימה: ${zone.formatTimeWindow()} (${if (isInTimeWindow) "פעיל כעת" else "מחוץ לטווח השעות"})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isInTimeWindow) RadarEmerald else RadarAmber
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isInside) RadarEmerald.copy(alpha = 0.18f) else RadarCoral.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = if (isInside) "בתוך הרדיוס" else "מחוץ לרדיוס",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isInside) RadarEmerald else RadarCoral,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Radar Canvas
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                val cyan = RadarCyan
                val emerald = RadarEmerald
                val coral = RadarCoral
                val discColor = LocalRadarAccents.current.radarDisc
                val primaryRingColor = if (isInside) emerald else cyan
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxCanvasRadius = size.minDimension / 2f - 8.dp.toPx()
                    val geofenceRadiusPx = maxCanvasRadius * 0.65f

                    // Outer dark radar disc
                    drawCircle(
                        color = discColor,
                        radius = maxCanvasRadius,
                        center = center
                    )

                    // Concentric range rings
                    for (fraction in listOf(0.32f, 0.65f, 1.0f)) {
                        drawCircle(
                            color = cyan.copy(alpha = 0.22f),
                            radius = maxCanvasRadius * fraction,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Crosshair lines
                    drawLine(
                        color = cyan.copy(alpha = 0.18f),
                        start = Offset(center.x - maxCanvasRadius, center.y),
                        end = Offset(center.x + maxCanvasRadius, center.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = cyan.copy(alpha = 0.18f),
                        start = Offset(center.x, center.y - maxCanvasRadius),
                        end = Offset(center.x, center.y + maxCanvasRadius),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Active Geofence Radius fill & border
                    drawCircle(
                        color = primaryRingColor.copy(alpha = 0.15f),
                        radius = geofenceRadiusPx,
                        center = center
                    )
                    drawCircle(
                        color = primaryRingColor,
                        radius = geofenceRadiusPx,
                        center = center,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                        )
                    )

                    // Sweeping radar arc when time window is active
                    if (isInTimeWindow) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryRingColor.copy(alpha = 0.05f),
                                    primaryRingColor.copy(alpha = 0.35f)
                                ),
                                center = center
                            ),
                            startAngle = sweepAngle - 60f,
                            sweepAngle = 60f,
                            useCenter = true,
                            topLeft = Offset(center.x - maxCanvasRadius, center.y - maxCanvasRadius),
                            size = Size(maxCanvasRadius * 2f, maxCanvasRadius * 2f)
                        )
                    }

                    // Zone Center Pin
                    drawCircle(
                        color = cyan,
                        radius = 6.dp.toPx(),
                        center = center
                    )

                    // Phone Current Position Dot relative to zone radius
                    val normalizedRatio = (distanceMeters / zone.radiusMeters.coerceAtLeast(1f))
                        .coerceIn(0f, 1.45f)
                    val dotDistancePx = normalizedRatio * geofenceRadiusPx
                    val angleRad = Math.toRadians(45.0)
                    val phoneOffset = Offset(
                        x = center.x + (dotDistancePx * cos(angleRad)).toFloat(),
                        y = center.y - (dotDistancePx * sin(angleRad)).toFloat()
                    )

                    val dotColor = if (isInside) emerald else coral
                    drawCircle(
                        color = dotColor.copy(alpha = 0.35f),
                        radius = 13.dp.toPx(),
                        center = phoneOffset
                    )
                    drawCircle(
                        color = dotColor,
                        radius = 7.dp.toPx(),
                        center = phoneOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = phoneOffset,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Distance & Radius Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricPill(
                    label = "מרחק נוכחי מהמרכז",
                    value = "${distanceMeters.toInt()} מ׳",
                    valueColor = if (isInside) RadarEmerald else RadarCoral
                )
                MetricPill(
                    label = "רדיוס מוגדר",
                    value = "${zone.radiusMeters.toInt()} מ׳",
                    valueColor = RadarCyan
                )
                MetricPill(
                    label = "תדירות דגימה",
                    value = "כל ${zone.samplingIntervalSeconds} שנ׳",
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
            }

            // Ringtone Summary & Audio Preview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestEntrySound,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_entry_sound_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = RadarEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "כניסה: ${zone.entryRingtoneDisplayName()}",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = onTestExitSound,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_exit_sound_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = RadarCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "יציאה: ${zone.exitRingtoneDisplayName()}",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Field Testing Controls (Simulate Entry / Exit to immediately test notifications & ringtones)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = RadarCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "בדיקת התראות וצלצולים בזמן אמת (הדמיית תנועה לרדיוס וממנו):",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSimulateEntry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadarEmerald,
                                contentColor = LocalRadarAccents.current.onEmerald
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_entry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("דמה כניסה לרדיוס", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onSimulateExit,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadarCoral,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_exit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("דמה יציאה מהרדיוס", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}
