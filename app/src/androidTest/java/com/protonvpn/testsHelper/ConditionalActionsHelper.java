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
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
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
        Instruction instruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until object appears";
            }

            @Override
            public boolean checkCondition() {
                try {
                    onView(withId(viewId)).perform(swipeUp());
                    return isObjectWithIdVisible(objectId);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };
        checkCondition(instruction);
    }

    public static void scrollDownInViewWithIdUntilObjectWithTextAppears(@IdRes int viewId, String text) {
        Instruction instruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until object appears";
            }

            @Override
            public boolean checkCondition() {
                try {
                    onView(withId(viewId)).perform(swipeUp());
                    return isObjectWithTextVisible(text);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };
        checkCondition(instruction);
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
