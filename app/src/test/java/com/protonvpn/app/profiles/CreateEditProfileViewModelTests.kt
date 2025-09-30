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
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.ProtonApplication
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
import com.protonvpn.android.profiles.usecases.UpdateConnectIntentForExistingServers
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.servers.api.SERVER_FEATURE_P2P
import com.protonvpn.android.servers.api.SERVER_FEATURE_RESTRICTED
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
import kotlin.test.assertIs

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
            id = -1L, // ID is returned by upsert when inserting the profile to DB.
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
    private val testProfileZurichP2P = testProfile.copy(
        info = testProfile.info.copy(name = "Profile Zurich P2P"),
        connectIntent = ConnectIntent.FastestInCity(CountryId("CH"), cityEn = "Zurich", setOf(ServerFeature.P2P))
    )

    private val serverGenevaRegular = createServer(exitCountry = "CH", city = "Geneva")
    private val serverGenevaP2P = createServer(exitCountry = "CH", city = "Geneva", features = SERVER_FEATURE_P2P)
    private val serverZurichRegular = createServer(exitCountry = "CH", city = "Zurich")
    private val serverLondonP2P = createServer(exitCountry = "UK", city = "London", features = SERVER_FEATURE_P2P)
    private val serverLondonRegular = createServer(exitCountry = "UK", city = "London")
    private val serverGateway = createServer(exitCountry = "CH", gatewayName = "Gateway", features = SERVER_FEATURE_RESTRICTED)

    @Before
    fun setup() {
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)
        Dispatchers.setMain(dispatcher)
        Storage.setPreferences(MockSharedPreference())
        // Context is needed by CountryTools - we should fix that.
        ProtonApplication.setAppContextForTest(ApplicationProvider.getApplicationContext<ProtonApplication>())

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
            servers
        )
        vpnStateMonitor = VpnStateMonitor()
        val serverManager2 = ServerManager2(serverManager, supportsProtocol)
        serversAdapter = ProfilesServerDataAdapter(
            serverManager2,
            Translator(testScope.backgroundScope, serverManager),
            UpdateConnectIntentForExistingServers(serverManager2),
        )
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
        val profileId = profilesDao.upsert(testProfile.toProfileEntity())

        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                ConnectionParams(testProfile.connectIntent, servers.first(), null, null)
            )
        )

        viewModel.setEditedProfileId(profileId, false)
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
        val profileId = profilesDao.upsert(testProfile.toProfileEntity())

        vpnStateMonitor.updateStatus(
            VpnStateMonitor.Status(
                VpnState.Connected,
                ConnectionParams(testProfile.connectIntent, servers.first(), null, null)
            )
        )

        viewModel.setEditedProfileId(profileId, false)
        viewModel.setNetShield(false)
        var addScreenClosed = false
        viewModel.saveOrShowReconnectDialog(false) { addScreenClosed = true }
        assertFalse(viewModel.showReconnectDialogFlow.value)
        assertTrue(addScreenClosed)

        assertEquals(listOf(NetShieldProtocol.DISABLED), profilesDao.getProfiles(vpnUser.userId).first().map { it.connectIntent.settingsOverrides?.netShield })
    }

    @Test
    fun `when country not available edit switches to fastest`() = testScope.runTest {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId.switzerland, emptySet())
        val profileId = profilesDao.upsert(testProfile.copy(connectIntent = connectIntent).toProfileEntity())

        viewModel.setEditedProfileId(profileId, false)
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
    fun `available types depend on available servers`() = testScope.runTest {
        val profileId = profilesDao.upsert(testProfile.toProfileEntity())
        viewModel.setEditedProfileId(profileId, false)

        val regular = createServer(exitCountry = "SE")
        val p2p = createServer(exitCountry = "SE", features = SERVER_FEATURE_P2P)
        val secureCore = createServer(exitCountry = "SE", entryCountry = "CH")
        val gateway = createServer(exitCountry = "SE", gatewayName = "Gateway")

        listOf(
            listOf(regular) to listOf(ProfileType.Standard),
            listOf(p2p) to listOf(ProfileType.Standard, ProfileType.P2P),
            listOf(gateway) to listOf(ProfileType.Gateway),
            listOf(secureCore) to listOf(ProfileType.SecureCore),
            listOf(regular, p2p, secureCore, gateway)
                to listOf(ProfileType.Standard, ProfileType.SecureCore, ProfileType.P2P, ProfileType.Gateway)
        ).forEachIndexed { index, (servers, expectedTypes) ->
            serverManager.setServers(servers, statusId = null, language = null)

            assertEquals(
                "Case $index",
                expectedTypes,
                viewModel.typeAndLocationScreenStateFlow.first().availableTypes
            )
        }
    }

    @Test
    fun `missing servers fallback - city P2P - drop city, preserve type`() = testScope.runTest {
        val profileId = profilesDao.upsert(testProfileZurichP2P.toProfileEntity())
        val servers = listOf(serverGenevaRegular, serverGenevaP2P, serverZurichRegular)
        serverManager.setServers(servers, statusId = null, language = null)
        viewModel.setEditedProfileId(profileId, false)

        val state = viewModel.typeAndLocationScreenStateFlow.first()
        assertIs<TypeAndLocationScreenState.P2P>(state)
        assertEquals(CountryId("CH"), state.country.id)
        assertEquals(null, state.cityOrState?.name)
    }

    @Test
    fun `missing servers fallback - city P2P - drop city and country, preserve type`() = testScope.runTest {
        val profileId = profilesDao.upsert(testProfileZurichP2P.toProfileEntity())
        val servers = listOf(serverGenevaRegular, serverZurichRegular, serverLondonP2P, serverLondonRegular)
        serverManager.setServers(servers, statusId = null, language = null)
        viewModel.setEditedProfileId(profileId, false)

        val state = viewModel.typeAndLocationScreenStateFlow.first()
        assertIs<TypeAndLocationScreenState.P2P>(state)
        assertEquals(CountryId.fastest, state.country.id)
        assertEquals(null, state.cityOrState?.name)
    }

    @Test
    fun `missing servers fallback - city P2P - no P2P servers`() = testScope.runTest {
        val profileId = profilesDao.upsert(testProfileZurichP2P.toProfileEntity())
        serverManager.setServers(listOf(serverLondonRegular), statusId = null, language = null)
        viewModel.setEditedProfileId(profileId, false)

        val state = viewModel.typeAndLocationScreenStateFlow.first()
        assertIs<TypeAndLocationScreenState.Standard>(state)
        assertEquals(CountryId.fastest, state.country.id)
    }

    @Test
    fun `missing servers fallback - city P2P - fallback to gateway`() = testScope.runTest {
        val profileId = profilesDao.upsert(testProfileZurichP2P.toProfileEntity())
        serverManager.setServers(listOf(serverGateway), statusId = null, language = null)
        viewModel.setEditedProfileId(profileId, false)

        val state = viewModel.typeAndLocationScreenStateFlow.first()
        assertIs<TypeAndLocationScreenState.Gateway>(state)
        assertEquals("Gateway", state.gateway.name)
    }

    @Test
    fun `missing servers fallback - gateway - fallback to fastest country`() = testScope.runTest {
        val gatewayProfile = testProfile.copy(connectIntent = ConnectIntent.Gateway("Gateway", null))
        val profileId = profilesDao.upsert(gatewayProfile.toProfileEntity())
        serverManager.setServers(listOf(serverLondonRegular), statusId = null, language = null)
        viewModel.setEditedProfileId(profileId, false)

        val state = viewModel.typeAndLocationScreenStateFlow.first()
        assertIs<TypeAndLocationScreenState.Standard>(state)
        assertEquals(CountryId.fastest, state.country.id)
    }
}
