/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.test.shared

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.Nullable
import com.protonvpn.android.utils.SharedPreferencesProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockSharedPreferencesProvider @Inject constructor() : SharedPreferencesProvider {

    private val allPrefs = ConcurrentHashMap<String, SharedPreferences>()

    override fun getPrefs(name: String): SharedPreferences =
        allPrefs.getOrPut(name) { MockSharedPreference() }
}

class MockSharedPreference : SharedPreferences {
    private val preferenceMap: HashMap<String?, Any?> = HashMap()
    private val preferenceEditor: MockSharedPreferenceEditor

    override fun getAll(): Map<String?, *> = preferenceMap

    @Nullable
    override fun getString(s: String, @Nullable s1: String?) = preferenceMap.getOrDefault(s, s1) as? String

    @Nullable
    override fun getStringSet(s: String, @Nullable set: Set<String>?) = preferenceMap.getOrDefault(s, set) as? Set<String>

    override fun getInt(s: String, i: Int) = preferenceMap.getOrDefault(s, i) as Int

    override fun getLong(s: String, l: Long) = preferenceMap.getOrDefault(s, l) as Long

    override fun getFloat(s: String, v: Float) = preferenceMap.getOrDefault(s, v) as Float

    override fun getBoolean(s: String, b: Boolean) = preferenceMap.getOrDefault(s, b) as Boolean

    override fun contains(s: String) = preferenceMap.containsKey(s)

    override fun edit() = preferenceEditor

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}

    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener) {}

    class MockSharedPreferenceEditor(private val preferenceMap: HashMap<String?, Any?>) :
        SharedPreferences.Editor {

        override fun putString(s: String, @Nullable s1: String?): SharedPreferences.Editor {
            preferenceMap[s] = s1
            return this
        }

        override fun putStringSet(s: String, @Nullable set: Set<String>?): SharedPreferences.Editor {
            preferenceMap[s] = set
            return this
        }

        override fun putInt(s: String, i: Int): SharedPreferences.Editor {
            preferenceMap[s] = i
            return this
        }

        override fun putLong(s: String, l: Long): SharedPreferences.Editor {
            preferenceMap[s] = l
            return this
        }

        override fun putFloat(s: String, v: Float): SharedPreferences.Editor {
            preferenceMap[s] = v
            return this
        }

        override fun putBoolean(s: String, b: Boolean): SharedPreferences.Editor {
            preferenceMap[s] = b
            return this
        }

        override fun remove(s: String): SharedPreferences.Editor {
            preferenceMap.remove(s)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            preferenceMap.clear()
            return this
        }

        override fun commit() = true

        override fun apply() { // Nothing to do, everything is saved in memory.
        }

    }

    init {
        preferenceEditor = MockSharedPreferenceEditor(preferenceMap)
    }
}
