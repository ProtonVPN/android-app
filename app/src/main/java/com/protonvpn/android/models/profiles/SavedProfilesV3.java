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

import com.protonvpn.android.utils.Storage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SavedProfilesV3 implements Serializable {

    // Use hardcoded IDs for prebaked profiles.
    // It's not strictly necessary but should make things a bit more robust.
    private static final UUID FASTEST_PROFILE_ID = UUID.fromString("82c935d8-2968-4cc5-8ea7-8d73270efe57");
    private static final UUID RANDOM_PROFILE_ID = UUID.fromString("45509eff-bafb-46c1-8b16-ff605d94c5f6");

    private final List<Profile> profileList;

    public SavedProfilesV3(List<Profile> profileList) {
        this.profileList = profileList;
    }

    public List<Profile> getProfileList() {
        return profileList;
    }

    public static SavedProfilesV3 defaultProfiles() {
        SavedProfilesV3 defaultProfiles = new SavedProfilesV3(new ArrayList<>());
        Profile fastest = new Profile("fastest", null, ServerWrapper.makePreBakedFastest(),
                null, null, null, null, FASTEST_PROFILE_ID);
        Profile random = new Profile("random", null, ServerWrapper.makePreBakedRandom(),
                null, null, null, null, RANDOM_PROFILE_ID);
        defaultProfiles.getProfileList().add(fastest);
        defaultProfiles.getProfileList().add(random);
        return defaultProfiles;
    }

    public SavedProfilesV3 migrateProfiles() {
        List<Profile> migrated = new ArrayList<Profile>(profileList.size());
        boolean hasChanged = false;
        for (Profile profile : profileList) {
            final UUID profileId;
            if (profile.getWrapper().isPreBakedFastest())
                profileId = FASTEST_PROFILE_ID;
            else if (profile.getWrapper().isPreBakedRandom())
                profileId = RANDOM_PROFILE_ID;
            else
                profileId = null;
            Profile migratedProfile = profile.migrateFromOlderVersion(profileId);
            hasChanged |= migratedProfile != profile;
            migrated.add(migratedProfile);
        }
        SavedProfilesV3 migratedProfiles = new SavedProfilesV3(migrated);
        if (hasChanged)
            Storage.save(migratedProfiles);
        return migratedProfiles;
    }
}
