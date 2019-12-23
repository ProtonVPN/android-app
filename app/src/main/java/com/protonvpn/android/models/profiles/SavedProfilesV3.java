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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SavedProfilesV3 implements Serializable {

    private final List<Profile> profileList;

    public SavedProfilesV3(List<Profile> profileList) {
        this.profileList = profileList;
    }

    public List<Profile> getProfileList() {
        return profileList;
    }

    public static SavedProfilesV3 defaultProfiles(ServerDeliver deliver) {
        SavedProfilesV3 defaultProfiles = new SavedProfilesV3(new ArrayList<Profile>());
        defaultProfiles.getProfileList()
            .add(new Profile("Fastest", "#27272c", ServerWrapper.makePreBakedFastest(deliver)));
        defaultProfiles.getProfileList()
            .add(new Profile("Random", "#27272c", ServerWrapper.makePreBakedRandom(deliver)));
        return defaultProfiles;
    }
}
