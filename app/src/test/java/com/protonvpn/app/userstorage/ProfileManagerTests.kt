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

package com.protonvpn.app.userstorage

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.content.edit
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileManagerTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var settingsManager: CurrentUserLocalSettingsManager
    private lateinit var effectiveUserSettings: MutableStateFlow<LocalUserSettings>
    private lateinit var storagePrefs: SharedPreferences
    private lateinit var testScope: TestScope

    private lateinit var profileManager: ProfileManager
    private lateinit var serverManager: ServerManager

    @Before
    fun setup() {
        storagePrefs = MockSharedPreference()
        Storage.setPreferences(storagePrefs)
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        effectiveUserSettings = MutableStateFlow(LocalUserSettings.Default)

        settingsManager =
            CurrentUserLocalSettingsManager(LocalUserSettingsStoreProvider(InMemoryDataStoreFactory()))

        profileManager = createProfileManager(testScope)
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            emptyList()
        )
    }

    @Test
    fun `deleting default profile clears UserData defaultProfileId`() = testScope.runTest {
        val profile = Profile(
            ServerWrapper.makeFastestForCountry("pl"),
            null
        )

        settingsManager.updateDefaultProfile(profile.id)
        profileManager.deleteProfile(profile)

        assertNull(settingsManager.rawCurrentUserSettingsFlow.first().defaultProfileId)
    }

    @Test
    fun `when defaultProfileId is invalid then defaultConnection falls back to saved profiles`() = testScope.runTest {
        effectiveUserSettings.update { settings -> settings.copy(defaultProfileId = UUID.randomUUID()) }
        val profile = profileManager.getDefaultOrFastestSync()
        assertEquals(profileManager.getSavedProfiles().first(), profile)
    }

    @Test
    fun `restore profiles from persistent storage`() = testScope.runTest {
        val jsonSavedProfiles = """
            {"profileList":[{"id":"82c935d8-2968-4cc5-8ea7-8d73270efe57","isGuestHoleProfile":false,"name":"fastest","wrapper":{"country":"","secureCoreCountry":false,"serverId":"","type":"FASTEST"}},{"id":"45509eff-bafb-46c1-8b16-ff605d94c5f6","isGuestHoleProfile":false,"name":"random","wrapper":{"country":"","secureCoreCountry":false,"serverId":"","type":"RANDOM"}},{"id":"3b982087-47e5-4560-b99b-bb2433b8b770","isGuestHoleProfile":false,"name":"Argentina","wrapper":{"country":"AR","secureCoreCountry":false,"serverId":"","type":"FASTEST_IN_COUNTRY"}}]}
        """.trimIndent()
        storagePrefs.edit {
            putString("com.protonvpn.android.models.profiles.SavedProfilesV3", jsonSavedProfiles)
        }
        profileManager = createProfileManager(testScope, ProfileManager.loadProfiles())
        val profiles = profileManager.getSavedProfiles()
        val customProfile = profiles.find { !it.isPreBakedProfile }
        assertEquals("AR", customProfile?.country)
        assertEquals(ServerWrapper.ProfileType.FASTEST_IN_COUNTRY, customProfile?.wrapper?.type)
    }

    private fun createProfileManager(
        testScope: TestScope,
        profiles: SavedProfilesV3 = SavedProfilesV3.defaultProfiles(),
    ): ProfileManager {
        val currentUserSettings = EffectiveCurrentUserSettingsCached(effectiveUserSettings)
        return ProfileManager(
            profiles,
            testScope.backgroundScope,
            currentUserSettings,
            settingsManager
        )
    }
}
