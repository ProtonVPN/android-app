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

public class LoginInfoResponse {

    private final int code;
    private final String modulus;
    private final String serverEphemeral;
    private final Integer version;
    private final String salt;
    private final String srpSession;

    public LoginInfoResponse(@JsonProperty(value = "Code", required = true) int code,
                             @JsonProperty(value = "Modulus", required = true) String modulus,
                             @JsonProperty(value = "ServerEphemeral", required = true) String serverEphemeral,
                             @JsonProperty(value = "Version", required = true) int version,
                             @JsonProperty(value = "Salt", required = true) String salt,
                             @JsonProperty(value = "SRPSession", required = true) String srpSession) {
        this.code = code;
        this.modulus = modulus;
        this.serverEphemeral = serverEphemeral;
        this.version = version;
        this.salt = salt;
        this.srpSession = srpSession;
    }

    public int getCode() {
        return code;
    }

    public String getModulus() {
        return modulus;
    }

    public String getServerEphemeral() {
        return serverEphemeral;
    }

    public Long getVersion() {
        return version.longValue();
    }

    public String getSalt() {
        return salt;
    }

    public String getSrpSession() {
        return srpSession;
    }
}