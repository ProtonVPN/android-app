package com.protonvpn.app.mocks

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.Nullable

class MockSharedPreference : SharedPreferences {
    private val preferenceMap: HashMap<String?, Any?> = HashMap()
    private val preferenceEditor: MockSharedPreferenceEditor

    override fun getAll(): Map<String?, *> = preferenceMap

    @Nullable
    override fun getString(s: String, @Nullable s1: String?) = preferenceMap[s] as? String ?: s1

    @Nullable
    override fun getStringSet(s: String, @Nullable set: Set<String>?) = preferenceMap[s] as? Set<String> ?: set

    override fun getInt(s: String, i: Int) = preferenceMap[s] as Int

    override fun getLong(s: String, l: Long) = preferenceMap[s] as Long

    override fun getFloat(s: String, v: Float) = preferenceMap[s] as Float

    override fun getBoolean(s: String, b: Boolean) = preferenceMap[s] as Boolean

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
