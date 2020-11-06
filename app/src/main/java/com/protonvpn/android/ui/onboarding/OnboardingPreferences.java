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
package com.protonvpn.android.ui.onboarding;

import com.protonvpn.android.utils.Storage;

import java.io.Serializable;

public class OnboardingPreferences implements Serializable {

    public static String MAPVIEW_DIALOG = "MapViewShown";
    public static String PROFILES_DIALOG = "ProfilesShown";
    public static String SECURECORE_DIALOG = "SecureCoreShown";
    public static String NETSHIELD_DIALOG = "NetShieldShown";
    public static String FLOATINGACTION_DIALOG = "FloatingActionShown";
    public static String FLOATING_BUTTON_USED = "FloatingActionUsed";
    public static String COUNTRY_DIALOG = "CountryDialogShown";
    public static String SLIDES_SHOWN = "OnboardingShown";

    static boolean wasOnboardingShown() {
        if (!Storage.getBoolean(SLIDES_SHOWN)) {
            Storage.saveBoolean(SLIDES_SHOWN, true);
            return false;
        }
        return true;
    }

    public static void clearPreferences() {
        Storage.saveBoolean(MAPVIEW_DIALOG, false);
        Storage.saveBoolean(PROFILES_DIALOG, false);
        Storage.saveBoolean(SECURECORE_DIALOG, false);
        Storage.saveBoolean(FLOATINGACTION_DIALOG, false);
        Storage.saveBoolean(FLOATING_BUTTON_USED, false);
        Storage.saveBoolean(COUNTRY_DIALOG, false);
        Storage.saveBoolean(SLIDES_SHOWN, false);
    }

    public static boolean wasCountryDialogShown() {
        return Storage.getBoolean(COUNTRY_DIALOG);
    }

    public static boolean wasFloatingButtonUsed() {
        return Storage.getBoolean(FLOATING_BUTTON_USED);
    }

    public static boolean wasFloatingTipUsed() {
        return Storage.getBoolean(FLOATINGACTION_DIALOG);
    }
}
