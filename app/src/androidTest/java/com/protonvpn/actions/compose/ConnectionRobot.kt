package com.protonvpn.actions.compose

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.protonvpn.actions.compose.interfaces.Robot
import com.protonvpn.android.R
import com.protonvpn.data.Timeouts
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.wrappers.NodeActions
import me.proton.test.fusion.ui.compose.wrappers.NodeMatchers
import me.proton.test.fusion.ui.compose.ComposeWaiter.waitFor as wait

object ConnectionRobot : Robot {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val connectButton get() = node.withText(R.string.connect)
    private val disconnectButton get() = node.withText(R.string.disconnect)

    fun quickConnect() = connectButton.clickTo(this)
    fun disconnect() = disconnectButton.clickTo(this)

    fun allowVpnPermission(): ConnectionRobot {
        if (isAllowVpnRequestVisible()) {
            device.findObject(UiSelector().textContains("OK")).click()
        }
        return this
    }

    fun isConnected() = nodeWithTextDisplayed(R.string.vpn_status_connected)
    fun isDisconnected() = nodeWithTextDisplayed(R.string.vpn_status_disabled)

    private fun isAllowVpnRequestVisible(): Boolean {
        device.wait(Until.hasObject(By.text("OK")), Timeouts.FIVE_SECONDS_MS)
        return device.findObject(UiSelector().textContains("Connection request")).exists()
    }

    // NodeActions.waitFor is now private, make the functionality accessible.
    // If possible find a better way to do this (maybe ask for Fusion to be extended).
    private fun <T : NodeMatchers<T>> NodeMatchers<T>.waitFor(block : () -> Any) =
        (this as NodeActions).wait(block = block)
}
