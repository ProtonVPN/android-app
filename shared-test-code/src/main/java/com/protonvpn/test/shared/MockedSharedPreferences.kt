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
import java.util.Collections
import java.util.WeakHashMap
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
    private val changeListeners = Collections.newSetFromMap(WeakHashMap<OnSharedPreferenceChangeListener, Boolean>())

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

    override fun edit() = MockSharedPreferenceEditor(preferenceMap, ::onPreferenceChanged)

    override fun registerOnSharedPreferenceChangeListener(
        onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener
    ) {
        changeListeners.add(onSharedPreferenceChangeListener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener
    ) {
        changeListeners.remove(onSharedPreferenceChangeListener)
    }

    private fun onPreferenceChanged(key: String?) {
        changeListeners.forEach { it.onSharedPreferenceChanged(this, key) }
    }

    class MockSharedPreferenceEditor(
        private val destinationMap: MutableMap<String?, Any?>,
        private val onPrefChanged: (key: String?) -> Unit
    ) : SharedPreferences.Editor {

        private val removals = mutableListOf<String>()
        private val editMap = HashMap<String?, Any?>()
        private var clearAll = false

        override fun putString(s: String, @Nullable s1: String?): SharedPreferences.Editor {
            editMap[s] = s1
            return this
        }

        override fun putStringSet(s: String, @Nullable set: Set<String>?): SharedPreferences.Editor {
            editMap[s] = set
            return this
        }

        override fun putInt(s: String, i: Int): SharedPreferences.Editor {
            editMap[s] = i
            return this
        }

        override fun putLong(s: String, l: Long): SharedPreferences.Editor {
            editMap[s] = l
            return this
        }

        override fun putFloat(s: String, v: Float): SharedPreferences.Editor {
            editMap[s] = v
            return this
        }

        override fun putBoolean(s: String, b: Boolean): SharedPreferences.Editor {
            editMap[s] = b
            return this
        }

        override fun remove(s: String): SharedPreferences.Editor {
            removals.add(s)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            if (clearAll) {
                onPrefChanged(null)
                destinationMap.clear()
            }
            removals.forEach { key -> destinationMap.remove(key) }
            destinationMap.putAll(editMap)
            editMap.keys.forEach(onPrefChanged)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
