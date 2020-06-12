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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

public final class Json {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();

        SimpleModule dateModule = new SimpleModule("date", new Version(1, 0, 0, null, null, null));

        MAPPER.registerModule(dateModule);
        MAPPER.registerModule(new KotlinModule());
        MAPPER.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        MAPPER.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
    }

    private Json() {
    }

    public static JSONObject buildHashMapObject(String... values) {
        return new JSONObject(buildHashMapForJson(values));
    }

    private static HashMap<String, String> buildHashMapForJson(String... data) {
        HashMap<String, String> result = new HashMap<>();

        if (data.length % 2 != 0) {
            throw new IllegalArgumentException("Odd number of arguments");
        }

        String key = null;
        Integer step = -1;

        for (String value : data) {
            step++;
            switch (step % 2) {
                case 0:
                    if (value == null) {
                        throw new IllegalArgumentException("Null key value");
                    }
                    key = value;
                    continue;
                case 1:
                    result.put(key, value);
                    break;
            }
        }

        return result;
    }

    public static HashMap<String, List<String>> buildHashMap(String... data) {
        HashMap<String, List<String>> result = new HashMap<>();

        if (data.length % 2 != 0) {
            throw new IllegalArgumentException("Odd number of arguments");
        }

        String key = null;
        Integer step = -1;

        for (String value : data) {
            step++;
            switch (step % 2) {
                case 0:
                    if (value == null) {
                        throw new IllegalArgumentException("Null key value");
                    }
                    key = value;
                    continue;
                case 1:
                    result.put(key, Collections.singletonList(value));
                    break;
            }
        }

        return result;
    }

    public static String toString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            return null;
        }
    }

    @NonNull
    public static <T> T unwrap(Map<String, T> embedded, String key) {
        T item = embedded.get(key);
        if (item == null) {
            throw new RuntimeException("Embedded does not contain required entry " + key);
        }
        return item;
    }
}