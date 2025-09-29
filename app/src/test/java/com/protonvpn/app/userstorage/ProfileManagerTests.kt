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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.SavedProfilesV3
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
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
import com.protonvpn.test.shared.createGetSmartProtocols
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileManagerTests {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var settingsManager: CurrentUserLocalSettingsManager
    private lateinit var effectiveUserSettings: MutableStateFlow<LocalUserSettings>
    private lateinit var testScope: TestScope

    private lateinit var profileManager: ProfileManager
    private lateinit var serverManager: ServerManager

    @Before
    fun setup() {
        Storage.setPreferences(MockSharedPreference())
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)

        effectiveUserSettings = MutableStateFlow(LocalUserSettings.Default)
        val currentUserSettings = EffectiveCurrentUserSettingsCached(effectiveUserSettings)
        settingsManager =
            CurrentUserLocalSettingsManager(LocalUserSettingsStoreProvider(InMemoryDataStoreFactory()))

        val bgScope = testScope.backgroundScope
        profileManager =
            ProfileManager(SavedProfilesV3.defaultProfiles(), bgScope, currentUserSettings, settingsManager)
        serverManager = createInMemoryServerManager(
            testScope,
            TestDispatcherProvider(testDispatcher),
            SupportsProtocol(createGetSmartProtocols()),
            emptyList()
        )
    }

    @Test
    fun `deleting default profile clears UserData defaultProfileId`() = testScope.runTest {
        val profile = Profile(
            "test",
            null,
            ServerWrapper.makeFastestForCountry("pl"),
            ProfileColor.OLIVE.id,
            false,
            "WireGuard",
            null
        )

        settingsManager.updateDefaultProfile(profile.id)
        profileManager.deleteProfile(profile)

        Assert.assertNull(settingsManager.rawCurrentUserSettingsFlow.first().defaultProfileId)
    }

    @Test
    fun `when defaultProfileId is invalid then defaultConnection falls back to saved profiles`() = testScope.runTest {
        effectiveUserSettings.update { settings -> settings.copy(defaultProfileId = UUID.randomUUID()) }
        val profile = profileManager.getDefaultOrFastest()
        Assert.assertEquals(profileManager.getSavedProfiles().first(), profile)
    }
}
