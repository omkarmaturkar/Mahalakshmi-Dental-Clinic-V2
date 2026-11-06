package com.mahalakshmi.dental

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** All patients with search and filters. filter: 0 = all, 1 = pending dues, 2 = today. */
@Composable
fun PatientsScreen(db: ClinicDb, startFilter: Int, focusSearch: Boolean, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val version = DataBus.version
    val everyone = remember(version) { db.getPatients("") }
    val todayIds = remember(version) { db.upcoming(0).map { it.patientId }.toSet() }
    val clinicName = remember(version) { db.clinicInfo().name }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(startFilter) }
    val shown = remember(search, filter, everyone, todayIds) { filterPatients(everyone, search, filter, todayIds) }
    val dueCount = remember(everyone) { everyone.count { it.remaining > 0.0 } }
    val dueTotal = remember(everyone) { everyone.sumOf { it.remaining.coerceAtLeast(0.0) } }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Opened from a search button: put the cursor in the search box straight away
    LaunchedEffect(Unit) {
        if (focusSearch) {
            delay(400)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
        val wide = maxWidth >= 700.dp
        val side = if (wide) 24.dp else 20.dp
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                if (filter == 1) "Pending dues" else "Patients",
                if (filter == 1) "${rs(dueTotal)} from $dueCount patients" else "${everyone.size} patients"
            ) {
                if (wide) {
                    GradientButton("New Patient", Modifier.width(190.dp), Icons.Filled.Add, height = 48.dp) {
                        go(Screen.PatientForm())
                    }
                }
            }
            Column(Modifier.padding(horizontal = side)) {
                PatientSearchField(search, focus) { search = it }
                Spacer(Modifier.height(12.dp))
                PatientFilters(filter, everyone.size, dueCount) { filter = it }
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState(
                                if (everyone.isEmpty()) "No patients yet" else "No patients found",
                                if (everyone.isEmpty()) "Tap \"New Patient\" to add your first patient."
                                else "Try a different name, number or filter."
                            )
                        }
                    }
                    items(shown, key = { it.id }) { p ->
                        if (filter == 1) {
                            DueCard(p, onOpen = { go(Screen.PatientDetail(p.id)) }) {
                                openWhatsApp(context, p.phone, reminderMessage(p, null, clinicName))
                            }
                        } else {
                            PatientCard(p) { go(Screen.PatientDetail(p.id)) }
                        }
                    }
                }
                if (!wide) {
                    GradientFab(
                        "New Patient", Icons.Filled.Add,
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                    ) { go(Screen.PatientForm()) }
                }
            }
        }
    }
}

@Composable
private fun PatientSearchField(value: String, focus: FocusRequester, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape).background(Brand.Surface)
            .border(1.dp, Brand.Line, shape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = Brand.Faint)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search by name or mobile number", color = Brand.Faint, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Poppins, fontSize = 15.sp, color = Brand.Ink),
                cursorBrush = SolidColor(Brand.GreenDeep),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Brand.Soft).clickable { onChange("") },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Close, null, tint = Brand.Sub, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun DueCard(p: Patient, onOpen: () -> Unit, onRemind: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Brand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Due ${rs(p.remaining)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Brand.Red)
                if (p.lastVisit > 0L) {
                    Text("Last visit: ${relativeTime(p.lastVisit)}", fontSize = 12.sp, color = Brand.Sub)
                }
            }
            if (p.phone.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                SoftButton("Remind", AppIcons.Chat, height = 42.dp, onClick = onRemind)
            }
        }
    }
}
