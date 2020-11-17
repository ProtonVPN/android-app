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
package com.protonvpn.tests.onboarding;

import com.protonvpn.actions.OnboardingRobot;
import com.protonvpn.android.ui.onboarding.OnboardingActivity;
import com.protonvpn.testsHelper.UIActionsTestHelper;
import com.protonvpn.results.OnboardingResults;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.rule.ActivityTestRule;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class OnboardingRobotTests extends UIActionsTestHelper {

    OnboardingRobot onboardingRobot = new OnboardingRobot();

    @Rule public ActivityTestRule<OnboardingActivity> activityTestRule =
        new ActivityTestRule<>(OnboardingActivity.class, false, true);

    @Test
    public void checkIfOnboardingViewIsVisible() {
        OnboardingResults result = new OnboardingResults();
        result.onboardingViewIsVisible();
    }

    @Test
    public void checkIfSwipeLeftWorks() {
        OnboardingResults result = onboardingRobot.swipeLeft();
        result.isTextWeBelieveIsDisplayed();
    }

    @Test
    public void checkIfSwipeRightWorks() {
        onboardingRobot.swipeLeft();
        OnboardingResults result = onboardingRobot.swipeRight();
        result.isTextWelcomeIsDisplayed();
    }

    @Test
    public void skipOnboarding() {
        OnboardingResults result = onboardingRobot.clickSkip();
        result.isLoginPageDisplayed();
    }

    @Test
    public void checkIfButtonNextWorks() {
        OnboardingResults result = onboardingRobot.clickNext();
        result.isTextWeBelieveIsDisplayed();
    }

    @Test
    public void checkIfFinalPageDisplayed() {
        onboardingRobot.clickNext();
        OnboardingResults result = onboardingRobot.clickNext();
        result.isFinalPageDisplayed();
    }

    @Test
    @FlakyTest
    public void checkIfSignUpNavigatesToCorrectLink() {
        onboardingRobot.clickNext();
        onboardingRobot.clickNext();
        OnboardingResults result = onboardingRobot.pressSignUp();
        result.signUpButtonHasLink();
    }

    // Currently skipped on Android 5-6, because animation Fails to load button fully. Flaky also on higher
    // versions.
    @SdkSuppress(minSdkVersion = 24)
    @Test
    @FlakyTest
    public void checkIfLoginButtonWorks() {
        onboardingRobot.clickNext();
        onboardingRobot.clickNext();
        OnboardingResults result = onboardingRobot.pressLogin();
        result.isLoginPageDisplayed();
    }
}