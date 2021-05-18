/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.testsTv.matchers

import android.view.View
import android.widget.TextView
import androidx.leanback.widget.Presenter
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import com.protonvpn.android.tv.presenters.TvItemCardView
import org.hamcrest.Description
import org.hamcrest.Matcher

/**
 * [UiElementMatcher] Contains UI element matchers
 */
open class UiElementMatcher {

    fun withCardTitle(title: String): Matcher<Presenter.ViewHolder> {
        return object : BoundedMatcher<Presenter.ViewHolder,
                Presenter.ViewHolder>(Presenter.ViewHolder::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("Message item with subject: \"$title\"\n")
            }
            override fun matchesSafely(item: Presenter.ViewHolder): Boolean {
                return (item.view as TvItemCardView).binding.textTitle.text == title
            }
        }
    }

    fun getText(matcher: ViewInteraction): String {
        var text = String()
        matcher.perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isAssignableFrom(TextView::class.java)
            }

            override fun getDescription(): String {
                return "Gets text from element."
            }

            override fun perform(uiController: UiController, view: View) {
                val textView = view as TextView
                text = textView.text.toString()
            }
        })

        return text
    }
}