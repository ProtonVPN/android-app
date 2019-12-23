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

public class LoginResponse {

    private String accessToken;
    private int expiresIn;
    private String tokenType;
    private String scope;
    private String uid;
    private String refreshToken;
    private String eventID;
    private int passwordMode;

    public LoginResponse(@JsonProperty(value = "AccessToken", required = true) String accessToken,
                         @JsonProperty(value = "ExpiresIn", required = true) int expiresIn,
                         @JsonProperty(value = "TokenType", required = true) String tokenType,
                         @JsonProperty(value = "Scope", required = true) String scope,
                         @JsonProperty(value = "Uid", required = true) String uid,
                         @JsonProperty(value = "RefreshToken", required = true) String refreshToken) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
        this.scope = scope;
        this.uid = uid;
        this.refreshToken = refreshToken;
        this.eventID = eventID;
        this.passwordMode = passwordMode;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getScope() {
        return scope;
    }

    public String getUid() {
        return uid;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getEventID() {
        return eventID;
    }

    public int getPasswordMode() {
        return passwordMode;
    }
}
