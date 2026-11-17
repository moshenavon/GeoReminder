package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.LocationMode
import com.example.ui.theme.RadarCyan
import com.example.ui.theme.RadarEmerald
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 7
private const val PAGE_MODE = 5
private const val PAGE_LAST = 6

private data class GuidePage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val bullets: List<String>
)

private val textPages = listOf(
    GuidePage(
        icon = Icons.Default.Radar,
        title = "ברוכים הבאים ל-GeoAlert",
        body = "GeoAlert מתריעה לך אוטומטית כשאתה נכנס לאזור מסוים או יוצא ממנו, רק בשעות ובימים שבחרת.",
        bullets = listOf(
            "לדוגמה: להזכיר לך משהו כשאתה מגיע למשרד",
            "או להתריע כשאתה עוזב את הבית",
            "ההסבר לוקח פחות מדקה, ובסופו תדע בדיוק מה לעשות"
        )
    ),
    GuidePage(
        icon = Icons.Default.LocationOn,
        title = "שלב 1: מגדירים אזור",
        body = "אזור הוא מעגל על המפה: נקודת מרכז ורדיוס.",
        bullets = listOf(
            "לוחצים על \"הגדר רדיוס חדש\" במסך הראשי",
            "נותנים לאזור שם, למשל \"משרד\"",
            "כשאתה במקום, לוחצים \"קבע מרכז לפי מיקום הטלפון\". אפשר גם להזין קואורדינטות ידנית",
            "בוחרים רדיוס בין 100 ל-3,000 מטר"
        )
    ),
    GuidePage(
        icon = Icons.Default.Schedule,
        title = "שלב 2: שעות וימים",
        body = "קובעים מתי האזור פעיל. מחוץ לשעות האלה לא תקבל התראות.",
        bullets = listOf(
            "בוחרים שעת התחלה ושעת סיום",
            "אפשר לבחור טווח שחוצה חצות, למשל 22:00 עד 06:00",
            "מסמנים באילו ימים בשבוע האזור פעיל"
        )
    ),
    GuidePage(
        icon = Icons.Default.NotificationsActive,
        title = "שלב 3: התראה וצלצול",
        body = "לכל אזור אפשר להגדיר התראה נפרדת לכניסה וליציאה.",
        bullets = listOf(
            "בוחרים צלצול מובנה או צלצול מהמכשיר",
            "אפשר לכבות את ההתראה בכניסה או ביציאה",
            "אפשר להפעיל רטט",
            "אפשר לשמוע את הצלצול לפני השמירה"
        )
    ),
    GuidePage(
        icon = Icons.Default.Security,
        title = "שלב 4: הרשאות",
        body = "כדי שההתראות יעבדו גם כשהאפליקציה סגורה, היא תבקש שלוש הרשאות:",
        bullets = listOf(
            "מיקום מדויק: לזיהוי הכניסה והיציאה",
            "מיקום \"אפשר כל הזמן\": כדי לעבוד ברקע",
            "התראות: כדי להציג לך את ההתראה",
            "המיקום נשאר במכשיר שלך, והאפליקציה לא שולחת אותו לשום שרת",
            "כרטיס ההרשאות במסך הראשי ינחה אותך צעד אחר צעד"
        )
    )
)

/**
 * First-run walkthrough. Shown on every launch until the user ticks "I understood, don't
 * show again" on the last page (or until they skip it for the current session).
 */
@Composable
fun OnboardingScreen(
    locationMode: LocationMode,
    onLocationModeChange: (LocationMode) -> Unit,
    initialDontShowAgain: Boolean,
    onFinish: (dontShowAgain: Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    var dontShowAgain by remember { mutableStateOf(initialDontShowAgain) }
    val page = pagerState.currentPage

    BackHandler(enabled = page > 0) {
        scope.launch { pagerState.animateScrollToPage(page - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page != PAGE_LAST) {
                TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding_skip")) {
                    Text("דלג")
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (index) {
                        PAGE_MODE -> ModePage(locationMode, onLocationModeChange)
                        PAGE_LAST -> LastPage(
                            dontShowAgain = dontShowAgain,
                            onDontShowAgainChange = { dontShowAgain = it }
                        )
                        else -> TextPage(textPages[index])
                    }
                }
            }
        }

        // Page dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PAGE_COUNT) { i ->
                val active = i == page
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                    modifier = Modifier.testTag("onboarding_back")
                ) { Text("הקודם") }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }

            if (page == PAGE_LAST) {
                Button(
                    onClick = { onFinish(dontShowAgain) },
                    modifier = Modifier.testTag("onboarding_finish")
                ) { Text("סיום והתחלה", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                    modifier = Modifier.testTag("onboarding_next")
                ) { Text("הבא", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, title: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier.size(88.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RadarCyan,
                modifier = Modifier.size(46.dp)
            )
        }
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BulletRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = RadarEmerald,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TextPage(page: GuidePage) {
    PageHeader(page.icon, page.title)
    Text(
        text = page.body,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        page.bullets.forEach { BulletRow(it) }
    }
}

@Composable
private fun ModePage(
    locationMode: LocationMode,
    onLocationModeChange: (LocationMode) -> Unit
) {
    PageHeader(Icons.Default.BatteryChargingFull, "שלב 5: איך לבדוק את המיקום?")
    Text(
        text = "בחר את שיטת הדגימה. תמיד אפשר לשנות אותה אחר כך בלשונית \"הגדרות\".",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LocationModePicker(selected = locationMode, onSelect = onLocationModeChange)
}

@Composable
private fun LastPage(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit
) {
    PageHeader(Icons.Default.CheckCircle, "מוכן להתחיל!")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BulletRow("לחץ על \"הגדר רדיוס חדש\" כדי ליצור את האזור הראשון")
        BulletRow("בדוק אותו עם כפתורי \"דמה כניסה\" ו\"דמה יציאה\" במסך הרדאר")
        BulletRow("ביומן האירועים תראה את כל ההתראות שהופעלו")
        BulletRow("את ההסבר הזה אפשר להציג שוב מלשונית \"הגדרות\"")
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDontShowAgainChange(!dontShowAgain) }
            .testTag("onboarding_dont_show_again")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = dontShowAgain, onCheckedChange = onDontShowAgainChange)
            Text(
                text = "הבנתי, אין צורך להציג שוב",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
