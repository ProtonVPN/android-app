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
package com.protonvpn.testsHelper;

import com.protonvpn.android.utils.Storage;

import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.COUNTRY_DIALOG;
import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.FLOATINGACTION_DIALOG;
import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.FLOATING_BUTTON_USED;
import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.MAPVIEW_DIALOG;
import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.PROFILES_DIALOG;
import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.SECURECORE_DIALOG;
import static com.protonvpn.android.ui.onboarding.OnboardingPreferences.SLIDES_SHOWN;

public class TestSetup {

    public static void setCompletedOnboarding() {
        //set flag to slide show to be visible
        Storage.saveBoolean(SLIDES_SHOWN, true);
        Storage.saveBoolean(MAPVIEW_DIALOG, true);
        Storage.saveBoolean(PROFILES_DIALOG, true);
        Storage.saveBoolean(SECURECORE_DIALOG, true);
        Storage.saveBoolean(FLOATINGACTION_DIALOG, true);
        Storage.saveBoolean(FLOATING_BUTTON_USED, true);
        Storage.saveBoolean(COUNTRY_DIALOG, true);
    }
}
