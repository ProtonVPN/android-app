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

package com.protonvpn.tests.util

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.BuildConfig
import com.protonvpn.android.utils.ProtonPreferences
import com.protonvpn.android.utils.migrateProtonPreferences
import me.proton.core.util.android.sharedpreferences.isEmpty
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue

class ProtonPreferencesMigrationTests {

    private val srcPrefsName = "Proton-Preferences"
    private val dstPrefsName = "Preferences"

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun cleanInstallationDoesntMigrate() {
        migrateProtonPreferences(context, srcPrefsName, dstPrefsName)
        val prefs = context.getSharedPreferences(dstPrefsName, Context.MODE_PRIVATE)
        assertTrue(prefs.isEmpty())
    }

    @Test
    fun dataIsMigrated() {
        val srcPrefs = ProtonPreferences(context, BuildConfig.PREF_SALT, BuildConfig.PREF_KEY, srcPrefsName)
        val editor = srcPrefs.edit()
        editor.putInt("VERSION_CODE", 123)
        editor.putString("com.protonvpn.android.models.vpn.ConnectionParams", "{}")
        editor.putBoolean("sentry_is_enabled", true)
        editor.commit()

        migrateProtonPreferences(context, srcPrefsName, dstPrefsName)
        val prefs = context.getSharedPreferences(dstPrefsName, Context.MODE_PRIVATE)
        assertEquals(123, prefs.getInt("VERSION_CODE", 0))
        assertEquals("{}", prefs.getString("com.protonvpn.android.models.vpn.ConnectionParams", ""))
        assertEquals(true, prefs.getBoolean("sentry_is_enabled", false))
    }

    @Test
    fun sourcePreferencesAreDeleted() {
        run {
            val srcPrefs = ProtonPreferences(context, BuildConfig.PREF_SALT, BuildConfig.PREF_KEY, srcPrefsName)
            srcPrefs.edit().putString("LAST_USER", "user").commit()
        }

        migrateProtonPreferences(context, srcPrefsName, dstPrefsName)
        val prefs = context.getSharedPreferences(dstPrefsName, Context.MODE_PRIVATE)
        assertEquals("user", prefs.getString("LAST_USER", ""))

        prefs.edit().clear().commit()

        migrateProtonPreferences(context, srcPrefsName, dstPrefsName)
        assertTrue(prefs.all.isEmpty())
    }
}
