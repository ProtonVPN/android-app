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

package com.protonvpn.app.redesign.settings.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.app.ui.SettingsChangeViewModel
import com.protonvpn.android.excludedlocations.data.ExcludedLocationsDao
import com.protonvpn.android.excludedlocations.data.toEntity
import com.protonvpn.android.excludedlocations.usecases.AddExcludedLocation
import com.protonvpn.android.excludedlocations.usecases.RemoveExcludedLocation
import com.protonvpn.android.redesign.settings.ui.SettingsReconnectHandler
import com.protonvpn.android.redesign.settings.ui.excludedlocations.toExcludedLocationUiItem
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.userstorage.DontShowAgainStateStoreProvider
import com.protonvpn.android.userstorage.DontShowAgainStore
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.app.excludedlocations.TestExcludedLocation
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsChangeViewModelTests {

    @RelaxedMockK
    private lateinit var mockConnectionManager: VpnConnectionManager

    @RelaxedMockK
    private lateinit var mockUiDelegate: VpnUiDelegate

    @RelaxedMockK
    private lateinit var mockExcludedLocationsDao: ExcludedLocationsDao

    private lateinit var settingsManager: CurrentUserLocalSettingsManager

    private lateinit var dontShowAgainStore: DontShowAgainStore
    private lateinit var testScope: TestScope
    private lateinit var vpnStateMonitor: VpnStateMonitor

    private lateinit var viewModel: SettingsChangeViewModel

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        val currentUser = CurrentUser(TestCurrentUserProvider(TestUser.plusUser.vpnUser))
        dontShowAgainStore = DontShowAgainStore(currentUser, DontShowAgainStateStoreProvider(InMemoryDataStoreFactory()))
        vpnStateMonitor = VpnStateMonitor()
        settingsManager = CurrentUserLocalSettingsManager(
            LocalUserSettingsStoreProvider(InMemoryDataStoreFactory()),
        )

        viewModel = SettingsChangeViewModel(
            userSettingsManager = settingsManager,
            reconnectHandler = SettingsReconnectHandler(
                mainScope = testScope.backgroundScope,
                vpnConnectionManager = mockConnectionManager,
                vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor),
                dontShowAgainStore = dontShowAgainStore,
                savedStateHandle = SavedStateHandle(),
            ),
            removeExcludedLocation = RemoveExcludedLocation(
                currentUser = currentUser,
                excludedLocationsDao = mockExcludedLocationsDao,
            ),
            addExcludedLocation = AddExcludedLocation(
                currentUser = currentUser,
                excludedLocationsDao = mockExcludedLocationsDao,
            ),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lan connection triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, viewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `protocol update triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.updateProtocol(mockUiDelegate, ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
        assertEquals(DontShowAgainStore.Type.ProtocolChangeWhenConnected, viewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `split tunnel update triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.onSplitTunnelingUpdated(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected, viewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `split tunnel toggle with exclusions triggers reconnect dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        settingsManager.update { current ->
            val newSplitTunnelingSettings = SplitTunnelingSettings(
                isEnabled = false,
                mode = SplitTunnelingMode.EXCLUDE_ONLY,
                excludedApps = listOf("app1")
            )
            current.copy(splitTunneling = newSplitTunnelingSettings)
        }
        viewModel.onSplitTunnelingUpdated(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.SplitTunnelingChangeWhenConnected, viewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `split tunnel toggle with empty exclusions don't triggers reconnect dialog`() = testScope.runTest {
        settingsManager.update { current ->
            val newSplitTunnelingSettings = SplitTunnelingSettings(
                isEnabled = false,
                mode = SplitTunnelingMode.EXCLUDE_ONLY
            )
            current.copy(splitTunneling = newSplitTunnelingSettings)
        }
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.toggleSplitTunneling(mockUiDelegate)
        assertEquals(null, viewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `no reconnection dialog when not connected`() = testScope.runTest {
        viewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(null, viewModel.showReconnectDialogFlow.first())
    }

    @Test
    fun `saved reconnection 'yes' choice reconnects automatically`() = testScope.runTest {
        dontShowAgainStore.setChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, DontShowAgainStore.Choice.Positive)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(null, viewModel.showReconnectDialogFlow.first())
        coVerify(exactly = 1) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `saved reconnection 'no' don't reconnects automatically`() = testScope.runTest {
        dontShowAgainStore.setChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, DontShowAgainStore.Choice.Negative)
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(null, viewModel.showReconnectDialogFlow.first())
        coVerify(exactly = 0) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `reconnect & save reconnection dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, viewModel.showReconnectDialogFlow.first())
        viewModel.onReconnectClicked(mockUiDelegate, true, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        assertEquals(DontShowAgainStore.Choice.Positive, dontShowAgainStore.getChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected))
        coVerify(exactly = 1) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `dismiss & save reconnection dialog`() = testScope.runTest {
        vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, mockk(relaxed = true)))
        viewModel.toggleLanConnections(mockUiDelegate)
        assertEquals(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected, viewModel.showReconnectDialogFlow.first())
        viewModel.dismissReconnectDialog(true, DontShowAgainStore.Type.LanConnectionsChangeWhenConnected)
        assertEquals(DontShowAgainStore.Choice.Negative, dontShowAgainStore.getChoice(DontShowAgainStore.Type.LanConnectionsChangeWhenConnected))
        coVerify(exactly = 0) { mockConnectionManager.reconnect(any(), any()) }
    }

    @Test
    fun `WHEN removing excluded location THEN delete excluded location is called`() {
        val userId = TestUser.plusUser.vpnUser.userId
        val excludedLocation = TestExcludedLocation.create()
        val location = excludedLocation.toExcludedLocationUiItem(locale = Locale.ENGLISH, translations = null)
        val expectedExcludedLocationEntity = excludedLocation.toEntity(userId = userId)

        viewModel.onRemoveExcludedLocation(location = location)

        coVerify(exactly = 1) { mockExcludedLocationsDao.delete(entity = expectedExcludedLocationEntity) }
    }

    @Test
    fun `WHEN removing excluded location THEN excluded location removal event is emitted`() = testScope.runTest {
        val excludedLocation = TestExcludedLocation.create()
        val location = excludedLocation.toExcludedLocationUiItem(locale = Locale.ENGLISH, translations = null)
        val expectedEvent = SettingsChangeViewModel.ExcludedLocationEvent.OnRemoved(location = location)

        viewModel.excludedLocationEventsFlow.test {
            viewModel.onRemoveExcludedLocation(location = location)

            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

    @Test
    fun `WHEN adding excluded location THEN insert excluded location is called`() {
        val userId = TestUser.plusUser.vpnUser.userId
        val excludedLocation = TestExcludedLocation.create()
        val location = excludedLocation.toExcludedLocationUiItem(locale = Locale.ENGLISH, translations = null)
        val expectedExcludedLocationEntity = excludedLocation.toEntity(userId = userId)

        viewModel.onAddExcludedLocation(location = location)

        coVerify(exactly = 1) { mockExcludedLocationsDao.insert(entity = expectedExcludedLocationEntity) }
    }

}
