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
package com.protonvpn.app;

import android.content.SharedPreferences;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.utils.Storage;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.Assert.assertEquals;

public class UserPreferencesTests {

    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    @Before
    public void before() {
        Storage.setPreferences(Mockito.mock(SharedPreferences.class));
    }

    @Test
    public void simpleOpenTracking() {
        UserData testPrefs = new UserData();
        testPrefs.trackAppOpening(new DateTime("2018-08-16T07:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-16T09:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-16T10:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-17T09:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-18T10:22:05Z"));
        assertEquals(3, testPrefs.getTimesAppUsed());
    }

    @Test
    public void consecutiveOpenTracking() {
        UserData testPrefs = new UserData();
        testPrefs.trackAppOpening(new DateTime("2018-08-16T07:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-17T10:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-18T10:22:05Z"));
        testPrefs.trackAppOpening(new DateTime("2018-08-20T10:22:05Z"));
        assertEquals(1, testPrefs.getTimesAppUsed());
    }
}
