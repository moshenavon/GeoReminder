package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.LocationUtils
import com.example.data.GeoEventLogEntity
import com.example.data.GeoZoneEntity
import com.example.data.RingtonePreset
import com.example.ui.theme.LocalRadarAccents
import com.example.ui.theme.RadarAmber
import com.example.ui.theme.RadarCoral
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import com.example.util.LocationEvaluator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeoAlertScreen(viewModel: GeoAlertViewModel) {
    val context = LocalContext.current
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val eventLogs by viewModel.eventLogs.collectAsStateWithLifecycle()
    val radarState by viewModel.radarState.collectAsStateWithLifecycle()
    val isPlayingSound by viewModel.isPlayingSound.collectAsStateWithLifecycle()
    val activeSoundLabel by viewModel.activeSoundLabel.collectAsStateWithLifecycle()
    val locationMode by viewModel.locationMode.collectAsStateWithLifecycle()
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()

    // The first-run guide is shown on every launch until the user ticks "don't show again".
    var showGuide by rememberSaveable { mutableStateOf(!onboardingDone) }
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedZoneId by remember { mutableLongStateOf(0L) }
    var editingZone by remember { mutableStateOf<GeoZoneEntity?>(null) }
    var isEditorOpen by remember { mutableStateOf(false) }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // v2: geofencing needs "Allow all the time" (background) location on Android 10+.
    var hasBackgroundPermission by remember {
        mutableStateOf(LocationUtils.hasBackgroundLocationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermissions = granted
        hasBackgroundPermission = LocationUtils.hasBackgroundLocationPermission(context)
        if (granted) {
            viewModel.refreshMonitoring()
        }
    }

    var backgroundRequestStartedAt by remember { mutableLongStateOf(0L) }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundPermission = granted
        if (granted) {
            viewModel.refreshMonitoring()
        } else if (SystemClock.elapsedRealtime() - backgroundRequestStartedAt < 700L) {
            // Denied instantly = Android no longer shows the request (after repeated
            // denials), so send the user to the app's settings page instead.
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // Re-check permissions when returning from Settings, and run the live radar refresh
    // only while this screen is visible (background alerts come from geofencing).
    LifecycleResumeEffect(Unit) {
        val fg = LocationUtils.hasLocationPermission(context)
        val bg = LocationUtils.hasBackgroundLocationPermission(context)
        val changed = fg != hasPermissions || bg != hasBackgroundPermission
        hasPermissions = fg
        hasBackgroundPermission = bg
        if (changed) viewModel.refreshMonitoring()
        viewModel.onScreenVisible()
        onPauseOrDispose { viewModel.onScreenHidden() }
    }

    // Handle back navigation on secondary tabs
    if (currentTab != 0) {
        BackHandler { currentTab = 0 }
    }

    val activeZone = zones.find { it.id == selectedZoneId } ?: zones.firstOrNull()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (showGuide) {
            OnboardingScreen(
                locationMode = locationMode,
                onLocationModeChange = { viewModel.setLocationMode(it) },
                initialDontShowAgain = onboardingDone,
                onFinish = { dontShowAgain ->
                    viewModel.setOnboardingDone(dontShowAgain)
                    showGuide = false
                },
                onSkip = { showGuide = false }
            )
            return@CompositionLocalProvider
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = RadarCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "GeoAlert",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ניטור רדיוס מיקום והתראות לפי שעות",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                if ((currentTab == 0 || currentTab == 1) && zones.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingZone = null
                            isEditorOpen = true
                        },
                        containerColor = RadarCyan,
                        contentColor = LocalRadarAccents.current.onCyan,
                        modifier = Modifier.testTag("add_zone_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "הוספת אזור חדש")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("הגדר רדיוס חדש", fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 0) Icons.Filled.Radar else Icons.Outlined.Radar,
                                contentDescription = "רדאר וניטור"
                            )
                        },
                        label = { Text("רדאר") },
                        modifier = Modifier.testTag("nav_tab_radar")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 1) Icons.Filled.LocationOn else Icons.Outlined.LocationOn,
                                contentDescription = "אזורי התראה"
                            )
                        },
                        label = { Text("אזורים (${zones.size})") },
                        modifier = Modifier.testTag("nav_tab_zones")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 2) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "יומן אירועים"
                            )
                        },
                        label = { Text("יומן (${eventLogs.size})") },
                        modifier = Modifier.testTag("nav_tab_history")
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "הגדרות"
                            )
                        },
                        label = { Text("הגדרות") },
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                when (currentTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 640.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!hasPermissions) {
                                item {
                                    PermissionBannerCard(
                                        onRequestPermissions = {
                                            val perms = buildList {
                                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            }
                                            permissionLauncher.launch(perms.toTypedArray())
                                        }
                                    )
                                }
                            } else if (!hasBackgroundPermission &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ) {
                                item {
                                    PermissionBannerCard(
                                        title = "נדרש מיקום \"כל הזמן\"",
                                        body = "כדי לקבל התראות גם כשהאפליקציה סגורה, יש לבחור בהגדרות: הרשאות ← מיקום ← \"אפשר כל הזמן\".",
                                        buttonText = "אפשר כל הזמן",
                                        onRequestPermissions = {
                                            backgroundRequestStartedAt = SystemClock.elapsedRealtime()
                                            backgroundPermissionLauncher.launch(
                                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                            )
                                        }
                                    )
                                }
                            }

                            item {
                                HeroStatusBanner(
                                    radarState = radarState,
                                    isPlayingSound = isPlayingSound,
                                    activeSoundLabel = activeSoundLabel,
                                    onToggleMonitoring = { viewModel.toggleMonitoring(it) },
                                    onSampleNow = { viewModel.sampleNow() },
                                    onStopSound = { viewModel.stopPlayingSound() }
                                )
                            }

                            radarState.latestBannerAlert?.let { alert ->
                                item {
                                    ActiveAlertBannerCard(
                                        alert = alert,
                                        onDismiss = { viewModel.dismissBannerAlert() },
                                        onStopSound = { viewModel.stopPlayingSound() }
                                    )
                                }
                            }

                            if (zones.isEmpty()) {
                                item {
                                    EmptyZonesCard(
                                        onCreateZone = {
                                            editingZone = null
                                            isEditorOpen = true
                                        }
                                    )
                                }
                            }

                            if (zones.size > 1) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "בחר אזור לתצוגה ובדיקה ברדאר:",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            zones.forEach { z ->
                                                val selected = activeZone?.id == z.id
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = { selectedZoneId = z.id },
                                                    label = { Text("${z.name} (${z.radiusMeters.toInt()} מ׳)") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (activeZone != null) {
                                item {
                                    InteractiveRadarVisualizerCard(
                                        zone = activeZone,
                                        currentLat = radarState.currentLat,
                                        currentLon = radarState.currentLon,
                                        onSimulateEntry = {
                                            viewModel.simulateMovementRelativeToZone(
                                                zone = activeZone,
                                                moveInside = true
                                            )
                                        },
                                        onSimulateExit = {
                                            viewModel.simulateMovementRelativeToZone(
                                                zone = activeZone,
                                                moveInside = false
                                            )
                                        },
                                        onTestEntrySound = {
                                            viewModel.previewRingtone(
                                                preset = RingtonePreset.fromName(activeZone.entryRingtoneType),
                                                customUri = activeZone.entryCustomRingtoneUri,
                                                isEntry = true
                                            )
                                        },
                                        onTestExitSound = {
                                            viewModel.previewRingtone(
                                                preset = RingtonePreset.fromName(activeZone.exitRingtoneType),
                                                customUri = activeZone.exitCustomRingtoneUri,
                                                isEntry = false
                                            )
                                        }
                                    )
                                }
                            }

                            if (zones.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "סיכום אזורי רדיוס וזמני דגימה",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            items(zones, key = { it.id }) { zone ->
                                ZoneDetailCard(
                                    zone = zone,
                                    currentLat = radarState.currentLat,
                                    currentLon = radarState.currentLon,
                                    onToggleEnabled = { viewModel.toggleZoneEnabled(zone, it) },
                                    onEdit = {
                                        editingZone = zone
                                        isEditorOpen = true
                                    },
                                    onDelete = { viewModel.deleteZone(zone) },
                                    onPreviewEntry = {
                                        viewModel.previewRingtone(
                                            preset = RingtonePreset.fromName(zone.entryRingtoneType),
                                            customUri = zone.entryCustomRingtoneUri,
                                            isEntry = true
                                        )
                                    },
                                    onPreviewExit = {
                                        viewModel.previewRingtone(
                                            preset = RingtonePreset.fromName(zone.exitRingtoneType),
                                            customUri = zone.exitCustomRingtoneUri,
                                            isEntry = false
                                        )
                                    }
                                )
                            }
                        }
                    }

                    1 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 640.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Text(
                                    text = "הגדרת אזורי רדיוס, טווחי שעות וצלצולים",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            if (zones.isEmpty()) {
                                item {
                                    EmptyZonesCard(
                                        onCreateZone = {
                                            editingZone = null
                                            isEditorOpen = true
                                        }
                                    )
                                }
                            }

                            items(zones, key = { it.id }) { zone ->
                                ZoneDetailCard(
                                    zone = zone,
                                    currentLat = radarState.currentLat,
                                    currentLon = radarState.currentLon,
                                    onToggleEnabled = { viewModel.toggleZoneEnabled(zone, it) },
                                    onEdit = {
                                        editingZone = zone
                                        isEditorOpen = true
                                    },
                                    onDelete = { viewModel.deleteZone(zone) },
                                    onPreviewEntry = {
                                        viewModel.previewRingtone(
                                            preset = RingtonePreset.fromName(zone.entryRingtoneType),
                                            customUri = zone.entryCustomRingtoneUri,
                                            isEntry = true
                                        )
                                    },
                                    onPreviewExit = {
                                        viewModel.previewRingtone(
                                            preset = RingtonePreset.fromName(zone.exitRingtoneType),
                                            customUri = zone.exitCustomRingtoneUri,
                                            isEntry = false
                                        )
                                    }
                                )
                            }
                        }
                    }

                    2 -> {
                        EventHistoryList(
                            logs = eventLogs,
                            onClearHistory = { viewModel.clearEventHistory() }
                        )
                    }

                    3 -> {
                        SettingsScreen(
                            locationMode = locationMode,
                            onLocationModeChange = { viewModel.setLocationMode(it) },
                            onShowGuide = { showGuide = true }
                        )
                    }
                }
            }
        }

        if (isEditorOpen) {
            ZoneEditorDialog(
                initialZone = editingZone,
                onDismiss = { isEditorOpen = false },
                onSave = { updatedZone ->
                    viewModel.saveZone(updatedZone)
                    isEditorOpen = false
                },
                onUseCurrentGps = { callback ->
                    viewModel.fetchCurrentGpsCoordinates(callback)
                },
                onPreviewRingtone = { preset, customUri, isEntry ->
                    viewModel.previewRingtone(preset, customUri, isEntry)
                },
                onResolveRingtoneTitle = { uri ->
                    viewModel.resolveRingtoneTitle(uri)
                }
            )
        }
    }
}

