/*
 * Copyright (c) 2025. Proton AG
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

import android.os.Looper
import androidx.test.annotation.UiThreadTest
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnectionSync
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
@UiThreadTest
class EffectiveCurrentUserSettingsCachedTests {

    // Important: don't use ProtonHiltAndroidRule because it initializes dependencies early, before the test, which
    // breaks it, i.e. the test will pass even if there is deadlock potential.
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var effectiveCurrentUserSettingsCached: EffectiveCurrentUserSettingsCached

    @Inject
    lateinit var settingsForConnectionSync: SettingsForConnectionSync

    @Test(timeout = 5_000)
    fun effectiveCurrentUserSettingsCached_syncGetterDoesNotDeadlock() {
        // Inject on main thread and try to read the cached value as quickly as possible. This should cause
        // a blocking read from the underlying SyncStateFlow and thus test for deadlock.
        assertEquals(Looper.getMainLooper().thread, Thread.currentThread())
        hiltRule.inject()
        effectiveCurrentUserSettingsCached.value
    }

    @Test(timeout = 5_000)
    fun settingsForConnectionSync_syncGetterDoesNotDeadlock() {
        // Inject on main thread and try to read the cached value as quickly as possible. This should cause
        // a blocking read from the underlying SyncStateFlow and thus test for deadlock.
        assertEquals(Looper.getMainLooper().thread, Thread.currentThread())
        hiltRule.inject()
        settingsForConnectionSync.getForSync(ConnectIntent.Fastest)
    }
}
