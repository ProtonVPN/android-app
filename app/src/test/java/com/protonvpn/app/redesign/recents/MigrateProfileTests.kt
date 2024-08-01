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

package com.protonvpn.app.redesign.recents

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.SERVER_FEATURE_P2P
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.usecases.MigrateProfiles
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.ServersDataManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.test.shared.createGetSmartProtocols
import com.protonvpn.test.shared.createInMemoryServersStore
import com.protonvpn.test.shared.createIsImmutableServerListEnabled
import com.protonvpn.test.shared.createServer
import com.protonvpn.testsHelper.AccountTestHelper
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MigrateProfileTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    private lateinit var currentUser: CurrentUser
    private lateinit var profileManager: ProfileManager
    private lateinit var recentsDao: RecentsDao
    private lateinit var serverManager: ServerManager
    private lateinit var settingsFlow: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope

    private val secureCoreServer =
        createServer("secureCore", exitCountry = "PL", entryCountry = "CH", isSecureCore = true)

    private val servers = listOf(
        createServer("server1"),
        createServer("server2", features = SERVER_FEATURE_P2P),
        secureCoreServer,
        createServer("gateway1", gatewayName = "gateway")
    )

    private val userId = AccountTestHelper.TestAccount1.userId
    private val vpnUser = TestUser.plusUser.vpnUser.copy(userId = userId)

    private lateinit var migrateProfiles: MigrateProfiles

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        val bgScope = testScope.backgroundScope

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .setQueryExecutor(Executors.newSingleThreadExecutor())
            .buildDatabase()

        val accountHelper = AccountTestHelper()
        accountHelper.withAccountManager(db) { accountManager ->
            accountManager.addAccount(AccountTestHelper.TestAccount1, AccountTestHelper.TestSession1)
        }
        recentsDao = db.recentsDao()

        Storage.setPreferences(MockSharedPreference())

        settingsFlow = MutableStateFlow(LocalUserSettings.Default)
        val userSettingsCached = EffectiveCurrentUserSettingsCached(settingsFlow)
        val userSettings = EffectiveCurrentUserSettings(bgScope, settingsFlow)

        profileManager = ProfileManager(
            SavedProfilesV3.defaultProfiles(),
            bgScope,
            userSettingsCached,
            userSettingsManager = mockk(relaxed = true)
        )

        currentUser = CurrentUser(TestCurrentUserProvider(vpnUser))

        val serversDataManager = ServersDataManager(
            bgScope,
            TestDispatcherProvider(testDispatcher),
            createInMemoryServersStore(),
            { createIsImmutableServerListEnabled(true) }
        )
        serverManager = ServerManager(
            bgScope,
            userSettingsCached,
            currentUser,
            { 0 },
            SupportsProtocol(createGetSmartProtocols()),
            serversDataManager,
            profileManager,
        )
        runBlocking {
            serverManager.setServers(servers, null)
        }
        serverManager.setBuiltInGuestHoleServersForTesting(emptyList())

        migrateProfiles = MigrateProfiles(profileManager, serverManager, recentsDao, currentUser, userSettings)
    }

    @Test
    fun profilesAreMigratedToRecentPinnedItems() = testScope.runTest {
        // Keep in mind that ServerManager always has the two pre-baked profiles.
        val userProfiles = listOf(
            createProfile(ServerWrapper.makeFastestForCountry("CH"), false),
            createProfile(ServerWrapper.makeFastestForCountry("PL"), true),
            createProfile(ServerWrapper.makeRandomForCountry("LT"), false),
            createProfile(ServerWrapper.makeFastestForCountry("DE"), false),
            createProfile(ServerWrapper.makeWithServer(servers[0]), false),
            createProfile(ServerWrapper.makeWithServer(servers[1]), false),
            createProfile(ServerWrapper.makeWithServer(secureCoreServer), true),
        )
        val expectedConnectIntents = listOf(
            ConnectIntent.SecureCore(CountryId(secureCoreServer.exitCountry), CountryId(secureCoreServer.entryCountry)),
            ConnectIntent.Server(servers[1].serverId, emptySet()),
            ConnectIntent.Server(servers[0].serverId, emptySet()),
            ConnectIntent.FastestInCountry(CountryId("DE"), emptySet()),
            ConnectIntent.FastestInCountry(CountryId("LT"), emptySet()),
            ConnectIntent.SecureCore(CountryId("PL"), CountryId.fastest),
            ConnectIntent.FastestInCountry(CountryId("CH"), emptySet()),
            // Default profile is not pinned so it's last.
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()),
        )
        testMigration(userProfiles, expectedConnectIntents)
    }

    @Test
    fun identicalMigratedRecentsAreDeduplicated() = testScope.runTest {
        val userProfiles = listOf(
            createProfile(ServerWrapper.makeFastestForCountry("US"), false, VpnProtocol.Smart),
            createProfile(ServerWrapper.makeFastestForCountry("US"), false, VpnProtocol.OpenVPN),
            createProfile(ServerWrapper.makeFastestForCountry("US"), true)
        )
        val expected = listOf(
            ConnectIntent.SecureCore(CountryId("US"), CountryId.fastest), // Secure Core separate.
            ConnectIntent.FastestInCountry(CountryId("US"), emptySet()), // Single US.
            ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()), // Default.
        )
        testMigration(userProfiles, expected)
    }

    @Test
    fun profilesForServersThatDontExistAreSkipped() = testScope.runTest {
        testMigration(
            userProfiles = listOf(
                createProfile(ServerWrapper.makeWithServer(createServer("nonexistent")), false)
            ),
            expectedIntents = listOf(
                ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()), // Default
            )
        )
    }

    @Test
    fun profilesForSecureCoreServersThatDontExistAreSkipped() = testScope.runTest {
        val secureCoreServer = createServer("nonexistent", isSecureCore = true)
        testMigration(
            userProfiles = listOf(
                createProfile(ServerWrapper.makeWithServer(secureCoreServer), true)
            ),
            expectedIntents = listOf(
                ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()), // Default
            )
        )
    }

    @Test
    fun customQuickConnectProfileIsFirst() = testScope.runTest {
        val userProfiles = listOf(
            createProfile(ServerWrapper.makeFastestForCountry("CH"), false),
            createProfile(ServerWrapper.makeFastestForCountry("UK"), false),
            createProfile(ServerWrapper.makeFastestForCountry("PL"), false),
        )
        settingsFlow.update { it.copy(defaultProfileId = userProfiles.find { it.country == "UK" }?.id) }
        val expectedIntents = listOf(
            ConnectIntent.FastestInCountry(CountryId("UK"), emptySet()),
            ConnectIntent.FastestInCountry(CountryId("PL"), emptySet()),
            ConnectIntent.FastestInCountry(CountryId("CH"), emptySet()),
        )
        testMigration(userProfiles, expectedIntents)

        val mostRecent = recentsDao.getMostRecentConnection(userId).first()
        assertEquals(ConnectIntent.FastestInCountry(CountryId("UK"), emptySet()), mostRecent?.connectIntent)
    }

    @Test
    fun randomProfilesAreTreatedAsFastest() = testScope.runTest {
        val userProfiles = listOf(
            createProfile(ServerWrapper.makeRandomForCountry("CH"), false),
            createProfile(ServerWrapper.makeRandomForCountry("CH"), true),
            createProfile(ServerWrapper.makePreBakedRandom(), true),
        )
        // Make the random Secure Core profile the default, otherwise it won't migrate.
        val profileId = userProfiles.find { it.isPreBakedProfile && it.isSecureCore == true }!!.id
        settingsFlow.update { it.copy(defaultProfileId = profileId) }
        val expectedIntents = listOf(
            ConnectIntent.SecureCore(CountryId("CH"), entryCountry = CountryId.fastest),
            ConnectIntent.FastestInCountry(CountryId("CH"), emptySet()),
            ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest), // Default profile is unpinned, so last.
        )
        testMigration(userProfiles, expectedIntents)
    }

    @Test
    fun serverGatewayProfilesAreConvertedToGatewayIntents() = testScope.runTest {
        testMigration(
            userProfiles = listOf(
                createProfile(ServerWrapper.makeWithServer(servers[3]), false),
            ),
            expectedIntents = listOf(
                ConnectIntent.Gateway("gateway", servers[3].serverId),
                ConnectIntent.FastestInCountry(CountryId.fastest, emptySet()), // Default
            )
        )
    }

    private suspend fun testMigration(userProfiles: List<Profile>, expectedIntents: List<ConnectIntent>) {
        userProfiles.forEach { profileManager.addToProfileList(it) }

        migrateProfiles()

        val migratedConnectIntents = recentsDao.getRecentsList(userId).first().map { it.connectIntent }
        assertEquals(expectedIntents, migratedConnectIntents)
    }

    private fun createProfile(
        serverWrapper: ServerWrapper,
        isSecureCore: Boolean?,
        protocol: VpnProtocol? = null
    ) = Profile(
        name = "",
        color = null,
        colorId = null,
        wrapper =  serverWrapper,
        isSecureCore = isSecureCore,
        protocol = protocol?.toString(),
        transmissionProtocol = null
    )
}
