/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.app.tv.settings.splittunneling

import android.graphics.drawable.ColorDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.protonvpn.android.components.InstalledAppsProvider
import com.protonvpn.android.redesign.base.ui.nav.Screen
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.tv.settings.splittunneling.TvSettingsSplitTunnelingAppsVM
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.settings.SplitTunnelingAppsViewModelHelper
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.awaitMatchingItem
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.proton.core.util.kotlin.serialize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class) // Needed for classes like Uri.
class TvSettingsSplitTunnelingAppsVPMTests {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var mockAppsProvider: InstalledAppsProvider

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private val appA = "A app"
    private val appB = "B app"
    private val appSystemA = "A system"
    private val appSystemB = "B system"

    private val appNames = mapOf(
        "pkg.appb" to appB, // Non-alphabetic order to test sorting.
        "pkg.appa" to appA,
        "pkg.systemb" to appSystemB,
        "pkg.systema" to appSystemA,
    )

    private val regularAppPackages = listOf(pkg(appA), pkg(appB))
    private val systemAppPackages = listOf(pkg(appSystemA), pkg(appSystemB))

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        userSettingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory())
        )

        coEvery { mockAppsProvider.getInstalledInternetApps(true, true) } returns regularAppPackages
        coEvery { mockAppsProvider.getInstalledInternetApps(false, true) } returns systemAppPackages
        coEvery { mockAppsProvider.getAppInfos(any(), any()) } coAnswers {
            val packageNames: List<String> = secondArg()
            packageNames.map {
                InstalledAppsProvider.AppInfo(it, requireNotNull(appNames[it]), ColorDrawable(0x00000000))
            }
        }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty config, load regular apps`() = testScope.runTest {
        val viewModel = createViewModel(SplitTunnelingMode.INCLUDE_ONLY)
        val content = viewModel.viewState
            .filterIsInstance<SplitTunnelingAppsViewModelHelper.ViewState.Content>()
            .first()
        assertEquals(listOf(appA, appB), content.availableRegularApps.mapToNames())
        assertEquals(emptyList<String>(), content.selectedApps.mapToNames())
    }

    @Test
    fun `selected regular apps removed from available apps`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(isEnabled = true, includedApps = listOf(pkg(appA)))
        }
        val viewModel = createViewModel(SplitTunnelingMode.INCLUDE_ONLY)
        val content = viewModel.viewState
            .filterIsInstance<SplitTunnelingAppsViewModelHelper.ViewState.Content>()
            .first()
        assertEquals(listOf(appA), content.selectedApps.mapToNames())
        assertEquals(listOf(appB), content.availableRegularApps.mapToNames())
    }

    @Test
    fun `selected system apps are listed when system apps not loaded`() = testScope.runTest {
        userSettingsManager.updateSplitTunnelSettings {
            it.copy(isEnabled = true, includedApps = listOf(pkg(appSystemB), pkg(appA)))
        }
        val viewModel = createViewModel(SplitTunnelingMode.INCLUDE_ONLY)
        val content = viewModel.viewState
            .filterIsInstance<SplitTunnelingAppsViewModelHelper.ViewState.Content>()
            .first()
        assertEquals(listOf(appA, appSystemB), content.selectedApps.mapToNames())
        assertEquals(listOf(appB), content.availableRegularApps.mapToNames())
        assertIs<SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded>(content.availableSystemApps)
    }

    @Test
    fun `available system apps can be listed`() = testScope.runTest {
        val viewModel = createViewModel(SplitTunnelingMode.INCLUDE_ONLY)
        viewModel.viewState
            .filterIsInstance<SplitTunnelingAppsViewModelHelper.ViewState.Content>()
            .test {
                assertIs<SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded>(awaitItem().availableSystemApps)
                viewModel.toggleLoadSystemApps()
                assertIs<SplitTunnelingAppsViewModelHelper.SystemAppsState.Loading>(awaitItem().availableSystemApps)
                advanceUntilIdle()
                val loadedSystemApps = awaitItem().availableSystemApps
                assertIs<SplitTunnelingAppsViewModelHelper.SystemAppsState.Content>(loadedSystemApps)
                assertEquals(listOf(appSystemA, appSystemB), loadedSystemApps.apps.mapToNames())
            }
    }

    @Test
    fun `newly selected system apps don't disappear when system apps hidden`() = testScope.runTest {
        val viewModel = createViewModel(SplitTunnelingMode.INCLUDE_ONLY)
        viewModel.viewState
            .filterIsInstance<SplitTunnelingAppsViewModelHelper.ViewState.Content>()
            .test {
                viewModel.toggleLoadSystemApps()
                skipItems(1) // Skip the loading state.
                val loadedSystemApps = awaitItem().availableSystemApps
                assertIs<SplitTunnelingAppsViewModelHelper.SystemAppsState.Content>(loadedSystemApps)

                // Add system app to included
                val selectedApp = loadedSystemApps.apps.first()
                viewModel.addApp(selectedApp)
                assertEquals(listOf(selectedApp), awaitItem().selectedApps)

                // Hide system apps, the included app should still be in the UI.
                viewModel.toggleLoadSystemApps()
                val systemAppsHiddenState = awaitMatchingItem {
                    it.availableSystemApps is SplitTunnelingAppsViewModelHelper.SystemAppsState.NotLoaded
                }
                assertEquals(listOf(selectedApp), systemAppsHiddenState.selectedApps)
            }
    }

    private fun TestScope.createViewModel(mode: SplitTunnelingMode): TvSettingsSplitTunnelingAppsVM {
        val savedStateHandle = SavedStateHandle().apply {
            this[Screen.ARG_NAME] = mode.serialize()
        }
        return TvSettingsSplitTunnelingAppsVM(
            savedStateHandle,
            backgroundScope,
            TestDispatcherProvider(testDispatcher),
            mockAppsProvider,
            userSettingsManager,
        )
    }

    private fun pkg(appName: String) = requireNotNull(appNames.entries.find { (_, name) -> name == appName }).key
    private fun Iterable<LabeledItem>.mapToNames() = map { it.label }
}
