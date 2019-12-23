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

import android.view.View;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.util.concurrent.TimeoutException;

import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.util.HumanReadables;
import androidx.test.espresso.util.TreeIterables;

import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.equalToIgnoringCase;

public class UICustomViewActions {

    public static ViewAction waitText(String text) {
        return waitForObject(withText(text), "wait for specific view with text: " + text);
    }

    public static ViewAction waitObjectWithIdAndText(final int id, String text) {
        return waitForObject(
            Matchers.allOf(withId(id), withText(equalToIgnoringCase(text)), isDisplayed(), isClickable(),
                isEnabled()), "wait for specific view with id: " + id + " text: " + text);
    }

    public static ViewAction waitObjectWithId(final int id) {
        return waitForObject(Matchers.allOf(withId(id), isDisplayed()),
            "wait for a specific view with id: " + id);
    }

    public static ViewAction waitObjectWithContentDescription(String contentDescription) {
        return waitForObject(
            Matchers.allOf(withContentDescription(equalToIgnoringCase(contentDescription)), isDisplayed()),
            "wait for a specific view with content description: + " + contentDescription);
    }

    private static ViewAction waitForObject(Matcher<View> matcher, String description) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return Matchers.allOf(isRoot());
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public void perform(final UiController uiController, final View view) {
                uiController.loopMainThreadUntilIdle();
                final long startTime = System.currentTimeMillis();
                final long endTime = startTime + 5000;

                do {
                    uiController.loopMainThreadForAtLeast(500);
                    for (View child : TreeIterables.breadthFirstViewTraversal(view)) {
                        if (matcher.matches(child)) {
                            return;
                        }
                    }
                }
                while (System.currentTimeMillis() < endTime);

                throw new PerformException.Builder().withActionDescription(this.getDescription())
                    .withViewDescription(HumanReadables.describe(view))
                    .withCause(new TimeoutException())
                    .build();
            }
        };
    }
}
