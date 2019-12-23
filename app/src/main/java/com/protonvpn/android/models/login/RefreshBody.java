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
import com.protonvpn.android.utils.Storage;

import java.util.UUID;

public class RefreshBody implements GsonModel {

    @JsonProperty("ResponseType") private String responseType = "token";

    @JsonProperty("RefreshToken") private String refreshToken;

    @JsonProperty("ClientID") private String clientId = "VPN";

    @JsonProperty("GrantType") private String grantType = "refresh_token";

    @JsonProperty("RedirectURI") private String redirectUri = "http://protonmail.ch";

    @JsonProperty("State") private String state;

    @JsonProperty("UID") private String uid;

    public RefreshBody() {
        LoginResponse response = Storage.load(LoginResponse.class);
        if (response != null) {
            this.refreshToken = response.getRefreshToken();
            uid = response.getUid();
            this.state = UUID.randomUUID().toString();
        }
    }

    @Override
    public JsonObject toJson() {
        String jsonString = new GsonBuilder().create().toJson(this, RefreshBody.class);
        JsonParser parser = new JsonParser();
        return parser.parse(jsonString).getAsJsonObject();
    }
}
