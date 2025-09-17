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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfile
import com.protonvpn.android.profiles.usecases.DeleteProfileFromUi
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.telemetry.ProfilesTelemetry
import com.protonvpn.android.telemetry.Telemetry
import com.protonvpn.android.telemetry.TelemetryEventData
import com.protonvpn.android.telemetry.TelemetryFlowHelper
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.TestTelemetryReporter
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.proton.core.domain.entity.UserId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteProfileFromUiTests {

    private val profile = Profile(
        info = ProfileInfo(1, "profile 1", ProfileColor.Color2, ProfileIcon.Icon5, 100L, true),
        connectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet(), profileId = 1),
        userId = UserId("user1"),
        autoOpen = ProfileAutoOpen.None
    )

    @MockK
    private lateinit var mockProfilesDao: ProfilesDao

    private lateinit var profiles: MutableMap<Long, Profile>
    private lateinit var testScope: TestScope
    private lateinit var testTelemetry: TestTelemetryReporter

    private val commonDimensions = FakeCommonDimensions(mapOf("user_tier" to "paid"))

    private lateinit var deleteUsecase: DeleteProfileFromUi

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testScope = TestScope()
        testTelemetry = TestTelemetryReporter()

        profiles = HashMap()
        coEvery { mockProfilesDao.getProfileById(any()) } answers { profiles[firstArg()] }
        coEvery { mockProfilesDao.upsert(any()) } answers {
            val profileEntity: ProfileEntity = firstArg()
            val id = profileEntity.connectIntentData.profileId!!
            profiles[id] = profileEntity.toProfile()
            id
        }
        coEvery { mockProfilesDao.remove(any()) } answers { profiles.remove(firstArg()) }
        coEvery { mockProfilesDao.getProfileCount(any()) } answers { profiles.size }

        val currentUser = CurrentUser(TestCurrentUserProvider(TestUser.plusUser.vpnUser))

        val profilesTelemetry = ProfilesTelemetry(
            commonDimensions = commonDimensions,
            telemetry = TelemetryFlowHelper(testScope.backgroundScope, testTelemetry)
        )

        deleteUsecase = DeleteProfileFromUi(
            mainScope = testScope.backgroundScope,
            profilesDao = mockProfilesDao,
            telemetry = profilesTelemetry,
            currentUser = currentUser,
        )
    }

    @Test
    fun `deleted profile is removed from dao`() = testScope.runTest {
        val profileId = profile.info.id
        profiles[profileId] = profile
        deleteUsecase(profileId, 5000L)
        runCurrent()
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `deleted profile is restored with undo`() = testScope.runTest {
        val profileId = profile.info.id
        profiles[profileId] = profile
        val undo = deleteUsecase(profileId, 5000L)
        runCurrent()
        assertTrue(profiles.isEmpty())
        undo()
        runCurrent()
        assertEquals(profile, profiles[profileId])
    }

    @Test
    fun `deleting a profile emits a telemetry event`() = testScope.runTest {
        val profileId = profile.info.id
        profiles[profileId] = profile
        deleteUsecase(profileId, 5000L)
        advanceTimeBy(6000)
        runCurrent()
        assertEquals(1, testTelemetry.collectedEvents.size)
        val collectedEvent = testTelemetry.collectedEvents.first()
        assertEquals("vpn.profiles", collectedEvent.measurementGroup)
        assertEquals("profile_deleted", collectedEvent.eventName)
    }

    @Test
    fun `deleting and undoing does not emit a telemetry event`() = testScope.runTest {
        val profileId = profile.info.id
        profiles[profileId] = profile
        val undo = deleteUsecase(profileId, 5000L)
        advanceTimeBy(3000)
        runCurrent()
        undo()
        advanceTimeBy(3000)
        runCurrent()
        assertTrue(testTelemetry.collectedEvents.isEmpty())
    }
}
