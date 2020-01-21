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
package com.protonvpn.android.models.profiles;

import com.protonvpn.android.components.Listable;
import com.protonvpn.android.models.vpn.Server;

import java.io.Serializable;

public final class ServerWrapper implements Listable, Serializable {

    public ProfileType type;
    public String country;
    public String serverId;
    public boolean secureCoreCountry;
    private transient ServerDeliver deliver;

    @Override
    public String toString() {
        return "type: " + type + " country: " + country + " serverId: " + serverId + " secureCore: "
            + secureCoreCountry + " deliverer: " + deliver.toString();
    }

    @Override
    public String getLabel() {
        switch (type) {
            case RANDOM_IN_COUNTRY:
                return "Random" + upgradeLabel(deliver.getServer(this));
            case FASTEST_IN_COUNTRY:
                return "Fastest" + upgradeLabel(deliver.getServer(this));
            case DIRECT:
                Server server = deliver.getServer(this);
                return server.getLabel() + upgradeLabel(server);
        }
        throw new RuntimeException("Label not found for: " + type);
    }

    public void setDeliverer(ServerDeliver deliverer) {
        this.deliver = deliverer;
    }

    private String upgradeLabel(Server server) {
        return server.isOnline() ? (deliver.hasAccessToServer(server) ? "" : " (Upgrade)") :
            " Under maintenance";
    }

    public void setSecureCoreCountry(boolean secureCoreCountry) {
        this.secureCoreCountry = secureCoreCountry;
    }

    public enum ProfileType {
        FASTEST, RANDOM, RANDOM_IN_COUNTRY, FASTEST_IN_COUNTRY, DIRECT
    }

    boolean isPreBakedFastest() {
        return type.equals(ProfileType.FASTEST);
    }

    boolean isFastestInCountry() {
        return type.equals(ProfileType.FASTEST_IN_COUNTRY);
    }

    boolean isPreBakedRandom() {
        return type.equals(ProfileType.RANDOM);
    }

    boolean isPreBakedProfile() {
        return type.equals(ProfileType.FASTEST) || type.equals(ProfileType.RANDOM);
    }

    private ServerWrapper(ProfileType type, String country, String serverId, ServerDeliver deliver) {
        this.type = type;
        this.country = country;
        this.serverId = serverId;
        this.deliver = deliver;
    }

    public static ServerWrapper makePreBakedFastest(ServerDeliver deliver) {
        return new ServerWrapper(ProfileType.FASTEST, "", "", deliver);
    }

    public static ServerWrapper makePreBakedRandom(ServerDeliver deliver) {
        return new ServerWrapper(ProfileType.RANDOM, "", "", deliver);
    }

    public static ServerWrapper makeWithServer(Server server, ServerDeliver deliver) {
        return new ServerWrapper(ProfileType.DIRECT, "", server.getServerId(), deliver);
    }

    public static ServerWrapper makeFastestForCountry(String country, ServerDeliver deliver) {
        return new ServerWrapper(ProfileType.FASTEST_IN_COUNTRY, country, "", deliver);
    }

    public static ServerWrapper makeRandomForCountry(String country, ServerDeliver deliver) {
        return new ServerWrapper(ProfileType.RANDOM_IN_COUNTRY, country, "", deliver);
    }

    boolean isSecureCoreServer() {
        return secureCoreCountry;
    }

    public Server getServer() {
        return deliver.getServer(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerWrapper)) {
            return false;
        }

        ServerWrapper that = (ServerWrapper) o;

        return secureCoreCountry == that.secureCoreCountry && type == that.type && (country != null ?
            country.equals(that.country) : that.country == null) && (serverId != null ?
            serverId.equals(that.serverId) : that.serverId == null);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (country != null ? country.hashCode() : 0);
        result = 31 * result + (serverId != null ? serverId.hashCode() : 0);
        result = 31 * result + (secureCoreCountry ? 1 : 0);
        return result;
    }
}
