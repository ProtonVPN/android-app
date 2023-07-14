package com.protonvpn.actions.compose

import com.protonvpn.actions.compose.interfaces.Robot
import com.protonvpn.android.R
import me.proton.test.fusion.Fusion.node

object ConnectionPanelRobot : Robot {
    private val backButton get() = node.withContentDescription(R.string.accessibility_back)

    fun goBack(): ConnectionPanelRobot = backButton.clickTo(this)

    fun correctIpIsDisplayed(ip: String) = nodeWithTextDisplayed(ip)
    fun correctProtocolIsDisplayed(protocol: Int) = nodeWithTextDisplayed(protocol)
}