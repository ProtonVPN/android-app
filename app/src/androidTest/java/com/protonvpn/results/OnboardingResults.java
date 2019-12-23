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
package com.protonvpn.results;

import com.protonvpn.android.R;
import com.protonvpn.testsHelper.UIActionsTestHelper;

public class OnboardingResults extends UIActionsTestHelper {

    public OnboardingResults onboardingViewIsVisible() {
        checkIfObjectWithIdIsDisplayed(R.id.protonLogo);
        checkIfObjectWithIdIsDisplayed(R.id.textSkip);
        checkIfObjectWithIdIsDisplayed(R.id.textNext);
        checkIfObjectWithIdIsDisplayed(R.id.mapView);
        checkIfObjectWithIdIsDisplayed(R.id.textTitle);
        checkIfObjectWithIdIsDisplayed(R.id.textDescription);
        return this;
    }

    public OnboardingResults isTextWeBelieveIsDisplayed() {
        checkIfObjectWithTextIsDisplayed(R.string.onboardingMiddleSlideTitle);
        return this;
    }

    public OnboardingResults isTextWelcomeIsDisplayed() {
        checkIfObjectWithTextIsDisplayed(R.string.onboardingStartSlideDescription);
        return this;
    }

    public OnboardingResults isLoginPageDisplayed() {
        checkIfObjectWithIdIsDisplayed(R.id.buttonLogin);
        return this;
    }

    public OnboardingResults isFinalPageDisplayed() {
        checkIfObjectWithTextIsDisplayed(R.string.onboardingEndingSlideDescription);
        checkIfObjectWithIdIsDisplayed(R.id.buttonLogin);
        checkIfObjectWithIdIsDisplayed(R.id.buttonSignup);
        checkIfObjectWithIdIsNotDisplayed(R.id.textSkip);
        checkIfObjectWithIdIsNotDisplayed(R.id.textNext);
        return this;
    }

    public OnboardingResults signUpButtonHasLink() {
        checkIfButtonOpensUrl(R.id.buttonSignup);
        return this;
    }
}
