package com.pixel.oceanfacts.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.AdConsent
import com.pixel.oceanfacts.AdManager
import com.pixel.oceanfacts.AppActions
import com.pixel.oceanfacts.billing.BillingManager
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.FEATURED_ZONES
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.core.Quiz
import com.pixel.oceanfacts.notify.DailyReminder

private val Danger = Color(0xFFE0654A)
private val Accent = Color(0xFF6FD0E8)

/** The reminder times offered, as hour-of-day to label. */
private val NOTIFY_TIMES = listOf(
    8 to "Morning · 8:00",
    13 to "Midday · 1:00 PM",
    19 to "Evening · 7:00 PM",
    21 to "Night · 9:00 PM",
)

private fun timeLabel(hour: Int): String =
    NOTIFY_TIMES.firstOrNull { it.first == hour }?.second ?: "$hour:00"

@Composable
fun ProfileScreen(savedCount: Int, viewed: Int, onClearSaved: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    // Result of the last manual update check, shown under the row instead of as a toast.
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    // Result of the last purchase or restore attempt, shown under the row it came from.
    var purchaseStatus by remember { mutableStateOf<String?>(null) }
    var billingBusy by remember { mutableStateOf(false) }
    var privacyStatus by remember { mutableStateOf<String?>(null) }
    var notifyOn by remember { mutableStateOf(OceanPrefs.notifyEnabled) }
    var notifyHour by remember { mutableStateOf(OceanPrefs.notifyHour) }
    var pickTime by remember { mutableStateOf(false) }
    // Set when the OS prompt is declined, so the row explains itself instead of silently
    // snapping back off.
    var notifyDenied by remember { mutableStateOf(false) }

    fun enableReminder() {
        notifyDenied = false
        notifyOn = true
        OceanPrefs.notifyEnabled = true
        DailyReminder.schedule(context)
        Analytics.reminderToggled(enabled = true, hour = OceanPrefs.notifyHour)
    }

    // Android 13+ gates notifications behind a runtime permission. Asked here, on the tap that
    // turns the reminder on, rather than at launch.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) enableReminder() else notifyDenied = true
    }

    fun toggleReminder(on: Boolean) {
        if (!on) {
            notifyDenied = false
            notifyOn = false
            OceanPrefs.notifyEnabled = false
            DailyReminder.cancel(context)
            Analytics.reminderToggled(enabled = false, hour = OceanPrefs.notifyHour)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableReminder()
        }
    }

    // These come from disk, and the daily quiz can change them while this screen is alive, so
    // the read is keyed to the revision rather than left to a plain one-off.
    val revision = OceanPrefs.revision
    val dayIndex = OceanPrefs.dayIndex
    val streakState = remember(revision, dayIndex) { OceanPrefs.streakState }
    val streak = streakState.current.coerceAtLeast(1)
    val daysExploring = OceanPrefs.daysSinceFirstOpen

    Column(
        Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(bottom = 80.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Sphere(
                sizeUnits = 84f,
                colors = listOf(Color(0xFF7FE4F0), Color(0xFF2F8FB4), Color(0xFF0B2E44)),
                glow = Color(0xFF3FC8DC).copy(alpha = 0.4f),
            )
            Text("Diver", style = ts(22f, FontWeight.Bold, Color.White), modifier = Modifier.padding(top = 16.dp))
            Text(
                when (daysExploring) {
                    0 -> "Exploring since today"
                    1 -> "Exploring since yesterday"
                    else -> "Exploring for $daysExploring days"
                },
                style = ts(13.5f, color = Dim, spacingEm = 0.02f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                .padding(vertical = 18.dp),
        ) {
            ProfileStat(viewed.toString(), "Facts seen")
            ProfileStat(savedCount.toString(), "Saved")
            // Only once there is something to show — an empty column reads as a missing feature.
            if (OceanPrefs.dailyQuizCount > 0) {
                ProfileStat(OceanPrefs.dailyQuizCount.toString(), "Dailies")
            } else {
                ProfileStat(FEATURED_ZONES.size.toString(), "Zones")
            }
            if (OceanPrefs.quizRounds > 0) {
                ProfileStat("${OceanPrefs.quizBest}/${Quiz.ROUND_SIZE}", "Quiz best")
            }
        }
        Row(
            Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(StreakAccent.copy(alpha = 0.18f), StreakAccent.copy(alpha = 0.05f)),
                    ),
                )
                .border(1.dp, StreakAccent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico("bolt", size = 30.dp, color = StreakAccent, filled = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("$streak-day streak", style = ts(19f, FontWeight.Bold, Color.White))
                Text(
                    when {
                        streakState.longest > streak -> "Longest run: ${streakState.longest} days"
                        streak == 1 -> "Come back tomorrow to start a run."
                        else -> "Your longest run yet — keep it going tomorrow."
                    },
                    style = ts(13.5f, color = Color(0xFFE0B48F)),
                    modifier = Modifier.padding(top = 1.dp),
                )
                Text("LAST 7 DAYS", style = ts(9.5f, FontWeight.Bold, Dim, 0.16f), modifier = Modifier.padding(top = 12.dp))
                WeekDots(streakState.week(dayIndex), modifier = Modifier.padding(top = 6.dp))
            }
        }

        SectionLabel("DAILY REMINDER")
        SettingsCard {
            SettingsRow(
                icon = "bell",
                iconTint = if (notifyOn) Accent else Color(0xFFCADCE4),
                title = "Daily reminder",
                // The reminder is one send that picks its own subject, so the copy names both
                // rather than promising only the fact it used to always be.
                subtitle = when {
                    notifyDenied -> "Notifications are off for Ocean Facts — turn them on in Settings"
                    notifyOn -> "A fact each day — or the quiz, when your streak is at stake"
                    else -> "One nudge a day, at a time you choose"
                },
                subtitleTint = if (notifyDenied) Danger else Dim,
                showChevron = false,
                onClick = { toggleReminder(!notifyOn) },
                trailingSlot = {
                    Switch(
                        checked = notifyOn,
                        onCheckedChange = { toggleReminder(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = Color(0xFF7E8F9A),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.18f),
                        ),
                    )
                },
            )
            if (notifyOn) {
                RowDivider()
                SettingsRow(
                    icon = "refresh",
                    title = "Time",
                    subtitle = timeLabel(notifyHour),
                    onClick = { pickTime = true },
                )
            }
        }

        SectionLabel("ADS")
        SettingsCard {
            if (BillingManager.isAdFree) {
                SettingsRow(
                    icon = "bolt",
                    iconTint = Accent,
                    title = "Ad-free unlocked",
                    subtitle = "Thank you for supporting Ocean Facts.",
                    showChevron = false,
                )
            } else {
                SettingsRow(
                    icon = "bolt",
                    iconTint = Accent,
                    title = "Remove ads",
                    subtitle = when {
                        billingBusy -> "Opening Google Play…"
                        purchaseStatus != null -> purchaseStatus!!
                        // Play's own localised price, never a hardcoded one — it differs by
                        // country, tax and currency.
                        BillingManager.price != null -> "One-time purchase · ${BillingManager.price}"
                        else -> "One-time purchase, yours forever"
                    },
                    subtitleTint = if (purchaseStatus != null && !billingBusy) Accent else Dim,
                    enabled = !billingBusy,
                    onClick = {
                        val activity = context as? android.app.Activity ?: return@SettingsRow
                        billingBusy = true
                        purchaseStatus = null
                        BillingManager.launchPurchase(activity) { outcome ->
                            billingBusy = false
                            Analytics.purchase(outcome.name)
                            purchaseStatus = when (outcome) {
                                BillingManager.Outcome.PURCHASED -> {
                                    AdManager.discard()
                                    "Ads removed — thank you!"
                                }
                                BillingManager.Outcome.CANCELLED -> null
                                BillingManager.Outcome.UNAVAILABLE ->
                                    "Google Play is not available right now."
                                BillingManager.Outcome.ERROR ->
                                    "Purchase did not go through. Please try again."
                            }
                        }
                    },
                )
                RowDivider()
                SettingsRow(
                    icon = "refresh",
                    title = "Restore purchase",
                    subtitle = "Already bought it? Bring it back here",
                    enabled = !billingBusy,
                    onClick = {
                        billingBusy = true
                        purchaseStatus = null
                        BillingManager.refreshPurchases { owned ->
                            billingBusy = false
                            purchaseStatus = if (owned) {
                                AdManager.discard()
                                "Purchase restored — ads are off."
                            } else {
                                "No previous purchase found on this account."
                            }
                        }
                    },
                )
                // Only shown where a privacy choice actually applies (the EEA and the UK);
                // AdConsent reports that from the UMP SDK rather than guessing from locale.
                if (AdConsent.isPrivacyOptionsRequired) {
                    RowDivider()
                    SettingsRow(
                        icon = "info",
                        title = "Privacy options",
                        subtitle = privacyStatus ?: "Change your ad personalisation choice",
                        subtitleTint = if (privacyStatus != null) Accent else Dim,
                        onClick = {
                            val activity = context as? android.app.Activity ?: return@SettingsRow
                            AdConsent.showPrivacyOptions(activity) { error ->
                                privacyStatus = error?.let { "Could not open the form. Please try again." }
                            }
                        },
                    )
                }
            }
        }

        SectionLabel("SUPPORT OCEAN FACTS")
        SettingsCard {
            SettingsRow(
                icon = "star",
                iconTint = Color(0xFFFFC24D),
                title = "Leave a review",
                subtitle = "Rate Ocean Facts on Google Play",
                onClick = { AppActions.openPlayListing(context) },
            )
            RowDivider()
            SettingsRow(
                icon = "share",
                title = "Share the app",
                subtitle = "Send a friend the Play Store link",
                onClick = { AppActions.shareApp(context) },
            )
        }

        SectionLabel("HELP")
        SettingsCard {
            SettingsRow(
                icon = "mail",
                title = "Contact support",
                subtitle = AppActions.SUPPORT_EMAIL,
                onClick = { AppActions.emailSupport(context) },
            )
            RowDivider()
            SettingsRow(
                icon = "refresh",
                title = "Check for updates",
                subtitle = when {
                    checkingUpdate -> "Checking…"
                    else -> updateStatus ?: "Ask Google Play for a newer build"
                },
                subtitleTint = if (updateStatus != null && !checkingUpdate) Accent else Dim,
                enabled = !checkingUpdate,
                onClick = {
                    checkingUpdate = true
                    updateStatus = null
                    AppActions.checkForUpdates(context) { result ->
                        checkingUpdate = false
                        updateStatus = result
                    }
                },
            )
        }

        SectionLabel("DATA")
        SettingsCard {
            SettingsRow(
                icon = "trash",
                iconTint = if (savedCount > 0) Danger else Dim,
                title = "Clear saved facts",
                titleTint = if (savedCount > 0) Danger else Dim,
                subtitle = if (savedCount > 0) {
                    "Remove all $savedCount from your collection"
                } else {
                    "Nothing saved yet"
                },
                showChevron = savedCount > 0,
                enabled = savedCount > 0,
                onClick = { confirmClear = true },
            )
        }

        SectionLabel("ABOUT")
        SettingsCard {
            SettingsRow(
                icon = "info",
                title = "App version",
                // Read off the installed package, so it always matches the running build.
                trailing = AppActions.versionLabel(context),
                showChevron = false,
            )
        }

        Text(
            "A daily descent into the sea.",
            style = ts(13f, color = Dim),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 30.dp),
        )
    }

    if (pickTime) {
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = Color(0xFF0A1C26),
            titleContentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("When should we remind you?", style = ts(19f, FontWeight.Bold, Color.White)) },
            text = {
                Column {
                    NOTIFY_TIMES.forEach { (hour, label) ->
                        val on = hour == notifyHour
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    notifyHour = hour
                                    OceanPrefs.notifyHour = hour
                                    DailyReminder.schedule(context)
                                    pickTime = false
                                }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = ts(
                                    15.5f,
                                    if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    if (on) Accent else Color.White,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (on) Text("✓", style = ts(16f, FontWeight.Bold, Accent))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickTime = false }) {
                    Text("Cancel", style = ts(15f, FontWeight.SemiBold, Mute))
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color(0xFF0A1C26),
            titleContentColor = Color.White,
            textContentColor = Mute,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Clear saved facts?", style = ts(19f, FontWeight.Bold, Color.White)) },
            text = {
                Text(
                    "All $savedCount saved fact${if (savedCount == 1) "" else "s"} will be removed from your collection. This cannot be undone.",
                    style = ts(14.5f, color = Mute, lineHeight = 21f),
                )
            },
            confirmButton = {
                TextButton(onClick = { onClearSaved(); confirmClear = false }) {
                    Text("Clear", style = ts(15f, FontWeight.SemiBold, Danger))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", style = ts(15f, FontWeight.SemiBold, Mute))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(
        text,
        style = ts(11.5f, FontWeight.Bold, Dim, 0.22f),
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 10.dp),
    )

/** The rounded translucent container the settings rows sit in, matching the stats card. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) =
    Column(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp)),
    ) { content() }

@Composable
private fun RowDivider() =
    Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))

@Composable
private fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    iconTint: Color = Color(0xFFCADCE4),
    titleTint: Color = Color.White,
    subtitleTint: Color = Dim,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingSlot: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) { Ico(icon, size = 19.dp, color = iconTint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(15.5f, FontWeight.SemiBold, titleTint))
            subtitle?.let {
                Text(it, style = ts(12.5f, color = subtitleTint), modifier = Modifier.padding(top = 2.dp))
            }
        }
        trailing?.let {
            Text(it, style = ts(14f, FontWeight.Medium, Mute), modifier = Modifier.padding(start = 8.dp))
        }
        trailingSlot?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Ico("chevR", size = 17.dp, color = Dim)
        }
    }
}

@Composable
private fun RowScope.ProfileStat(n: String, caption: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n, style = ts(30f, FontWeight.Bold, Color.White))
        Text(
            caption.uppercase(),
            style = ts(11f, FontWeight.SemiBold, Dim, 0.1f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
