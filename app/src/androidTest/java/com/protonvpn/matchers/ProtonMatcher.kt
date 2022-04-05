/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.matchers

import android.view.View
import android.view.ViewGroup
import com.protonvpn.base.BaseRobot
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/**
 * [ProtonMatcher] Contains custom matchers
 */
object ProtonMatcher {

    @JvmStatic
    fun lastChild(parentMatcher: Matcher<View?>, childMatcher: Matcher<View?>): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Last child ")
                childMatcher.describeTo(description)
                description.appendText(" in parent ")
                parentMatcher.describeTo(description)
            }

            override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                if (parent is ViewGroup && parentMatcher.matches(parent)) {
                    for (index in parent.childCount - 1 downTo 0) {
                        val child = parent.getChildAt(index)
                        if (childMatcher.matches(child)) return view === child
                    }
                }
                return false
            }
        }
    }
}