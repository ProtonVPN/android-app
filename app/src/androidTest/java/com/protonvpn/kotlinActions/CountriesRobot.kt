package com.protonvpn.kotlinActions

import com.protonvpn.MockSwitch
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot

class CountriesRobot : BaseRobot() {

    fun selectCountry(country: String): CountriesRobot = clickElementByText(country)

    fun waitForCountryList(): CountriesRobot = waitUntilDisplayed(R.id.list)

    fun connectToFastestServer(): ConnectionRobot = clickConnectButton("fastest")

    fun clickConnectButton(contentDescription: String): ConnectionRobot {
        clickElementByIdAndContentDescription<HomeRobot>(R.id.buttonConnect, contentDescription)
        if (!MockSwitch.mockedConnectionUsed) {
            HomeRobot().allowVpnToBeUsed()
        }
        return ConnectionRobot()
    }
}