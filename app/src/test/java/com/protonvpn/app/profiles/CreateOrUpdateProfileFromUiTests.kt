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

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.login.toVpnUserEntity
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfileEntity
import com.protonvpn.android.profiles.ui.NameScreenState
import com.protonvpn.android.profiles.ui.SettingsScreenState
import com.protonvpn.android.profiles.ui.TypeAndLocationScreenState
import com.protonvpn.android.profiles.usecases.CreateOrUpdateProfileFromUi
import com.protonvpn.android.profiles.usecases.PrivateBrowsingAvailability
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.telemetry.ProfilesTelemetry
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.AccountTestHelper
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestAccount1
import com.protonvpn.testsHelper.AccountTestHelper.Companion.TestSession1
import io.mockk.MockKAnnotations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CreateOrUpdateProfileFromUiTests {

    private lateinit var profilesDao: ProfilesDao
    private lateinit var testScope: TestScope
    private lateinit var testTelemetry: TestTelemetryReporter

    private lateinit var createOrUpdate: CreateOrUpdateProfileFromUi

    private val vpnUser =
        TestUser.plusUser.vpnInfoResponse.toVpnUserEntity(TestAccount1.userId, TestSession1.sessionId, 0L, null)
    private val nameScreenState =
        NameScreenState(name = "Profile 1", color = ProfileColor.Color1, icon = ProfileIcon.Icon1)
    private val typeAndLocationScreenState = TypeAndLocationScreenState.Standard(
        availableTypes = emptyList(),
        country = TypeAndLocationScreenState.CountryItem(CountryId.sweden, true),
        cityOrState = null,
        server = null,
        selectableCountries = emptyList(),
        selectableCitiesOrStates = emptyList(),
        selectableServers = emptyList()
    )
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
        showPrivateBrowsing = true
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
            settingsOverrides = settingsScreenState.toSettingsOverrides()
        ),
        autoOpen = ProfileAutoOpen.None
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)
        testTelemetry = TestTelemetryReporter()

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
            TelemetryFlowHelper(testScope.backgroundScope, testTelemetry)
        )

        createOrUpdate = CreateOrUpdateProfileFromUi(
            testScope.backgroundScope,
            profilesDao,
            currentUser,
            telemetry = profilesTelemetry,
            wallClock = { testScope.currentTime },
            getPrivateBrowsingAvailability = { PrivateBrowsingAvailability.AvailableWithDefault }
        )
    }

    @Test
    fun `newly inserted profile is marked user-created`() = testScope.runTest {
        createOrUpdate(null, null, nameScreenState, typeAndLocationScreenState, settingsScreenState)
        runCurrent()
        val profiles = profilesDao.getProfiles(vpnUser.userId).first()
        assertEquals(1, profiles.size)
        assertEquals(true, profiles.first().info.isUserCreated)
    }

    @Test
    fun `update of initial profile with no changes doesn't change user-created state`() = testScope.runTest {
        val profileId = testProfile.info.id
        val createdAt = testProfile.info.createdAt
        profilesDao.upsert(testProfile.toProfileEntity())
        createOrUpdate(profileId, createdAt, nameScreenState, typeAndLocationScreenState, settingsScreenState)
        runCurrent()
        assertEquals(false, profilesDao.getProfileById(profileId)?.info?.isUserCreated)
    }

    @Test
    fun `update of initial profile with changes makes it user-created`() = testScope.runTest {
        val profileId = testProfile.info.id
        val createdAt = testProfile.info.createdAt
        val profile = testProfile.copy(
            connectIntent = ConnectIntent.FastestInCountry(
                CountryId.fastest,
                emptySet(),
                profileId = profileId,
                settingsOverrides = settingsScreenState.toSettingsOverrides()
            )
        )
        profilesDao.upsert(profile.toProfileEntity())

        createOrUpdate(profileId, createdAt, nameScreenState, typeAndLocationScreenState, settingsScreenState)
        runCurrent()
        assertEquals(true, profilesDao.getProfileById(profileId)?.info?.isUserCreated)
    }

    @Test
    fun `creating a duplicate inserts a new profile item`() = testScope.runTest {
        profilesDao.upsert(testProfile.toProfileEntity())

        val newNameScreenState = nameScreenState.copy(name = "Profile duplicate")
        createOrUpdate(
            testProfile.info.id,
            testProfile.info.createdAt,
            newNameScreenState,
            typeAndLocationScreenState,
            settingsScreenState,
            createDuplicate = true,
        )
        runCurrent()
        val profiles = profilesDao.getProfiles(vpnUser.userId).first()
        assertEquals(2, profiles.size)
        assertEquals(testProfile, profiles.find { it.info.id == testProfile.info.id} )
        val newProfile = profiles.find { it.info.id != testProfile.info.id }
        assertEquals("Profile duplicate", newProfile?.info?.name)

        assertEquals(listOf("profile_duplicated"), testTelemetry.collectedEvents.map { it.eventName })
    }

    @Test
    fun `adding new profile generates telemetry event profile_created`() = testScope.runTest {
        val otherProfileEntity = with(testProfile.toProfileEntity()) {
            copy(connectIntentData = connectIntentData.copy(profileId = 2L))
        }
        profilesDao.upsert(otherProfileEntity)

        with(testProfile) {
            createOrUpdate(info.id, info.createdAt, nameScreenState, typeAndLocationScreenState, settingsScreenState)
        }
        runCurrent()

        with(testTelemetry) {
            assertEquals(1, collectedEvents.size)
            val event = collectedEvents.first()
            assertEquals("profile_created", event.eventName)
            // Count includes the newly added profile.
            assertEquals("2", event.dimensions["profile_count"])
        }
    }

    @Test
    fun `updating a profile generates telemetry event profile_updated`() = testScope.runTest {
        profilesDao.upsert(testProfile.toProfileEntity())

        val newNameScreenState = nameScreenState.copy(name = "Profile with changes")
        createOrUpdate(
            testProfile.info.id,
            testProfile.info.createdAt,
            newNameScreenState,
            typeAndLocationScreenState,
            settingsScreenState
        )
        runCurrent()
        assertEquals(listOf("profile_updated"), testTelemetry.collectedEvents.map { it.eventName })
    }
}
