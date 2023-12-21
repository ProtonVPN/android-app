/*
 * Copyright (c) 2023 Proton AG
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

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.protonvpn.android.redesign.base.ui.nav.BaseNav
import com.protonvpn.android.redesign.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.redesign.base.ui.nav.ScreenNoArg
import com.protonvpn.android.redesign.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.countries.ui.nav.CountryScreen.country
import com.protonvpn.android.redesign.countries.ui.nav.ServersScreen
import com.protonvpn.android.redesign.countries.ui.nav.ServersScreen.servers
import com.protonvpn.android.redesign.main_screen.ui.nav.EmptyScreenSheet.emptySheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberBottomSheetNav(
    scope: CoroutineScope = rememberCoroutineScope(),
    navController: NavHostController = rememberNavController(),
    sheetState: ModalBottomSheetState =
        rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden),
): BottomSheetNav =
    remember(scope, navController, sheetState) {
        BottomSheetNav(scope, navController, sheetState)
    }

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetNavigation(
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
            bottomSheetNav.NavHost(modifier)
        }
    ) {}
}

class BottomSheetNav(
    val scope: CoroutineScope,
    bottomSheetNavController: NavHostController,
    val sheetState: ModalBottomSheetState,
) : BaseNav<BottomSheetNav>(bottomSheetNavController, "sheet") {

    fun navigate(screen: Screen<Unit, BottomSheetNav>) {
        navigate(screen, Unit)
    }

    inline fun <reified A : Any> navigate(screen: Screen<A, BottomSheetNav>, arg: A) {
        scope.launch {
            if (!sheetState.isVisible)
                sheetState.show()
            navigateInternal(screen, arg, navOptions { launchSingleTop = true })
        }
    }

    fun show() {
        scope.launch {
            if (!sheetState.isVisible)
                sheetState.show()
        }
    }

    fun hide() {
        scope.launch {
            if (sheetState.isVisible)
                sheetState.hide()
        }
    }

    @Composable
    fun NavHost(modifier: Modifier) {
        SafeNavHost(
            modifier = modifier,
            startScreen = EmptyScreenSheet
        ) {
            // Hide the sheet whenever navigated to empty route
            emptySheet(this@BottomSheetNav)
            country(onCityClicked = { city ->
                navigate(ServersScreen, ServersScreen.Args("", city))
            })
            servers(onServerClicked = {
                popUpToStart()
            })
        }

        // TODO: there seems to be some issue causing the sheet to enter hidden state after
        //  restore. This prevents the sheet from being hidden.
        if (currentRoute() != EmptyScreenSheet.route)
            show()
    }
}

private object EmptyScreenSheet : ScreenNoArg<BottomSheetNav>("empty") {

    fun SafeNavGraphBuilder<BottomSheetNav>.emptySheet(bottomSheetNav: BottomSheetNav) =
        addToGraph(this) {
            LaunchedEffect(bottomSheetNav.sheetState) {
                bottomSheetNav.hide()
            }
        }
}
