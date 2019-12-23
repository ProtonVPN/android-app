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

public class SrpRequestBody {

    @JsonProperty("SRPSession") private String srpSession;

    @JsonProperty("ClientEphemeral") private String clientEphemeral;

    @JsonProperty("ClientProof") private String clientProof;

    @JsonProperty("TwoFactorCode") private String twoFactorCode;

    public SrpRequestBody(final String srpSession, final String clientEphemeral, final String clientProof,
                          final String twoFactorCode) {
        this.srpSession = srpSession;
        this.clientEphemeral = clientEphemeral;
        this.clientProof = clientProof;
        this.twoFactorCode = twoFactorCode;
    }
}