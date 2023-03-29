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

package com.protonvpn.android.uicatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme3

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
            ProtonTheme3 {
                Content()
            }
        }
    }
}

@Preview
@Composable
private fun PreviewContent() {
    ProtonTheme3(isDark = true) {
        Content()
    }
}

@Composable
private fun Content() {
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    val title = remember {
        navController.currentBackStackEntryFlow.map { navEntry ->
            sampleScreens
                .find { it.route == navEntry.destination.route }
                ?.title
        }
    }.collectAsStateWithLifecycle(initialValue = "").value

    val drawerContent: @Composable ColumnScope.() -> Unit = {
        Drawer(onSampleChanged = { newSample ->
            navController.navigateReplacing(newSample.route)
            coroutineScope.launch {
                scaffoldState.drawerState.close()
            }
        })
    }
    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text(title ?: "") },
            navigationIcon = {
                IconButton(
                    onClick = { coroutineScope.launch { scaffoldState.drawerState.open() } },
                ) {
                    Icon(Icons.Default.Menu, contentDescription = null)
                }
            }
        )
    }
    Scaffold(
        topBar = topBar,
        drawerContent = drawerContent,
        scaffoldState = scaffoldState
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            NavHost(navController = navController, startDestination = sampleScreens.first().route) {
                sampleScreens.forEach { sample ->
                    composable(sample.route) { sample.Content(modifier = Modifier) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Drawer(
    onSampleChanged: (SampleScreen) -> Unit
) {
    sampleScreens.forEach { sample ->
        // TODO: highlight current sample
        Text(
            sample.title,
            Modifier
                .fillMaxWidth()
                .clickable {
                    onSampleChanged(sample)
                }
                .padding(16.dp, 8.dp)
        )
        VerticalSpacer()
    }
}

private fun NavController.navigateReplacing(route: String) {
    navigate(route) {
        popUpTo(graph.id) {
            inclusive = true
        }
    }
}
