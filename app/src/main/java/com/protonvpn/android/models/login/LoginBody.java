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
package com.protonvpn.android.models.login;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protonvpn.android.components.GsonModel;

public class LoginBody extends SrpRequestBody implements GsonModel {

    @JsonProperty("Username") private String username;

    public LoginBody(final String username, final String srpSession, final String clientEphemeral,
                     final String clientProof, final String twoFactorCode) {
        super(srpSession, clientEphemeral, clientProof, twoFactorCode);
        this.username = username;
    }

    @Override
    public JsonObject toJson() {
        String jsonString = new GsonBuilder().create().toJson(this, LoginBody.class);
        JsonParser parser = new JsonParser();
        return parser.parse(jsonString).getAsJsonObject();
    }
}