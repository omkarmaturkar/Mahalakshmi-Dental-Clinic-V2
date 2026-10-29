@file:OptIn(ExperimentalFoundationApi::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*

/** 0 Home, 1 Patients, 2 Schedule, 3 Finance; -1 for every other screen. */
fun tabIndexOf(screen: Screen): Int = when (screen) {
    is Screen.Home -> 0
    is Screen.Patients -> 1
    is Screen.Schedule -> 2
    is Screen.Finance -> 3
    else -> -1
}

private fun tabScreen(index: Int): Screen = when (index) {
    1 -> Screen.Patients()
    2 -> Screen.Schedule
    3 -> Screen.Finance
    else -> Screen.Home
}

/** Home, Patients, Schedule and Finance side by side: swipe left or right to move between them. */
@Composable
fun TabPager(current: Screen, db: ClinicDb, go: (Screen) -> Unit, open: (Screen?) -> Unit) {
    val index = tabIndexOf(current).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = index, pageCount = { 4 })
    val latest by rememberUpdatedState(current)

    // Bottom bar, menu or back button changed the tab: slide the pages to it
    LaunchedEffect(index) {
        if (pager.currentPage != index && !pager.isScrollInProgress) pager.animateScrollToPage(index)
    }
    // The user swiped to another page: make it the current tab
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (page != tabIndexOf(latest)) open(tabScreen(page))
        }
    }

    HorizontalPager(state = pager, key = { it }) { page ->
        when (page) {
            0 -> TodayScreen(db, go)
            1 -> {
                val p = latest as? Screen.Patients ?: Screen.Patients()
                key(p.filter, p.focusSearch) { PatientsScreen(db, p.filter, p.focusSearch, go) }
            }
            2 -> ScheduleScreen(db, go)
            else -> FinanceScreen(db, go)
        }
    }
}
