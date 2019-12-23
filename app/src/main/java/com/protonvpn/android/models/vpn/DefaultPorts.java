/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.models.vpn;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

public class DefaultPorts {

    private final List<Integer> udpPorts;
    private final List<Integer> tcpPorts;

    public DefaultPorts(@JsonProperty(value = "UDP", required = true) @NonNull List<Integer> udpPorts,
                        @JsonProperty(value = "TCP", required = true) @NonNull List<Integer> tcpPorts) {
        this.udpPorts = udpPorts;
        this.tcpPorts = tcpPorts;
    }

    @NonNull
    public List<Integer> getUdpPorts() {
        return udpPorts.isEmpty() ? new ArrayList<>(443) : udpPorts;
    }

    @NonNull
    public List<Integer> getTcpPorts() {
        return tcpPorts.isEmpty() ? new ArrayList<>(443) : tcpPorts;
    }

    @NonNull
    public static DefaultPorts getDefaults() {
        return new DefaultPorts(Collections.singletonList(443), Collections.singletonList(443));
    }
}
