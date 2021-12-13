/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.testsHelper

import com.azimolabs.conditionwatcher.ConditionWatcher
import com.azimolabs.conditionwatcher.Instruction
import com.protonvpn.android.components.LoaderUI
import com.protonvpn.android.components.NetworkFrameLayout
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.CountryTools.getFullName

class NetworkTestHelper : UIActionsTestHelper() {

    val serverManager = ServerManagerHelper().serverManager

    val vpnCountries: List<VpnCountry>
        get() = serverManager.getVpnCountries()
    val firstNotAccessibleVpnCountry: VpnCountry
        get() = serverManager.firstNotAccessibleVpnCountry
    val exitVpnCountries: List<VpnCountry>
        get() = serverManager.getSecureCoreExitCountries()

    fun getEntryVpnCountry(exitCountry: VpnCountry?): String {
        val countryCode = serverManager.getBestScoreServer(exitCountry!!)!!.entryCountry
        return getFullName(countryCode)
    }

    fun waitUntilNetworkErrorAppears(loader: LoaderUI) {
        val networkErrorInstruction: Instruction = object : Instruction() {
            override fun getDescription(): String {
                return "Waiting until network loader returns Error state"
            }

            override fun checkCondition(): Boolean {
                return loader.state === NetworkFrameLayout.State.ERROR
            }
        }
        checkCondition(networkErrorInstruction)
    }

    private fun checkCondition(instruction: Instruction) {
        try {
            ConditionWatcher.waitForCondition(instruction)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
