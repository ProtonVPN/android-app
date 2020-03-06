/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.app

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.Storage
import io.mockk.mockkClass
import junit.framework.Assert
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserPreferencesTests {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @Before
    fun before() {
        Storage.setPreferences(mockkClass(SharedPreferences::class))
    }

    @Test
    fun simpleOpenTracking() {
        val testPrefs =
                UserData()
        testPrefs.trackAppOpening(DateTime("2018-08-16T07:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-16T09:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-16T10:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-17T09:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-18T10:22:05Z"))
        Assert.assertEquals(3, testPrefs.timesAppUsed)
    }

    @Test
    fun consecutiveOpenTracking() {
        val testPrefs =
                UserData()
        testPrefs.trackAppOpening(DateTime("2018-08-16T07:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-17T10:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-18T10:22:05Z"))
        testPrefs.trackAppOpening(DateTime("2018-08-20T10:22:05Z"))
        Assert.assertEquals(1, testPrefs.timesAppUsed)
    }
}
