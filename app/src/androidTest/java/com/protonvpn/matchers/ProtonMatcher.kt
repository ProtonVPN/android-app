package com.protonvpn.matchers

import android.view.View
import android.view.ViewGroup
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

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