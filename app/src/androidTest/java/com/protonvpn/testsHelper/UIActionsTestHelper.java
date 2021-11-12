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

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.azimolabs.conditionwatcher.ConditionWatcher;
import com.azimolabs.conditionwatcher.Instruction;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.test.espresso.ViewInteraction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.protonvpn.testsHelper.UICustomViewActions.waitObjectWithIdAndText;
import static com.protonvpn.testsHelper.UICustomViewActions.waitText;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.strongswan.android.logic.StrongSwanApplication.getContext;

import org.hamcrest.Matchers;
import org.hamcrest.Matcher;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public class UIActionsTestHelper {

    protected void allowVpnToBeUsed(boolean requestVisible) {
        if (requestVisible) {
            UiDevice device = UiDevice.getInstance(getInstrumentation());

            UiObject okButton = device.findObject(new UiSelector().textContains("OK"));
            try {
                okButton.click();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean isAllowVpnRequestVisible() {
        UiDevice device = UiDevice.getInstance(getInstrumentation());
        return device.findObject(new UiSelector().textContains("Connection request")) == null ? false : true;
    }

    protected void longClickOnLastChildWithId(@IdRes final int id, @NonNull Matcher<View> childMatcher) {
        onView(lastChild(withId(id), childMatcher)).perform(longClick());
    }

    protected void clickOnObjectWithIdAndText(@IdRes int objectId, String text) {
        onView(isRoot()).perform(waitObjectWithIdAndText(objectId, text));
        ViewInteraction object = onView(
            Matchers.allOf(withId(objectId), withText(equalToIgnoringCase(text)), isDisplayed(),
                isClickable(), isEnabled()));

        object.check(matches(isClickable()));

        object.perform(click());
    }

    protected void clickOnObjectWithIdAndText(@IdRes final int objectId, @StringRes final int textId) {
        clickOnObjectWithIdAndText(objectId, getContext().getString(textId));
    }

    protected void clickOnObjectWithContentDescription(String text) {
        ViewInteraction object = onView(Matchers.allOf(withContentDescription(text), isDisplayed()));
        object.perform(click());
    }

    protected void clickOnObjectWithContentDescription(@StringRes int resId) {
        clickOnObjectWithContentDescription(getContext().getString(resId));
    }

    protected void clickOnObjectWithText(@StringRes int resId) {
        clickOnObjectWithText(getContext().getString(resId));
    }

    protected void clickOnObjectWithText(String text) {
        onView(isRoot()).perform(waitText(text));
        ViewInteraction object = onView(Matchers.allOf(withText(text), isDisplayed()));
        object.perform(click());
    }

    public static boolean isObjectWithIdVisible(int objectId) {
        try {
            onView(Matchers.allOf(withId(objectId), isDisplayed())).
                check(matches(isDisplayed()));
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isButtonWithIdAndTextVisible(int buttonId, String text) {
        try {
            onView(Matchers.allOf(withId(buttonId), withText(equalToIgnoringCase(text)), isEnabled(),
                isClickable())).
                check(matches(isDisplayed()));
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    protected static Matcher<View> lastChild(
            final Matcher<View> parentMatcher, final Matcher<View> childMatcher) {
        return new TypeSafeMatcher<View>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("Last child ");
                childMatcher.describeTo(description);
                description.appendText(" in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            protected boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup && parentMatcher.matches(parent)) {
                    ViewGroup parentView = (ViewGroup) parent;
                    for (int index = parentView.getChildCount() - 1; index >= 0; --index) {
                        View child = parentView.getChildAt(index);
                        if (childMatcher.matches(child))
                            return view == child;
                    }
                }
                return false;
            }
        };
    }
}
