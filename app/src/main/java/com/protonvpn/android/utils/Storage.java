/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.utils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Objects;

import kotlin.jvm.functions.Function0;
import me.proton.core.network.domain.client.ClientId;

public final class Storage {

    private final static Gson GSON =
        new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(ClientId.class, new ClientIdGsonSerializer())
            .create();

    private static SharedPreferences preferences;

    private Storage() {
    }

    public static void setPreferences(SharedPreferences preferences) {
        Storage.preferences = preferences;
    }

    public static void saveBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(String key, Boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public static void saveInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public static int getInt(String key) {
        try {
            return preferences.getInt(key, 0);
        }
        catch (ClassCastException e) {
            DebugUtils.INSTANCE.fail("Int format exception for key: " + key + ": " + e.getMessage());
            return 0;
        }
    }

    public static void saveString(String key, String value) {
        if (!Objects.equals(getString(key, null), value)) {
            preferences.edit().putString(key, value).apply();
        }
    }

    public static String getString(String key, String defValue) {
        return preferences.getString(key, defValue);
    }

    public static <T> void save(@Nullable T data, Class<T> as) {
        if (data != null) {
            preferences.edit().putString(as.getName(), GSON.toJson(data)).apply();
        } else {
            preferences.edit().remove(as.getName()).apply();
        }
    }

    @Nullable
    public static <T> T load(Class<T> objClass) {
        return load(objClass, objClass);
    }

    @Nullable
    public static <K,V extends K> V load(Class<K> keyClass, Class<V> objClass) {
        String key = keyClass.getName();
        if (!preferences.contains(key)) {
            return null;
        }

        V fromJson;
        try {
            String json = preferences.getString(key, null);
            fromJson = GSON.fromJson(json, objClass);
        }
        catch (Exception | NoClassDefFoundError e) {
            DebugUtils.INSTANCE.fail("GSON load exception: " + e.getMessage());
            return null;
        }
        return fromJson;
    }

    public static void delete(String key) {
        preferences.edit().remove(key).apply();
    }

    public static <T> void delete(Class<T> objClass) {
        delete(objClass.getName());
    }

    @Deprecated // use load() with lambda defaultValue
    public static <T> T load(Class<T> objClass, T defaultValue) {
        String key = objClass.getName();
        if (key.equals("com.protonvpn.android.models.config.UserData") && !preferences.contains(key)) {
            key = "com.protonvpn.android.models.config.UserPreferences";
        }
        String json = preferences.getString(key, null);
        if (!preferences.contains(key) || json == null) {
            return defaultValue;
        }

        return GSON.fromJson(json, objClass);
    }

    public static <T> T load(Class<T> objClass, Function0<T> defaultValue) {
        T value = load(objClass);
        if (value == null) {
            value = defaultValue.invoke();
            save(value, objClass);
        }
        return value;
    }

    @SuppressLint("ApplySharedPref")
    @VisibleForTesting
    public static void clearAllPreferencesSync() {
        preferences.edit().clear().commit();
    }

}
