package com.protonvpn.actions.compose.interfaces

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
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

fun <T : Robot> T.verify(block: T.() -> ComposeInteraction<SemanticsNodeInteraction>): T = apply { block() }
