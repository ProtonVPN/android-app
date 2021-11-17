package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.data.DefaultData

class RealConnectionRobot : BaseRobot() {

    fun disconnectFromVPN() : RealConnectionRobot {
        clickElementById<RealConnectionRobot>(R.id.buttonDisconnect)
        return waitUntilDisplayedByText(R.string.loaderNotConnected)
    }

    fun connectThroughQuickConnectRealConnection() : RealConnectionRobot{
        HomeRobot().connectThroughQuickConnect(DefaultData.DEFAULT_CONNECTION_PROFILE)
        return waitUntilDisplayed(R.id.buttonDisconnect)
    }

    class Verify : BaseVerify(){

        fun checkIfDisconnected() = checkIfElementIsDisplayedById(R.id.textNotConnectedSuggestion)

        fun checkProtocol(protocol: VpnProtocol) =
            checkIfElementByIdContainsText(R.id.textProtocol, protocol.displayName())

        suspend fun checkIfConnectedAndCorrectIpAddressIsDisplayed(api: ProtonApiRetroFit){
            val ipAddress = api.getLocation().valueOrNull?.ipAddress.toString()
            checkIfElementIsDisplayedById(R.id.buttonDisconnect)
            checkIfElementByIdContainsText(R.id.textServerIp, ipAddress)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}