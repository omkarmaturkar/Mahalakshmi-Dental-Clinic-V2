package com.mahalakshmi.dental

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var db: ClinicDb

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashGuard.install(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        db = ClinicDb(applicationContext)
        val lastProblem = CrashGuard.read(this)
        setContent {
            ClinicTheme {
                var problem by remember { mutableStateOf(lastProblem) }
                val report = problem
                if (report != null) {
                    // The app closed because of an error last time: show the report first
                    CrashReportScreen(report) {
                        CrashGuard.clear(applicationContext)
                        problem = null
                    }
                } else {
                    var showSplash by remember { mutableStateOf(true) }
                    // Short welcome (about 1 second), then straight into the app
                    LaunchedEffect(Unit) {
                        delay(1100)
                        showSplash = false
                    }
                    Crossfade(targetState = showSplash, animationSpec = tween(350), label = "splash") { splash ->
                        if (splash) SplashScreen { showSplash = false } else ClinicApp(db)
                    }
                }
            }
        }
    }

    // When the app goes to the background: backup to the tablet folder + daily Google Drive backup
    override fun onStop() {
        super.onStop()
        val ctx = applicationContext
        Thread {
            try {
                Backup.autoBackup(ctx, db)
                DriveBackup.autoBackup(ctx, db)
            } catch (e: Throwable) {
                // A failed background backup should never close the app
            }
        }.start()
    }
}

sealed class Screen {
    object Home : Screen()
    object BackupPage : Screen()
    object ClinicSettings : Screen()
    data class Patients(val filter: Int = 0, val focusSearch: Boolean = false) : Screen()
    data class PatientForm(val patientId: Long = 0) : Screen()
    data class PatientDetail(val patientId: Long) : Screen()

    /** Opens the patient file with "Add treatment" ready (used by the "Start" button on Today). */
    data class VisitForm(val patientId: Long, val visitId: Long = 0) : Screen()

    /** A prescription: rxId 0 = new one. */
    data class RxForm(val patientId: Long, val rxId: Long = 0) : Screen()
}

data class NavEntry(val depth: Int, val screen: Screen)

@Composable
fun ClinicApp(db: ClinicDb) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val go: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val open: (Screen?) -> Unit = { target ->
        stack.clear()
        stack.add(Screen.Home)
        if (target != null && target != Screen.Home) stack.add(target)
    }
    BackHandler(enabled = stack.size > 1) { back() }
    StatusBarIcons(dark = true)

    var message by remember { mutableStateOf<String?>(null) }
    var shownText by remember { mutableStateOf("") }
    var messageId by remember { mutableIntStateOf(0) }
    LaunchedEffect(messageId) {
        if (messageId > 0) {
            delay(2600)
            message = null
        }
    }
    val notify: (String) -> Unit = { text ->
        shownText = text
        message = text
        messageId++
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalNotify provides notify) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Brand.Bg)) {
            val wide = maxWidth >= 900.dp
            val menuWidth = if (maxWidth >= 1200.dp) 280.dp else 250.dp
            val entry = NavEntry(stack.size, stack.last())
            val section = stack.getOrNull(1) ?: Screen.Home
            val menu: @Composable () -> Unit = {
                AppMenu(db, section, permanent = wide) { target ->
                    open(target)
                    scope.launch { drawer.close() }
                }
            }

            if (wide) {
                // Tablet held sideways: the menu is always visible on the left
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(menuWidth).fillMaxHeight()) { menu() }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Brand.Line))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CompositionLocalProvider(LocalMenu provides null) {
                            NavArea(entry) { screen -> ScreenHost(screen, db, stack, go, back, open) }
                        }
                    }
                }
            } else {
                // Phone or upright tablet: the menu slides out from the ☰ button
                BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
                ModalNavigationDrawer(
                    drawerState = drawer,
                    gesturesEnabled = drawer.isOpen,
                    drawerContent = {
                        ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.width(300.dp)) { menu() }
                    }
                ) {
                    CompositionLocalProvider(LocalMenu provides { scope.launch { drawer.open() } }) {
                        NavArea(entry) { screen -> ScreenHost(screen, db, stack, go, back, open) }
                    }
                }
            }

            AnimatedVisibility(
                visible = message != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
            ) { NotifyBanner(shownText) }
        }
    }
}

@Composable
private fun NavArea(entry: NavEntry, content: @Composable (Screen) -> Unit) {
    AnimatedContent(
        targetState = entry,
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 8 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 8 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200)))
            }
        },
        label = "navigation"
    ) { e ->
        Box(Modifier.fillMaxSize().background(Brand.Bg)) { content(e.screen) }
    }
}

@Composable
private fun ScreenHost(
    screen: Screen,
    db: ClinicDb,
    stack: MutableList<Screen>,
    go: (Screen) -> Unit,
    back: () -> Unit,
    open: (Screen?) -> Unit
) {
    when (screen) {
        is Screen.Home -> TodayScreen(db, go)
        is Screen.Patients -> PatientsScreen(db, screen.filter, screen.focusSearch, go)
        is Screen.BackupPage -> BackupScreen(db, back) { open(null) }
        is Screen.ClinicSettings -> ClinicSettingsScreen(db, back)
        is Screen.PatientForm -> PatientEditScreen(
            db, screen.patientId, back,
            onSaved = { id, isNew ->
                stack.removeAt(stack.lastIndex)
                if (isNew) stack.add(Screen.PatientDetail(id))
            },
            onDeleted = {
                repeat(2) { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
            }
        )
        is Screen.PatientDetail -> PatientFileScreen(db, screen.patientId, false, go, back)
        is Screen.VisitForm -> PatientFileScreen(db, screen.patientId, true, go, back)
        is Screen.RxForm -> PrescriptionScreen(db, screen.patientId, screen.rxId, back)
    }
}
