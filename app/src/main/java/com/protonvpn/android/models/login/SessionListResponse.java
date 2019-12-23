/*
 * Copyright (c) 2018 Proton Technologies AG
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

import java.util.List;

public class SessionListResponse {

    private int code;
    private List<Session> sessionList;

    public SessionListResponse(@JsonProperty(value = "Code", required = true) int code,
                               @JsonProperty(value = "Sessions", required = true) List<Session> sessionList) {
        this.code = code;
        this.sessionList = sessionList;
    }

    public int getCode() {
        return code;
    }

    public List<Session> getSessionList() {
        return sessionList;
    }
}
