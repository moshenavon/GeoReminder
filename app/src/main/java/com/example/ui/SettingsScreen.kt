package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.LocationMode
import com.example.ui.theme.RadarCoral
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald

private data class LocationModeInfo(
    val mode: LocationMode,
    val icon: ImageVector,
    val title: String,
    val badge: String?,
    val description: String,
    val pros: List<String>,
    val cons: List<String>,
    val bestFor: String
)

private val locationModeInfos = listOf(
    LocationModeInfo(
        mode = LocationMode.BATTERY_SAVER,
        icon = Icons.Default.BatteryChargingFull,
        title = "חסכוני בסוללה",
        badge = "מומלץ",
        description = "מערכת המיקום של גוגל (Geofencing) עוקבת אחרי גבולות האזורים בעזרת Wi-Fi, " +
                "אנטנות סלולר ומד-תאוצה, ומעירה את האפליקציה רק כשהטלפון חוצה גבול. " +
                "אין דגימת GPS רציפה.",
        pros = listOf(
            "צריכת סוללה נמוכה מאוד, מתאים לשימוש יומיומי",
            "אין התראה קבועה בשורת ההודעות",
            "ממשיך לעבוד גם אחרי הפעלה מחדש של הטלפון"
        ),
        cons = listOf(
            "פחות מדויק: הזיהוי עלול לסטות בעשרות עד מאות מטרים, בעיקר באזורים עם כיסוי רשת חלש",
            "ההתראה עלולה להתעכב בדקה–שתיים (ולפעמים יותר) מרגע הכניסה או היציאה",
            "תדירות הדגימה שהוגדרה לאזור משפיעה רק על הרדאר החי במסך, לא על ההתראות ברקע"
        ),
        bestFor = "בית, עבודה ואזורים ברדיוס של מאות מטרים, כשאין צורך להתריע בדיוק בשנייה."
    ),
    LocationModeInfo(
        mode = LocationMode.PRECISE,
        icon = Icons.Default.GpsFixed,
        title = "מדויק (GPS)",
        badge = null,
        description = "האפליקציה רצה ברקע כשירות קבוע ודוגמת את ה-GPS בתדירות שהגדרת לכל אזור " +
                "(למשל כל 15 שניות), אך ורק בשעות הפעילות של האזורים.",
        pros = listOf(
            "דיוק גבוה: מיקום לפי GPS, עם סטייה של מטרים עד עשרות מטרים",
            "תגובה מהירה: ההתראה מגיעה תוך שניות, לפי התדירות שהגדרת",
            "שליטה בתדירות לכל אזור (מכל 10 שניות ועד כל 5 דקות)"
        ),
        cons = listOf(
            "צורך יותר סוללה, במיוחד בתדירות גבוהה. תדירות נמוכה יותר (למשל כל 5 דקות) מפחיתה את הצריכה",
            "מציג התראה קבועה בשורת ההודעות כל עוד הניטור פועל",
            "ה-GPS פחות מדויק בתוך מבנים ובקומות תחתונות",
            "חלק מיצרני הטלפונים (כמו Xiaomi, Huawei ו-Samsung) עלולים לעצור את השירות כדי לחסוך סוללה. " +
                    "כדאי להוציא את GeoAlert מהגבלות הסוללה בהגדרות המכשיר"
        ),
        bestFor = "אזורים קטנים, או מצבים שבהם חשוב לקבל התראה מיידית ומדויקת."
    )
)

/** Both sampling-method cards, shared by the settings tab and the first-run guide. */
@Composable
fun LocationModePicker(
    selected: LocationMode,
    onSelect: (LocationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        locationModeInfos.forEach { info ->
            LocationModeCard(
                info = info,
                isSelected = info.mode == selected,
                onSelect = { onSelect(info.mode) }
            )
        }
    }
}

@Composable
private fun LocationModeCard(
    info: LocationModeInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
            .testTag("location_mode_${info.mode.name}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) RadarCyan else MaterialTheme.colorScheme.outlineVariant
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Icon(
                    imageVector = info.icon,
                    contentDescription = null,
                    tint = RadarCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (info.badge != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = RadarEmerald.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = info.badge,
                            style = MaterialTheme.typography.labelLarge,
                            color = RadarEmerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = info.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BulletSection(
                title = "יתרונות",
                items = info.pros,
                icon = Icons.Default.Check,
                color = RadarEmerald
            )
            BulletSection(
                title = "חסרונות",
                items = info.cons,
                icon = Icons.Default.Close,
                color = RadarCoral
            )

            Text(
                text = "מתאים ל: ${info.bestFor}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BulletSection(
    title: String,
    items: List<String>,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(16.dp)
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    locationMode: LocationMode,
    onLocationModeChange: (LocationMode) -> Unit,
    onShowGuide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "הגדרות",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "שיטת דגימת מיקום",
            style = MaterialTheme.typography.titleMedium,
            color = RadarCyan,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "בחר איך האפליקציה בודקת את המיקום שלך ברקע. אפשר להחליף בכל רגע, והשינוי חל מיד.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LocationModePicker(selected = locationMode, onSelect = onLocationModeChange)

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Text(
                text = "בשני המצבים נדרשת הרשאת מיקום \"אפשר כל הזמן\", והאפליקציה בודקת רק אזורים " +
                        "שמופעלים, בימים ובשעות שהוגדרו להם.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(1.dp))

        Text(
            text = "הסבר על האפליקציה",
            style = MaterialTheme.typography.titleMedium,
            color = RadarCyan,
            fontWeight = FontWeight.Bold
        )
        OutlinedButton(
            onClick = onShowGuide,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("show_guide_button")
        ) {
            Text("הצג שוב את ההסבר הראשוני")
        }
    }
}
