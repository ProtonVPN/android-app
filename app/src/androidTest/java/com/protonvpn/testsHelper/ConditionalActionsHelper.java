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
package com.protonvpn.testsHelper;

import com.azimolabs.conditionwatcher.ConditionWatcher;
import com.azimolabs.conditionwatcher.Instruction;
import com.protonvpn.android.R;
import com.protonvpn.android.vpn.VpnStateMonitor;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.protonvpn.testsHelper.ScrollToExKt.scrollToEx;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.strongswan.android.logic.StrongSwanApplication.getContext;

public class ConditionalActionsHelper extends UIActionsTestHelper {

    VpnStateMonitor stateMonitor = new ServerManagerHelper().vpnStateMonitor;

    public void clickOnDisconnectButtonUntilUserIsDisconnected() {
        Instruction instruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until object appears";
            }

            @Override
            public boolean checkCondition() {
                try {
                    clickOnObjectWithText(R.string.disconnect);
                    return !stateMonitor.isConnected();
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };
        checkCondition(instruction);
    }

    public void clickOnMapNodeUntilConnectButtonAppears(String countryName) {
        Instruction instruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until object appears";
            }

            @Override
            public boolean checkCondition() {
                try {
                    clickOnMapNode(countryName);
                    return isButtonWithIdAndTextVisible(R.id.buttonConnect, R.string.connect);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };
        checkCondition(instruction);
    }

    public static void scrollDownInViewWithIdUntilObjectWithIdAppears(@IdRes int viewId,
                                                                      @IdRes int objectId) {
        onView(withId(objectId)).perform(scrollToEx()).check(matches(isDisplayed()));
    }

    public static void scrollDownInViewWithIdUntilObjectWithTextAppears(@IdRes int viewId, String text) {
        onView(withText(equalToIgnoringCase(text))).perform(scrollToEx()).check(matches(isDisplayed()));
    }

    public static void scrollDownInViewWithIdUntilObjectWithTextAppears(@IdRes int viewId,
                                                                        @StringRes int textId) {
        scrollDownInViewWithIdUntilObjectWithTextAppears(viewId, getContext().getString(textId));
    }

    private static void checkCondition(Instruction instruction) {
        ConditionWatcher.setWatchInterval(500);
        try {
            ConditionWatcher.waitForCondition(instruction);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
