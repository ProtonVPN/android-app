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
package com.protonvpn.android.components;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.lang.reflect.Type;

public class LocalDateSerializer implements JsonDeserializer<LocalDate>, JsonSerializer<LocalDate> {

    private static final DateTimeFormatter DATE_FORMAT = ISODateTimeFormat.date();

    @Override
    public LocalDate deserialize(final JsonElement je, final Type type,
                                 final JsonDeserializationContext jdc) throws JsonParseException {
        final String dateAsString = je.getAsString();
        if (dateAsString.length() == 0) {
            return null;
        }
        else {
            return DATE_FORMAT.parseLocalDate(dateAsString);
        }
    }

    @Override
    public JsonElement serialize(final LocalDate src, final Type typeOfSrc,
                                 final JsonSerializationContext context) {
        String retVal;
        if (src == null) {
            retVal = "";
        }
        else {
            retVal = DATE_FORMAT.print(src);
        }
        return new JsonPrimitive(retVal);
    }
}