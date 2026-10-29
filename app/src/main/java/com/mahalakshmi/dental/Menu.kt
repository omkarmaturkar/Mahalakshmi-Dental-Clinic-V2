package com.mahalakshmi.dental

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Opens the slide-out menu on phones. It is null on tablets, where the menu is always visible. */
val LocalMenu = compositionLocalOf<(() -> Unit)?> { null }

/** The main menu: always visible on tablets held sideways, slides out on phones. */
@Composable
fun AppMenu(db: ClinicDb, section: Screen, permanent: Boolean, onSelect: (Screen) -> Unit) {
    val context = LocalContext.current
    val version = DataBus.version
    val clinic = remember(version) { db.clinicInfo() }
    val doctor = remember(version) { db.mainDoctor() }
    val everyone = remember(version) { db.getPatients("") }
    val dueTotal = remember(everyone) { everyone.sumOf { it.remaining.coerceAtLeast(0.0) } }
    val backupOk = remember(version) {
        val last = maxOf(DriveBackup.lastBackup(context), Backup.lastBackup(context))
        val on = DriveBackup.isConnected(context) || Backup.autoFolderName(context) != null
        on && last > 0L && System.currentTimeMillis() - last < 3L * 24 * 60 * 60 * 1000
    }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val active = when (section) {
        is Screen.Patients -> if (section.filter == 1) "dues" else "patients"
        is Screen.Schedule -> "schedule"
        is Screen.Finance -> "finance"
        is Screen.ClinicSettings -> "clinic"
        is Screen.BackupPage -> "backup"
        else -> "today"
    }

    Column(
        Modifier.fillMaxHeight().fillMaxWidth().background(Color.White)
            .then(if (permanent) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onSelect(Screen.ClinicSettings) }
                .padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoTile(46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    clinic.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Brand.Charcoal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp
                )
                Text(
                    doctor?.title ?: "Tap to add doctors", fontSize = 12.sp, color = Brand.Sub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = Brand.Line)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
            MenuItem("Home", HomeIcons.Home, active == "today") { onSelect(Screen.Home) }
            MenuItem("Patients", HomeIcons.People, active == "patients", trailing = "${everyone.size}") { onSelect(Screen.Patients()) }
            MenuItem("Schedule", HomeIcons.Calendar, active == "schedule") { onSelect(Screen.Schedule) }
            MenuItem("Finance", HomeIcons.Rupee, active == "finance") { onSelect(Screen.Finance) }
            MenuItem(
                "Pending dues", Icons.Filled.Warning, active == "dues",
                trailing = if (dueTotal > 0) rs(dueTotal) else null, trailingColor = Brand.Red
            ) { onSelect(Screen.Patients(filter = 1)) }
            MenuHeading("Settings")
            MenuItem("Clinic & doctors", DocIcons.Settings, active == "clinic") { onSelect(Screen.ClinicSettings) }
            MenuItem("Backup & restore", AppIcons.Cloud, active == "backup", dot = if (backupOk) null else Brand.Gold) {
                onSelect(Screen.BackupPage)
            }
        }
        HorizontalDivider(color = Brand.Line)
        Column(Modifier.padding(16.dp)) {
            GradientButton("New Patient", Modifier.fillMaxWidth(), Icons.Filled.Add, height = 50.dp) {
                onSelect(Screen.PatientForm())
            }
            if (appVersion.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("Version $appVersion", fontSize = 11.sp, color = Brand.Faint, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    trailing: String? = null,
    trailingColor: Color = Brand.Sub,
    dot: Color? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) Brand.GreenSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) Brand.GreenDeep else Brand.Sub, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f),
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) Brand.GreenText else Brand.Ink
        )
        if (trailing != null) Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = trailingColor, maxLines = 1)
        if (dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Brand.Faint,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** Top bar for the main sections. Shows the ☰ button when the menu is hidden. */
@Composable
fun SectionTopBar(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val openMenu = LocalMenu.current
    Row(
        Modifier.fillMaxWidth().background(Brand.Bg).statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (openMenu != null) {
            CircleIconButton(Icons.Filled.Menu, openMenu)
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 13.sp, color = Brand.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Bottom bar on phones: a floating dark bar with Home · Patients · Schedule · Finance */
@Composable
fun BottomNavBar(current: String, onSelect: (Screen) -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxWidth().background(Brand.Bg).navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .shadow(16.dp, shape, ambientColor = Color(0x26000000), spotColor = Color(0x40000000))
                .clip(shape).background(Brand.Charcoal).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavPill("Home", HomeIcons.Home, current == "home") { onSelect(Screen.Home) }
            NavPill("Patients", HomeIcons.People, current == "patients") { onSelect(Screen.Patients()) }
            NavPill("Schedule", HomeIcons.Calendar, current == "schedule") { onSelect(Screen.Schedule) }
            NavPill("Finance", HomeIcons.Rupee, current == "finance") { onSelect(Screen.Finance) }
        }
    }
}

@Composable
private fun RowScope.NavPill(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val weight by animateFloatAsState(if (selected) 1.9f else 1f, label = "navWeight")
    val bg by animateColorAsState(if (selected) Brand.GreenDeep else Color.Transparent, label = "navBg")
    Row(
        Modifier.weight(weight).height(50.dp).clip(RoundedCornerShape(25.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (selected) Color.White else Color(0xFFB4BDB8), modifier = Modifier.size(23.dp))
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}
