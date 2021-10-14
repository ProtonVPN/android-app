package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify

class RealConnectionRobot : BaseRobot() {

    fun disconnectFromVPN() : RealConnectionRobot {
        clickElementById<RealConnectionRobot>(R.id.buttonDisconnect)
        waitUntilDisplayedByText<RealConnectionRobot>(R.string.loaderNotConnected)
        return this
    }

    fun connectThroughQuickConnectRealConnection() : RealConnectionRobot{
        HomeRobot().connectThroughQuickConnect()
        waitUntilDisplayed<RealConnectionRobot>(R.id.buttonDisconnect)
        return this;
    }

    class Verify : BaseVerify(){

        fun checkIfDisconnected() = checkIfElementIsDisplayedById<RealConnectionRobot>(R.id.textNotConnectedSuggestion)

        fun checkProtocol(protocol: VpnProtocol) {
            checkIfElementByIdContainsText<RealConnectionRobot>(R.id.textProtocol, protocol.displayName())
        }

        suspend fun checkIfConnectedAndCorrectIpAddressIsDisplayed(api: ProtonApiRetroFit){
            val ipAddress = api.getLocation().valueOrNull?.ipAddress.toString()
            checkIfElementIsDisplayedById<RealConnectionRobot>(R.id.buttonDisconnect)
            checkIfElementByIdContainsText<RealConnectionRobot>(R.id.textServerIp, ipAddress)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}