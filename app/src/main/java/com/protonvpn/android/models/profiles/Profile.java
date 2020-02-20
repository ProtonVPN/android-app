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

import android.content.Context;

import com.protonvpn.android.R;
import com.protonvpn.android.components.Listable;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;

import java.io.Serializable;
import java.util.Random;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class Profile implements Serializable, Listable {

    private final String name;
    private final String color;
    private String protocol;
    private String transmissionProtocol;
    private final ServerWrapper wrapper;

    public Profile(@NonNull String name, @NonNull String color, @NonNull ServerWrapper wrapper) {
        this.name = name;
        this.color = color;
        this.wrapper = wrapper;
    }

    public static Profile getTemptProfile(Server server, ServerManager manager) {
        return new Profile("", "", ServerWrapper.makeWithServer(server, manager));
    }

    public String getDisplayName(Context context) {
        return isPreBakedProfile() ? context.getString(
            wrapper.isPreBakedFastest() ? R.string.profileFastest : R.string.profileRandom) : name;
    }

    public String getColor() {
        return color;
    }

    @DrawableRes
    public int getProfileIcon() {
        if (wrapper.isPreBakedFastest()) {
            return R.drawable.ic_fastest;
        }
        if (wrapper.isPreBakedRandom()) {
            return R.drawable.ic_random;
        }
        return R.drawable.ic_location;
    }

    public boolean isPreBakedProfile() {
        return wrapper.isPreBakedProfile();
    }

    public ServerWrapper getServerWrapper() {
        return wrapper;
    }

    @Nullable
    public Server getServer() {
        Server server = wrapper.getServer();
        if (server != null && (wrapper.isFastestInCountry() || wrapper.isPreBakedFastest())) {
            server.setSelectedAsFastest(true);
        }
        return server;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Profile)) {
            return false;
        }

        Profile profile = (Profile) o;

        return name != null ? name.equals(profile.name) :
            profile.name == null && (color != null ? color.equals(profile.color) :
                profile.color == null && (wrapper != null ? wrapper.equals(profile.wrapper) :
                    profile.wrapper == null));
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (color != null ? color.hashCode() : 0);
        result = 31 * result + (wrapper != null ? wrapper.hashCode() : 0);
        return result;
    }

    public boolean isSecureCore() {
        return getServerWrapper().isSecureCoreServer();
    }

    public static String getRandomProfileColor(Context context) {
        String name = "pickerColor" + (new Random().nextInt(18 - 1) + 1);
        int colorRes = context.getResources().getIdentifier(name, "color", context.getPackageName());
        return "#" + Integer.toHexString(ContextCompat.getColor(context, colorRes));
    }

    @Override
    public String getLabel(Context context) {
        return getDisplayName(context);
    }

    public String getTransmissionProtocol(@NonNull UserData userData) {
        return transmissionProtocol == null ? userData.getTransmissionProtocol() : transmissionProtocol;
    }

    public void setTransmissionProtocol(@Nullable String transmissionProtocol) {
        this.transmissionProtocol = transmissionProtocol;
    }

    public boolean isOpenVPNSelected(@NonNull UserData userData) {
        Log.e("protocol: " + getProtocol(userData));
        return getProtocol(userData).equals("OpenVPN");
    }

    public String getProtocol(@NonNull UserData userData) {
        return protocol == null ? userData.getSelectedProtocol().toString() : protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
