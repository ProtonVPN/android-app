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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.protonvpn.android.uicatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.protonvpn.android.base.ui.theme.VpnTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

abstract class SampleScreen(
    val title: String,
    val route: String
) {
    @Composable
    abstract fun Content(modifier: Modifier)
}

private val sampleScreens: List<SampleScreen> = listOf(
    CoreButtonsSample(),
    NetShieldSample(),
)

class UiCatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDark by remember { mutableStateOf(true) }
            VpnTheme(isDark = isDark) {
                Content(isDarkModeEnabled = isDark, onDarkModeToggle = { isDark = !isDark })
            }
        }
    }
}

@Preview
@Composable
private fun PreviewContent() {
    VpnTheme(isDark = true) {
        Content(true, {})
    }
}

@Composable
private fun Content(
    isDarkModeEnabled: Boolean, // Only for the toggle, this composable doesn't use theme,
    onDarkModeToggle: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var forceRtl by remember { mutableStateOf(false) }

    val currentSample = remember {
        navController.currentBackStackEntryFlow.map { navEntry ->
            sampleScreens
                .find { it.route == navEntry.destination.route }
        }
    }.collectAsStateWithLifecycle(initialValue = null).value

    val drawerContent: @Composable () -> Unit = {
        Drawer(
            selectedSample = currentSample,
            onSampleChanged = { newSample ->
                navController.navigateReplacing(newSample.route)
                coroutineScope.launch {
                    drawerState.close()
                }
            }
        )
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(currentSample?.title ?: "") },
            navigationIcon = {
                IconButton(
                    onClick = { coroutineScope.launch { drawerState.open() } },
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            actions = {
                val actionModifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .padding(4.dp, 8.dp)
                CheckboxAction(
                    "Dark",
                    checked = isDarkModeEnabled,
                    onCheckedChange = { onDarkModeToggle() },
                    modifier = actionModifier
                )
                CheckboxAction(
                    "RTL",
                    checked = forceRtl,
                    onCheckedChange = { forceRtl = !forceRtl },
                    modifier = actionModifier
                )
            }
        )
    }
    ModalNavigationDrawer(
        drawerContent = drawerContent,
        drawerState = drawerState
    ) {
        Scaffold(
            topBar = topBar,
        ) { paddingValues ->
            val direction = if (forceRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                NavHost(navController = navController, startDestination = sampleScreens.first().route) {
                    sampleScreens.forEach { sample ->
                        composable(sample.route) {
                            sample.Content(
                                modifier = Modifier
                                    .padding(paddingValues)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Drawer(
    selectedSample: SampleScreen?,
    onSampleChanged: (SampleScreen) -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(24.dp))
        sampleScreens.forEach { sample ->
            NavigationDrawerItem(
                label = { Text(sample.title) },
                onClick = { onSampleChanged(sample) },
                selected = sample == selectedSample,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

// Note: it's not a very generic implementation, don't reuse it directly.
@Composable
private fun CheckboxAction(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        Modifier
            .toggleable(checked, onValueChange = onCheckedChange, role = Role.Checkbox)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics {})
    }
}

private fun NavController.navigateReplacing(route: String) {
    navigate(route) {
        popUpTo(graph.id) {
            inclusive = true
        }
    }
}
