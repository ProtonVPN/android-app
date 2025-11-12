/*
 *
 *  * Copyright (c) 2025. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.interfaces

import android.app.Activity
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.core.view.WindowInsetsCompat
import me.proton.core.test.android.instrumented.utils.waitUntil
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.wrappers.ComposeInteraction
import me.proton.test.fusion.ui.compose.wrappers.NodeActions

interface Robot {
    fun <T : Robot> NodeActions.clickTo(goesTo: T): T = goesTo.apply { click() }

    /** Common assertions **/
    fun nodeWithTextDisplayed(text: String) =
        node.withText(text).await { assertIsDisplayed() }

    fun nodeWithTextDisplayed(@StringRes stringRes: Int) =
        node.withText(stringRes).await { assertIsDisplayed() }

    infix fun ComposeInteraction<SemanticsNodeInteraction>.then(
        other: ComposeInteraction<SemanticsNodeInteraction>
    ): ComposeInteraction<SemanticsNodeInteraction> =
        let { other }
}

fun <T : Robot> T.verify(block: T.() -> Unit): T = apply { block() }

fun <T : Robot> T.waitUntilKeyboardVisibility(activity: Activity, isVisible: Boolean): T {
    waitUntil {
        val rootView = activity.window.decorView as ViewGroup
        val insets = WindowInsetsCompat.toWindowInsetsCompat(rootView.rootWindowInsets, rootView)
        return@waitUntil insets.isVisible(WindowInsetsCompat.Type.ime()) == isVisible
    }

    return this
}
