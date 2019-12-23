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
package com.protonvpn.actions;

;

import com.protonvpn.android.R;
import com.protonvpn.results.OnboardingResults;
import com.protonvpn.testsHelper.UIActionsTestHelper;

import androidx.test.espresso.action.ViewActions;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

public class OnboardingRobot extends UIActionsTestHelper {

    public OnboardingResults swipeLeft() {
        onView(withId(R.id.action_bar_root)).perform(ViewActions.swipeLeft());
        return new OnboardingResults();
    }

    public OnboardingResults swipeRight() {
        onView(withId(R.id.action_bar_root)).perform(ViewActions.swipeRight());
        return new OnboardingResults();
    }

    public OnboardingResults clickSkip() {
        clickOnObjectWithId(R.id.textSkip);
        return new OnboardingResults();
    }

    public OnboardingResults clickNext() {
        clickOnObjectWithId(R.id.textNext);
        return new OnboardingResults();
    }

    public OnboardingResults pressSignUp() {
        clickOnObjectWithId(R.id.buttonSignup);
        return new OnboardingResults();
    }

    public OnboardingResults pressLogin() {
        clickOnObjectWithId(R.id.buttonLogin);
        return new OnboardingResults();
    }
}

