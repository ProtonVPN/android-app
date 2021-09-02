package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.testsHelper.ServerManagerHelper

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
            if(protocol != VpnProtocol.Smart){
                //It'll ignore Smart Protocol, because user might be connected to different protocols.
                checkIfElementByIdContainsText<RealConnectionRobot>(R.id.textProtocol, protocol.displayName())
            }
        }

        suspend fun checkIfConnectedAndCorrectIpAddressIsDisplayed(){
            checkIfElementIsDisplayedById<RealConnectionRobot>(R.id.buttonDisconnect)
            val location = ServerManagerHelper().backend.appConfig.api.getLocation().valueOrNull?.ipAddress.toString()
            checkIfElementByIdContainsText<RealConnectionRobot>(R.id.textServerIp, location)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}