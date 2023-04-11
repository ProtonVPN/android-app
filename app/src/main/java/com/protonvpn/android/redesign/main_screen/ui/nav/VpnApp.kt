/*
 * Copyright (c) 2023. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(ExperimentalMaterialApi::class)

package com.protonvpn.android.redesign.main_screen.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.protonvpn.android.redesign.main_screen.ui.BottomBarView
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun VpnApp(
    modifier: Modifier = Modifier,
) {
    val mainNav = rememberMainNav()
    val bottomSheetNav = rememberBottomSheetNav()
    val mainNavHostInitialized = remember { mutableStateOf(false) }

    MainScreenNavigation(modifier, mainNav, bottomSheetNav, mainNavHostInitialized)

    // Make sure that NavHost for sheet is created after main one for proper back handler order.
    if (mainNavHostInitialized.value) {
        // Global bottom sheet need to be defined on the top of hierarchy so it's not drawn under
        // the bottom bar. Bottom sheet M3 will not have this limitation.
        BottomSheetNavigation(Modifier, bottomSheetNav)
    }
}

@Composable
private fun MainScreenNavigation(
    modifier: Modifier,
    mainNav: MainNav,
    bottomSheetNav: BottomSheetNav,
    mainNavHostInitialized: MutableState<Boolean>
) {
    val bottomTarget = mainNav.currentBottomBarTargetAsState()
    Scaffold(
        modifier = modifier.background(ProtonTheme.colors.backgroundNorm),
        bottomBar = {
            BottomBarView(selectedTarget = bottomTarget, navigateTo = mainNav::navigate)
        }
    ) { paddingValues ->
        mainNav.NavGraph(
            modifier.padding(paddingValues),
            bottomSheetNav
        )
        mainNavHostInitialized.value = true
    }
}

@Composable
private fun BottomSheetNavigation(
    modifier: Modifier,
    bottomSheetNav: BottomSheetNav
) {
    LaunchedEffect(bottomSheetNav.sheetState) {
        var wasVisible = false
        snapshotFlow { bottomSheetNav.sheetState.isVisible }.collect { isVisible ->
            // Clear nav stack when bottom sheet is hidden outside of our control (e.g. user
            // clicking the blend).
            if (wasVisible && !isVisible)
                bottomSheetNav.popUpToStart()
            wasVisible = isVisible
        }
    }

    ModalBottomSheetLayout(
        sheetState = bottomSheetNav.sheetState,
        sheetContent = {
            bottomSheetNav.NavGraph(modifier)
        }
    ) {}
}