@Composable
private fun EmptyZonesCard(onCreateZone: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_zones_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, RadarCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = RadarCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "עדיין לא הוגדר שום אזור לניטור",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "צור אזור כדי לקבל התראה בכניסה אליו וביציאה ממנו.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreateZone,
                modifier = Modifier.testTag("empty_create_zone_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("צור אזור חדש", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionBannerCard(
    title: String = "נדרשת הרשאת מיקום והתראות",
    body: String = "אשר גישה למיקום ולהתראות כדי שהאפליקציה תתריע בכניסה וביציאה מהרדיוס.",
    buttonText: String = "אשר הרשאות",
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_banner_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.testTag("grant_permissions_button")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ZoneDetailCard(
    zone: GeoZoneEntity,
    currentLat: Double,
    currentLon: Double,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreviewEntry: () -> Unit,
    onPreviewExit: () -> Unit
) {
    val distanceMeters = LocationEvaluator.calculateDistanceMeters(
        currentLat,
        currentLon,
        zone.latitude,
        zone.longitude
    )
    val isInside = distanceMeters <= zone.radiusMeters
    val isInWindow = zone.isWithinTimeWindow(System.currentTimeMillis())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .testTag("zone_card_${zone.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (zone.isEnabled) RadarCyan.copy(alpha = 0.35f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            .clickable { onToggleEnabled(!zone.isEnabled) },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = if (!zone.isEnabled) Color.Gray else if (isInside) RadarEmerald else RadarCoral
                        ) {}
                    }
                    Column {
                        Text(
                            text = zone.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "רדיוס: ${zone.radiusMeters.toInt()} מ׳ • מרחק נוכחי: ${distanceMeters.toInt()} מ׳",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isInside) RadarEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = zone.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("zone_enable_switch_${zone.id}")
                )
            }

            // Time window row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isInWindow) RadarEmerald else RadarAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "שעות דגימה: ${zone.formatTimeWindow()} (כל ${zone.samplingIntervalSeconds} שנ׳)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = if (isInWindow) "פעיל כעת" else "ממתין לשעה",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isInWindow) RadarEmerald else RadarAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Entry & Exit Ringtone details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RadarEmerald.copy(alpha = 0.12f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPreviewEntry() }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "נגן צלצול כניסה",
                            tint = RadarEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "צלצול כניסה לרדיוס:",
                                style = MaterialTheme.typography.labelMedium,
                                color = RadarEmerald
                            )
                            Text(
                                text = if (zone.alertOnEntry) zone.entryRingtoneDisplayName() else "כבוי",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RadarCoral.copy(alpha = 0.12f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPreviewExit() }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "נגן צלצול יציאה",
                            tint = RadarCoral,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "צלצול יציאה מהרדיוס:",
                                style = MaterialTheme.typography.labelMedium,
                                color = RadarCoral
                            )
                            Text(
                                text = if (zone.alertOnExit) zone.exitRingtoneDisplayName() else "כבוי",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_zone_button_${zone.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ערוך רדיוס, שעות וצלצולים")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_zone_button_${zone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "מחק אזור",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EventHistoryList(
    logs: List<GeoEventLogEntity>,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "יומן התראות כניסה ויציאה מהרדיוס",
                style = MaterialTheme.typography.titleLarge
            )
            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("נקה יומן")
                }
            }
        }

        if (logs.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = RadarCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "טרם נרשמו אירועי כניסה או יציאה",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "כאשר תיכנס או תצא מרדיוס המיקום בטווח השעות שהגדרת (או תלחץ על 'דמה כניסה/יציאה' במסך הרדאר), האירוע והצלצול שהופעל יופיעו כאן.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs, key = { it.id }) { item ->
                    val isEntry = item.eventType == "ENTRY"
                    val accentColor = if (isEntry) RadarEmerald else RadarCoral
                    val timeFormatted = remember(item.timestamp) {
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.18f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isEntry) Icons.Default.Login else Icons.Default.Logout,
                                        contentDescription = null,
                                        tint = accentColor
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isEntry) "כניסה לרדיוס: ${item.zoneName}" else "יציאה מהרדיוס: ${item.zoneName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = timeFormatted,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "מרחק בדגימה: ${item.distanceMeters.toInt()} מ׳ (רדיוס מוגדר: ${item.radiusMeters.toInt()} מ׳)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "צלצול שהופעל: ${item.ringtoneLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RadarCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
