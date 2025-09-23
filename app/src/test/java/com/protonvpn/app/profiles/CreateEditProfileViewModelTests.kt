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

package com.protonvpn.app.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfileEntity
import com.protonvpn.android.profiles.ui.CreateEditProfileViewModel
import com.protonvpn.android.profiles.ui.ProfileType
import com.protonvpn.android.profiles.ui.ProfilesServerDataAdapter
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.ShouldAskForProfileReconnection
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.profiles.usecases.CreateOrUpdateProfileFromUi
import com.protonvpn.android.profiles.usecases.PrivateBrowsingAvailability
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.telemetry.ProfilesTelemetry
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.ui.storage.UiStateStoreProvider
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.usecases.TransientMustHaves
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.FakeIsLanDirectConnectionsFeatureFlagEnabled
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createServer
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession1
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateEditProfileViewModelTests {

    private lateinit var serverManager: ServerManager
    private lateinit var vpnStateMonitor: VpnStateMonitor
    private lateinit var testScope: TestScope
    private lateinit var profilesDao: ProfilesDao
    private lateinit var serversAdapter: ProfilesServerDataAdapter
    private lateinit var shouldAskForProfileReconnection: ShouldAskForProfileReconnection
    private lateinit var viewModel: CreateEditProfileViewModel
    private lateinit var isPrivateDnsActiveFlow: MutableStateFlow<Boolean>

    private val servers = listOf(createServer(exitCountry = "SE"))
    private val vpnUser =
        TestUser.plusUser.vpnInfoResponse.toVpnUserEntity(TestAccount1.userId, TestSession1.sessionId, 0L, null)
    private val settingsScreenState = SettingsScreenState(
        netShield = true,
        protocol = ProtocolSelection(VpnProtocol.WireGuard),
        natType = NatType.Moderate,
        lanConnections = true,
        lanConnectionsAllowDirect = false,
        autoOpen = ProfileAutoOpen.None,
        customDnsSettings = CustomDnsSettings(false),
        isAutoOpenNew = true,
        isPrivateDnsActive = false,
        showPrivateBrowsing = true,
    )
    // Matches the screen states above.
    private val testProfile = Profile(
        userId = vpnUser.userId,
        info = ProfileInfo(
            id = 1L,
            name = "Profile 1",
            color = ProfileColor.Color1,
            icon = ProfileIcon.Icon1,
            createdAt = 1_000,
            isUserCreated = false,
        ),
        connectIntent = ConnectIntent.FastestInCountry(
            CountryId.sweden,
            emptySet(),
            profileId = 1L,
            settingsOverrides = settingsScreenState.toSettingsOverrides(),
        ),
        autoOpen = ProfileAutoOpen.None,
    )

    @Before
    fun setup() {
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)
        Dispatchers.setMain(dispatcher)
        Storage.setPreferences(MockSharedPreference())

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .buildDatabase()

        val accountHelper = AccountTestHelper()
        accountHelper.withAccountManager(db) { accountManager ->
            accountManager.addAccount(TestAccount1, TestSession1)
        }
        profilesDao = db.profilesDao()

        val currentUser = CurrentUser(TestCurrentUserProvider(vpnUser))
        val profilesTelemetry = ProfilesTelemetry(
            FakeCommonDimensions(mapOf("user_tier" to "paid")),
            TelemetryFlowHelper(testScope.backgroundScope, TestTelemetryReporter())
        )

        val createOrUpdate = CreateOrUpdateProfileFromUi(
            testScope.backgroundScope,
            profilesDao,
            currentUser,
            telemetry = profilesTelemetry,
            wallClock = { testScope.currentTime },
            getPrivateBrowsingAvailability = { PrivateBrowsingAvailability.AvailableWithDefault }
        )
        val supportsProtocol = SupportsProtocol(createGetSmartProtocols())
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(dispatcher),
            supportsProtocol,
            currentUser,
            servers
        )
        vpnStateMonitor = VpnStateMonitor()
        serversAdapter = ProfilesServerDataAdapter(ServerManager2(serverManager, supportsProtocol), Translator(testScope.backgroundScope, serverManager))
        val vpnStatusProviderUI = VpnStatusProviderUI(testScope.backgroundScope, vpnStateMonitor)
        shouldAskForProfileReconnection = ShouldAskForProfileReconnection(vpnStatusProviderUI, profilesDao, createOrUpdate)
        isPrivateDnsActiveFlow = MutableStateFlow(false)
        viewModel = CreateEditProfileViewModel(
            savedStateHandle = SavedStateHandle(),
            mainScope = testScope.backgroundScope,
            appContext = appContext,
            profilesDao = profilesDao,
            createOrUpdateProfile = createOrUpdate,
            adapter = serversAdapter,
            vpnConnect = { _, connectIntent, _ -> vpnStateMonitor.updateStatus(VpnStateMonitor.Status(VpnState.Connected, ConnectionParams(connectIntent, servers.first(), null, null)) ) },
            vpnBackgroundUiDelegate = mockk(relaxed = true),
            shouldAskForReconnection = shouldAskForProfileReconnection,
            uiStateStorage = UiStateStorage(UiStateStoreProvider(InMemoryDataStoreFactory()), currentUser),
            isPrivateDnsActiveFlow = IsPrivateDnsActiveFlow(isPrivateDnsActiveFlow),
            isDirectLanConnectionsFeatureFlagEnabled = FakeIsLanDirectConnectionsFeatureFlagEnabled(true),
            transientMustHaves = TransientMustHaves({ testScope.currentTime }),
            autoOpenAppInfoHelper = mockk(relaxed = true),
            getPrivateBrowsingAvailability = { PrivateBrowsingAvailability.AvailableWithDefault },
        )
        viewModel.localeFlow.value = Locale("en")
    }

    @Test
    fun `profile is added`() = testScope.runTest {
        viewModel.setEditedProfileId(null, false)
        viewModel.setName("MyProfile")
        var addScreenClosed = false
        viewModel.saveOrShowReconnectDialog(false) { addScreenClosed = true }
        assertFalse(viewModel.showReconnectDialogFlow.value)
        assertTrue(addScreenClosed)
        assertEquals(listOf("MyProfile"), profilesDao.getProfiles(vpnUser.userId).first().map { it.info.name })
    }

    @Test
    fun `profile edit require reconnection on protocol change of currently connected profile`() = testScope.runTest {
        profilesDao.upsert(testProfile.toProfileEntity())

        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                ConnectionParams(testProfile.connectIntent, servers.first(), null, null)
            )
        )

        viewModel.setEditedProfileId(testProfile.info.id, false)
        val newProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP)
        viewModel.setProtocol(newProtocol)
        var addScreenClosed = false
        viewModel.saveOrShowReconnectDialog(false) { addScreenClosed = true }
        assertTrue(viewModel.showReconnectDialogFlow.value)
        assertFalse(addScreenClosed)

        viewModel.saveAndReconnect(false)
        assertFalse(viewModel.showReconnectDialogFlow.value)
        assertEquals(newProtocol, vpnStateMonitor.connectionIntent?.settingsOverrides?.protocol)
    }

    @Test
    fun `profile edit doesnt require reconnection on netshield change of currently connected profile`() = testScope.runTest {
        profilesDao.upsert(testProfile.toProfileEntity())

        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                ConnectionParams(testProfile.connectIntent, servers.first(), null, null)
            )
        )

        viewModel.setEditedProfileId(testProfile.info.id, false)
        viewModel.setNetShield(false)
        var addScreenClosed = false
        viewModel.saveOrShowReconnectDialog(false) { addScreenClosed = true }
        assertFalse(viewModel.showReconnectDialogFlow.value)
        assertTrue(addScreenClosed)

        assertEquals(listOf(NetShieldProtocol.DISABLED), profilesDao.getProfiles(vpnUser.userId).first().map { it.connectIntent.settingsOverrides?.netShield })
    }

    @Test
    fun `when country not available edit switches to fastest`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.switzerland, emptySet(), testProfile.info.id)
        profilesDao.upsert(testProfile.copy(connectIntent = connectIntent).toProfileEntity())
        viewModel.setEditedProfileId(testProfile.info.id, false)
        val typeAndLocation = viewModel.typeAndLocationScreenStateFlow.first() as TypeAndLocationScreenState.Standard
        assertEquals(
            listOf(
                TypeAndLocationScreenState.CountryItem(CountryId.fastest, true),
                TypeAndLocationScreenState.CountryItem(CountryId.fastestExcludingMyCountry, true),
                TypeAndLocationScreenState.CountryItem(CountryId.sweden, true)
            ),
            typeAndLocation.selectableCountries
        )
        val savedProfile = viewModel.save().await()
        assertEquals(CountryId.fastest, (savedProfile?.connectIntent as? ConnectIntent.FastestInCountry)?.country)
    }

    @Test
    fun `gateway type available only if gateways exists`() = testScope.runTest {
        profilesDao.upsert(testProfile.toProfileEntity())
        viewModel.setEditedProfileId(testProfile.info.id, false)
        assertEquals(
            listOf(ProfileType.Standard, ProfileType.SecureCore, ProfileType.P2P),
            viewModel.typeAndLocationScreenStateFlow.first().availableTypes
        )

        serverManager.setServers(
            listOf(
                createServer(exitCountry = "SE"),
                createServer(exitCountry = "CH", gatewayName = "Gateway1")
            ),
            statusId = null,
            language = "en"
        )

        assertEquals(
            listOf(ProfileType.Standard, ProfileType.SecureCore, ProfileType.P2P, ProfileType.Gateway),
            viewModel.typeAndLocationScreenStateFlow.first().availableTypes
        )
    }
}
