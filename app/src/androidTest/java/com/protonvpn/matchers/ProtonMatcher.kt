/*
 * Copyright (c) 2021 Proton AG
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
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textview.MaterialTextView
import com.protonvpn.android.utils.HtmlTools
import me.proton.core.presentation.ui.view.ProtonInput
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

    @JvmStatic
    fun inputFieldByLabel(labelText: String): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            public override fun matchesSafely(view: View): Boolean {
                if (view is EditText) {
                    val possibleProtonInputParent = view.parent.parent?.parent
                    if (possibleProtonInputParent is ProtonInput) {
                        possibleProtonInputParent.children.forEach {
                            if (it is MaterialTextView && it.text.equals(labelText)) {
                                return true
                            }
                        }
                    }
                }
                return false
            }

            override fun describeTo(description: Description) {
                description.appendText("Input field with label text $labelText")
            }
        }
    }

    fun withHtmlText(htmlText: String): Matcher<View> {
        val text = HtmlTools.fromHtml(htmlText)
        return ViewMatchers.withText(text.toString())
    }

    fun withHtmlText(@StringRes resId: Int): Matcher<View> {
        val htmlText = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
        return withHtmlText(htmlText)
    }
}
