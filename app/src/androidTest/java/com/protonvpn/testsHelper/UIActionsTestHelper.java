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
import com.protonvpn.android.R;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.test.espresso.ViewInteraction;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withResourceName;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.protonvpn.testsHelper.UICustomViewActions.waitObjectWithContentDescription;
import static com.protonvpn.testsHelper.UICustomViewActions.waitObjectWithId;
import static com.protonvpn.testsHelper.UICustomViewActions.waitObjectWithIdAndText;
import static com.protonvpn.testsHelper.UICustomViewActions.waitText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.core.IsNull.nullValue;
import static org.strongswan.android.logic.StrongSwanApplication.getContext;

import org.hamcrest.Matchers;
import org.hamcrest.Matcher;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public class UIActionsTestHelper {

    // Should use this only for debug purposes
    public void sleep(int duration) {
        try {
            Thread.sleep(duration);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    protected void setStateOfSecureCoreSwitch(boolean state) {
        onView(isRoot()).perform(waitObjectWithId(R.id.switchSecureCore));
        if (state != ServiceTestHelper.isSecureCoreEnabled()) {
            clickOnObjectWithId(R.id.switchSecureCore);
        }
    }

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

    protected void clickOnRandomButtonFromQuickConnectMenu(boolean longClickVisible) {
        if (longClickVisible) {
            UiDevice device = UiDevice.getInstance(getInstrumentation());

            UiObject randomButton = device.findObject(new UiSelector().textContains("Random"));
            try {
                randomButton.click();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean isLongClickOnQuickConnect() {
        String resourceName = getContext().getResources().getResourceName(R.drawable.ic_fastest);

        ViewInteraction object = onView(withResourceName(resourceName));
        return !object.equals(nullValue());
    }

    protected void clickOnObjectChildWithIdAndPosition(@IdRes final int id, int position) {
        ViewInteraction object =
            onView(Matchers.allOf(childAtPosition(Matchers.allOf(withId(id)), position), isDisplayed()));
        object.perform(click());
    }

    protected void longClickOnObjectChildWithIdAndPosition(@IdRes final int id, int position) {
        ViewInteraction object =
            onView(Matchers.allOf(childAtPosition(Matchers.allOf(withId(id)), position), isDisplayed()));
        object.perform(longClick());
    }

    protected void clickOnObjectChildWithinChildWithIdAndPosition(@IdRes final int id, int position,
                                                                  int childPosition) {
        ViewInteraction object = onView(
            Matchers.allOf(childAtPosition(childAtPosition(withId(id), position), childPosition),
                isDisplayed()));
        object.perform(click());
    }

    protected void insertTextIntoFieldWithId(@IdRes int objectId, String text) {
        ViewInteraction field = onView(Matchers.allOf(withId(objectId), isDisplayed()));
        field.perform(replaceText(text), closeSoftKeyboard());
    }

    protected void insertTextIntoFieldWithContentDescription(String contentDescription, String text) {
        ViewInteraction field =
            onView(Matchers.allOf(withContentDescription(contentDescription), isDisplayed()));
        field.perform(replaceText(text), closeSoftKeyboard());
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

    protected void clickOnObjectWithId(@IdRes int objectId) {
        ViewInteraction object = onView(Matchers.allOf(withId(objectId), isDisplayed()));
        object.perform(click());
    }

    protected void checkIfObjectWithIdIsDisplayed(@IdRes int objectId) {
        ViewInteraction object = onView(Matchers.allOf(withId(objectId), isDisplayed()));
        object.check(matches(isDisplayed()));
    }

    protected void checkIfObjectWithIdIsNotDisplayed(@IdRes int objectId) {
        onView(Matchers.allOf(withId(objectId))).equals(nullValue());
    }

    protected void checkIfObjectWithTextIsNotDisplayed(String text) {
        onView(Matchers.allOf(withText(text))).equals(nullValue());
    }

    protected void checkIfObjectWithTextIsDisplayed(String text) {
        onView(isRoot()).perform(waitText(text));
        ViewInteraction object = onView(Matchers.allOf(withText(text), isDisplayed()));
        object.check(matches(withText(text)));
    }

    protected void checkIfObjectWithTextIsDisplayed(@StringRes int resId) {
        checkIfObjectWithTextIsDisplayed(getContext().getString(resId));
    }

    protected void checkIfObjectWithIdAndTextIsDisplayed(@IdRes int objectId, @StringRes int resId) {
        checkIfObjectWithIdAndTextIsDisplayed(objectId, getContext().getString(resId));
    }

    protected void checkIfObjectWithIdAndTextIsDisplayed(@IdRes int objectId, String text) {
        ViewInteraction object = onView(Matchers.allOf(withId(objectId), withText(text), isDisplayed()));
        object.check(matches(withText(text)));
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

    protected void clickOnMapNode(String countryName) {
        onView(isRoot()).perform(waitObjectWithContentDescription(countryName));
        ViewInteraction object =
            onView(Matchers.allOf(withContentDescription(containsString(countryName)), isDisplayed()));
        object.perform(click());
    }

    protected void checkIfMapNodeIsSelected(String countryName) {
        onView(isRoot()).perform(waitObjectWithContentDescription(countryName + " Selected"));
    }

    protected void checkIfMapNodeIsNotSelected(String countryName) {
        ViewInteraction object = onView(Matchers.allOf(withContentDescription(countryName + " Selected")));
        object.check(doesNotExist());
    }

    public void checkIfErrorMessageHasAppeared(String errorMessage) {
        UICustomMatchers.withErrorText(Matchers.containsString(errorMessage));
    }

    public void checkIfErrorMessageHasAppeared(@StringRes int stringId) {
        checkIfErrorMessageHasAppeared(getContext().getString(stringId));
    }

    public static void pressDeviceBackButton() {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.pressBack();
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

    public static boolean isButtonWithIdAndTextVisible(@IdRes int buttonId, @StringRes int resId) {
        return isButtonWithIdAndTextVisible(buttonId, getContext().getString(resId));
    }

    public static boolean isObjectWithTextVisible(String text) {
        try {
            onView(Matchers.allOf(withText(equalToIgnoringCase(text)), isDisplayed())).
                check(matches(isDisplayed()));
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isObjectWithTextVisible(@StringRes int resId) {
        return isObjectWithTextVisible(getContext().getString(resId));
    }

    protected void checkIfButtonOpensUrl(int buttonId) {
        UiDevice myDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            UiObject object = myDevice.findObject(
                new UiSelector().resourceId(getContext().getResources().getResourceName(buttonId)));
            object.click();
            myDevice.findObject(new UiSelector().text("Open with")).exists();
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void waitUntilObjectWithTextAppearsInView(String text) {
        Instruction instruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until object appears";
            }

            @Override
            public boolean checkCondition() {
                try {
                    onView(Matchers.allOf(withText(equalToIgnoringCase(text)), isDisplayed())).check(
                        matches(isDisplayed()));
                    return true;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };

        checkCondition(instruction);
    }

    public static void waitUntilObjectWithContentDescriptionAppearsInView(String contentDescription) {
        Instruction instruction = new Instruction() {
            @Override
            public String getDescription() {
                return "Waiting until object appears";
            }

            @Override
            public boolean checkCondition() {
                try {
                    onView(Matchers.allOf(withContentDescription(contentDescription), isDisplayed())).check(
                        matches(isDisplayed()));
                    return true;
                }
                catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        };

        checkCondition(instruction);
    }

    public static void waitUntilObjectWithContentDescriptionAppearsInView(@IdRes int contentDescription) {
        waitUntilObjectWithContentDescriptionAppearsInView(getContext().getString(contentDescription));
    }

    private static void checkCondition(Instruction instruction) {
        try {
            ConditionWatcher.waitForCondition(instruction);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Matcher<View> childAtPosition(final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent) && view.equals(
                    ((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
