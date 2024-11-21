/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.tests.redesign.base.ui

import androidx.compose.ui.text.input.TextFieldValue
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.FusionConfig
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Before
import org.junit.Test

class ProtonTextFieldTests : FusionComposeTest() {

    @Before
    fun setup() {
        // Text fields are merged, so use unmerged tree to access individual parts.
        FusionConfig.Compose.useUnmergedTree.set(true)
    }

    @Test
    fun whenBothErrorAndAssistiveTextAreSetThenAssistiveTextIsDisplayed() {
        withContent {
            ProtonOutlinedTextField(
                value = TextFieldValue("text"),
                onValueChange = {},
                errorText = "Error text",
                assistiveText = "Assistive text"
            )
        }
        node.withText("Assistive text").assertIsDisplayed()
        node.withText("Error text").assertDoesNotExist()
    }

    @Test
    fun whenBothErrorAndAssistiveTextAreSetAndIsErrorThenErrorTextIsDisplayed() {
        withContent {
            ProtonOutlinedTextField(
                value = TextFieldValue("text"),
                onValueChange = {},
                errorText = "Error text",
                assistiveText = "Assistive text",
                isError = true
            )
        }
        node.withText("Error text").assertIsDisplayed()
        node.withText("Assistive text").assertDoesNotExist()
    }

    @Test
    fun whenOnlyAssistiveTextIsSetAndIsErrorThenAssistiveTextIsDisplayed() {
        withContent {
            ProtonOutlinedTextField(
                value = TextFieldValue("text"),
                onValueChange = {},
                assistiveText = "Assistive text",
                isError = true
            )
        }
        node.withText("Assistive text").assertIsDisplayed()
        node.withText("Error text").assertDoesNotExist()
    }
}
