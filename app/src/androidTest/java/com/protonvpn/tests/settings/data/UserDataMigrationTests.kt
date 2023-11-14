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

package com.protonvpn.tests.settings.data

import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.userstorage.DefaultLocalDataStoreFactory
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val TEST_MTU_SIZE = 1000

@OptIn(ExperimentalCoroutinesApi::class)
class UserDataMigrationTests {

    private lateinit var testScope: TestScope

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    @Before
    fun setup() {
        Storage.setPreferences(MockSharedPreference())

        testScope = TestScope(UnconfinedTestDispatcher())
        val currentUser = CurrentUser(testScope.backgroundScope, TestCurrentUserProvider(TestUser.plusUser.vpnUser))
        val storeProvider = LocalUserSettingsStoreProvider(
            DefaultLocalDataStoreFactory(InstrumentationRegistry.getInstrumentation().targetContext)
        )
        userSettingsManager = CurrentUserLocalSettingsManager(storeProvider)
    }

    @Test
    fun settingFromUserDataIsMigrated() = testScope.runTest {
        val userData = UserData(TEST_MTU_SIZE)
        Storage.save(userData, UserData::class.java)

        val settings = userSettingsManager.rawCurrentUserSettingsFlow.first()

        assertEquals(LocalUserSettings.Default.copy(mtuSize = TEST_MTU_SIZE), settings)

        val userDataAfterMigration = Storage.load(UserData::class.java)
        Assert.assertNull(userDataAfterMigration)
    }
}
