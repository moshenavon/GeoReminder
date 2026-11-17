package com.example.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.GeoZoneEntity
import com.example.data.RingtonePreset
import com.example.ui.theme.RadarCoral
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import java.util.Locale
import kotlin.math.roundToInt

private data class CityPreset(
    val nameHe: String,
    val lat: Double,
    val lon: Double
)

private val ISRAEL_PRESETS = listOf(
    CityPreset("תל אביב", 32.0853, 34.7818),
    CityPreset("ירושלים", 31.7683, 35.2137),
    CityPreset("חיפה", 32.7940, 34.9896),
    CityPreset("באר שבע", 31.2530, 34.7915),
    CityPreset("רמת גן", 32.0684, 34.8248)
)

private val DAYS_OF_WEEK = listOf(
    1 to "א׳",
    2 to "ב׳",
    3 to "ג׳",
    4 to "ד׳",
    5 to "ה׳",
    6 to "ו׳",
    7 to "ש׳"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneEditorDialog(
    initialZone: GeoZoneEntity?,
    onDismiss: () -> Unit,
    onSave: (GeoZoneEntity) -> Unit,
    onUseCurrentGps: ((Double, Double) -> Unit) -> Unit,
    onPreviewRingtone: (RingtonePreset, String, Boolean) -> Unit,
    onResolveRingtoneTitle: (Uri?) -> String
) {
    BackHandler { onDismiss() }

    var name by remember { mutableStateOf(initialZone?.name ?: "אזור התראה חדש") }
    var latText by remember {
        mutableStateOf(String.format(Locale.US, "%.5f", initialZone?.latitude ?: 32.0853))
    }
    var lonText by remember {
        mutableStateOf(String.format(Locale.US, "%.5f", initialZone?.longitude ?: 34.7818))
    }
    var radiusMeters by remember { mutableFloatStateOf((initialZone?.radiusMeters ?: 250f).coerceAtLeast(100f)) }

    var startHour by remember { mutableIntStateOf(initialZone?.startHour ?: 8) }
    var startMinute by remember { mutableIntStateOf(initialZone?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(initialZone?.endHour ?: 18) }
    var endMinute by remember { mutableIntStateOf(initialZone?.endMinute ?: 0) }

    var activeDays by remember {
        mutableStateOf(initialZone?.getActiveDaysSet() ?: setOf(1, 2, 3, 4, 5, 6, 7))
    }
    var samplingIntervalSec by remember {
        mutableIntStateOf(initialZone?.samplingIntervalSeconds ?: 15)
    }

    var alertOnEntry by remember { mutableStateOf(initialZone?.alertOnEntry ?: true) }
    var entryPreset by remember {
        mutableStateOf(
            RingtonePreset.fromName(initialZone?.entryRingtoneType ?: RingtonePreset.RADAR_CHIME.name)
        )
    }
    var entryCustomUri by remember { mutableStateOf(initialZone?.entryCustomRingtoneUri ?: "") }
    var entryCustomTitle by remember { mutableStateOf(initialZone?.entryCustomRingtoneTitle ?: "") }

    var alertOnExit by remember { mutableStateOf(initialZone?.alertOnExit ?: true) }
    var exitPreset by remember {
        mutableStateOf(
            RingtonePreset.fromName(initialZone?.exitRingtoneType ?: RingtonePreset.SIREN_ALERT.name)
        )
    }
    var exitCustomUri by remember { mutableStateOf(initialZone?.exitCustomRingtoneUri ?: "") }
    var exitCustomTitle by remember { mutableStateOf(initialZone?.exitCustomRingtoneTitle ?: "") }

    var vibrate by remember { mutableStateOf(initialZone?.vibrate ?: true) }

    // System Ringtone Picker for Entry
    val entryRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                entryCustomUri = uri.toString()
                entryCustomTitle = onResolveRingtoneTitle(uri)
                entryPreset = RingtonePreset.CUSTOM_URI
            }
        }
    }

    // System Ringtone Picker for Exit
    val exitRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                exitCustomUri = uri.toString()
                exitCustomTitle = onResolveRingtoneTitle(uri)
                exitPreset = RingtonePreset.CUSTOM_URI
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_zone_editor_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "סגור")
                            }
                            Text(
                                text = if (initialZone == null) "הגדרת אזור רדיוס חדש" else "עריכת אזור רדיוס",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        Button(
                            onClick = {
                                val parsedLat = latText.toDoubleOrNull() ?: 32.0853
                                val parsedLon = lonText.toDoubleOrNull() ?: 34.7818
                                val daysCsv = if (activeDays.isEmpty()) {
                                    "1,2,3,4,5,6,7"
                                } else {
                                    activeDays.sorted().joinToString(",")
                                }
                                onSave(
                                    GeoZoneEntity(
                                        id = initialZone?.id ?: 0L,
                                        name = name.ifBlank { "אזור מיקום" },
                                        latitude = parsedLat,
                                        longitude = parsedLon,
                                        radiusMeters = radiusMeters.coerceIn(100f, 10000f),
                                        startHour = startHour,
                                        startMinute = startMinute,
                                        endHour = endHour,
                                        endMinute = endMinute,
                                        activeDaysCsv = daysCsv,
                                        samplingIntervalSeconds = samplingIntervalSec,
                                        alertOnEntry = alertOnEntry,
                                        alertOnExit = alertOnExit,
                                        entryRingtoneType = entryPreset.name,
                                        entryCustomRingtoneUri = entryCustomUri,
                                        entryCustomRingtoneTitle = entryCustomTitle,
                                        exitRingtoneType = exitPreset.name,
                                        exitCustomRingtoneUri = exitCustomUri,
                                        exitCustomRingtoneTitle = exitCustomTitle,
                                        vibrate = vibrate,
                                        isEnabled = initialZone?.isEnabled ?: true,
                                        lastKnownState = initialZone?.lastKnownState ?: "UNKNOWN",
                                        lastDistanceMeters = initialZone?.lastDistanceMeters,
                                        lastSampledAt = initialZone?.lastSampledAt
                                    )
                                )
                            },
                            modifier = Modifier.testTag("save_zone_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("שמור הגדרות", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Zone Name & Center Location
                EditorSectionCard(title = "1. שם האזור ונקודת המרכז") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("שם האזור (למשל: בית, משרד, לימודים)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("zone_name_input")
                    )

                    OutlinedButton(
                        onClick = {
                            onUseCurrentGps { gpsLat, gpsLon ->
                                latText = String.format(Locale.US, "%.5f", gpsLat)
                                lonText = String.format(Locale.US, "%.5f", gpsLon)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("use_current_gps_button")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = RadarCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("קבע מרכז לפי מיקום הטלפון הנוכחי (GPS)")
                    }

                    Text(
                        text = "או בחר מיקום מהיר:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ISRAEL_PRESETS.forEach { city ->
                            AssistChip(
                                onClick = {
                                    latText = String.format(Locale.US, "%.5f", city.lat)
                                    lonText = String.format(Locale.US, "%.5f", city.lon)
                                },
                                label = { Text(city.nameHe) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("קו רוחב (Latitude)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("zone_lat_input")
                        )
                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it },
                            label = { Text("קו אורך (Longitude)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("zone_lon_input")
                        )
                    }
                }

                // 2. Radius in Meters
                EditorSectionCard(title = "2. הגדרת רדיוס המיקום") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "רדיוס לזיהוי כניסה ויציאה:",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${radiusMeters.roundToInt()} מטרים",
                            style = MaterialTheme.typography.titleLarge,
                            color = RadarCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = (it / 10f).roundToInt() * 10f },
                        valueRange = 100f..3000f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("radius_slider")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(100f, 150f, 250f, 500f, 1000f, 2000f).forEach { presetRadius ->
                            FilterChip(
                                selected = radiusMeters.roundToInt() == presetRadius.roundToInt(),
                                onClick = { radiusMeters = presetRadius },
                                label = { Text("${presetRadius.toInt()} מ׳") }
                            )
                        }
                    }
                }

                // 3. Time Range & Sampling Schedule
                EditorSectionCard(title = "3. טווח זמנים ותדירות דגימת המיקום") {
                    Text(
                        text = "הגדר באילו שעות וימים האפליקציה תדגום את מיקום הטלפון ותפעיל התראות:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimeStepperBox(
                            label = "שעת התחלה",
                            hour = startHour,
                            minute = startMinute,
                            onTimeChange = { h, m ->
                                startHour = h
                                startMinute = m
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TimeStepperBox(
                            label = "שעת סיום",
                            hour = endHour,
                            minute = endMinute,
                            onTimeChange = { h, m ->
                                endHour = h
                                endMinute = m
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                startHour = 0; startMinute = 0
                                endHour = 23; endMinute = 59
                            },
                            label = { Text("כל היממה (00:00 - 23:59)") }
                        )
                        AssistChip(
                            onClick = {
                                startHour = 8; startMinute = 0
                                endHour = 17; endMinute = 0
                            },
                            label = { Text("שעות עבודה (08:00 - 17:00)") }
                        )
                        AssistChip(
                            onClick = {
                                startHour = 18; startMinute = 0
                                endHour = 23; endMinute = 30
                            },
                            label = { Text("שעות ערב (18:00 - 23:30)") }
                        )
                    }

                    Text(
                        text = "ימים פעילים בשבוע:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DAYS_OF_WEEK.forEach { (dayNum, label) ->
                            val isSelected = activeDays.contains(dayNum)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    activeDays = if (isSelected && activeDays.size > 1) {
                                        activeDays - dayNum
                                    } else {
                                        activeDays + dayNum
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "תדירות דגימת מיקום בזמן החלון הפעיל:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "משמשת במצב \"מדויק (GPS)\" ובתצוגת הרדאר החי. במצב \"חסכוני בסוללה\" ההתראות ברקע מגיעות ממערכת המיקום של גוגל (אפשר לבחור את המצב בלשונית הגדרות).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            10 to "כל 10 שניות",
                            15 to "כל 15 שניות",
                            30 to "כל 30 שניות",
                            60 to "כל דקה",
                            300 to "כל 5 דקות"
                        ).forEach { (sec, title) ->
                            FilterChip(
                                selected = samplingIntervalSec == sec,
                                onClick = { samplingIntervalSec = sec },
                                label = { Text(title) }
                            )
                        }
                    }
                }

                // 4. Entry Alert & Ringtone Configuration
                EditorSectionCard(title = "4. התראה וצלצול בעת כניסה לרדיוס") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "הפעל התראה בכניסה לרדיוס",
                                style = MaterialTheme.typography.titleMedium,
                                color = RadarEmerald
                            )
                            Text(
                                text = "מופעל כאשר הטלפון נכנס לתוך הרדיוס בטווח הזמנים המוגדר",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alertOnEntry,
                            onCheckedChange = { alertOnEntry = it },
                            modifier = Modifier.testTag("entry_alert_switch")
                        )
                    }

                    if (alertOnEntry) {
                        RingtoneSelectorBlock(
                            selectedPreset = entryPreset,
                            customRingtoneTitle = entryCustomTitle,
                            isEntry = true,
                            onSelectPreset = { preset ->
                                entryPreset = preset
                            },
                            onPickFromDevice = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                        RingtoneManager.TYPE_ALL
                                    )
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "בחר צלצול להתראת כניסה לרדיוס")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    if (entryCustomUri.isNotBlank()) {
                                        putExtra(
                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                            Uri.parse(entryCustomUri)
                                        )
                                    }
                                }
                                entryRingtoneLauncher.launch(intent)
                            },
                            onPreviewSound = {
                                onPreviewRingtone(entryPreset, entryCustomUri, true)
                            }
                        )
                    }
                }

                // 5. Exit Alert & Ringtone Configuration
                EditorSectionCard(title = "5. התראה וצלצול בעת יציאה מהרדיוס") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "הפעל התראה ביציאה מהרדיוס",
                                style = MaterialTheme.typography.titleMedium,
                                color = RadarCoral
                            )
                            Text(
                                text = "מופעל כאשר הטלפון יוצא מחוץ לרדיוס בטווח הזמנים המוגדר",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alertOnExit,
                            onCheckedChange = { alertOnExit = it },
                            modifier = Modifier.testTag("exit_alert_switch")
                        )
                    }

                    if (alertOnExit) {
                        RingtoneSelectorBlock(
                            selectedPreset = exitPreset,
                            customRingtoneTitle = exitCustomTitle,
                            isEntry = false,
                            onSelectPreset = { preset ->
                                exitPreset = preset
                            },
                            onPickFromDevice = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                        RingtoneManager.TYPE_ALL
                                    )
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "בחר צלצול להתראת יציאה מהרדיוס")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    if (exitCustomUri.isNotBlank()) {
                                        putExtra(
                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                            Uri.parse(exitCustomUri)
                                        )
                                    }
                                }
                                exitRingtoneLauncher.launch(intent)
                            },
                            onPreviewSound = {
                                onPreviewRingtone(exitPreset, exitCustomUri, false)
                            }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "הפעל רטט ביחד עם הצלצול",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = vibrate,
                            onCheckedChange = { vibrate = it },
                            modifier = Modifier.testTag("vibrate_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = RadarCyan,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun TimeStepperBox(
    label: String,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%02d:%02d", hour, minute),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("שעה", style = MaterialTheme.typography.labelMedium)
                    Row {
                        IconButton(
                            onClick = { onTimeChange((hour + 1) % 24, minute) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "הוסף שעה")
                        }
                        IconButton(
                            onClick = { onTimeChange((hour + 23) % 24, minute) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "הפחת שעה")
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("דקות", style = MaterialTheme.typography.labelMedium)
                    Row {
                        IconButton(
                            onClick = { onTimeChange(hour, (minute + 15) % 60) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "הוסף 15 דקות")
                        }
                        IconButton(
                            onClick = { onTimeChange(hour, (minute + 45) % 60) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "הפחת 15 דקות")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RingtoneSelectorBlock(
    selectedPreset: RingtonePreset,
    customRingtoneTitle: String,
    isEntry: Boolean,
    onSelectPreset: (RingtonePreset) -> Unit,
    onPickFromDevice: () -> Unit,
    onPreviewSound: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "בחר סוג צלצול:",
                style = MaterialTheme.typography.labelLarge
            )
            Button(
                onClick = onPreviewSound,
                modifier = Modifier.testTag(if (isEntry) "editor_test_entry_sound" else "editor_test_exit_sound")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("השמע צלצול נבחר")
            }
        }

        RingtonePreset.entries.forEach { preset ->
            val isSelected = selectedPreset == preset
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectPreset(preset)
                        if (preset == RingtonePreset.CUSTOM_URI && customRingtoneTitle.isBlank()) {
                            onPickFromDevice()
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                },
                border = if (isSelected) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            onSelectPreset(preset)
                            if (preset == RingtonePreset.CUSTOM_URI && customRingtoneTitle.isBlank()) {
                                onPickFromDevice()
                            }
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (preset == RingtonePreset.CUSTOM_URI && customRingtoneTitle.isNotBlank()) {
                                "${preset.titleHe}: $customRingtoneTitle"
                            } else {
                                preset.titleHe
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = preset.subtitleHe,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onPickFromDevice,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (isEntry) "pick_device_entry_ringtone" else "pick_device_exit_ringtone")
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (customRingtoneTitle.isNotBlank()) {
                    "החלף צלצול מהמכשיר ($customRingtoneTitle)"
                } else {
                    "פתח בורר צלצולים מהמכשיר"
                }
            )
        }
    }
}
