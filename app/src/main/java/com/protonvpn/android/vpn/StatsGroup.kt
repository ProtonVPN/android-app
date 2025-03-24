/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.vpn

import com.proton.gopenpgp.localAgent.LocalAgent
import com.proton.gopenpgp.localAgent.StringArray
import com.proton.gopenpgp.localAgent.StringToValueMap
import com.protonvpn.android.logging.LocalAgentError
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.vpn.StatsGroups.Companion.ADS
import com.protonvpn.android.vpn.StatsGroups.Companion.MALWARE
import com.protonvpn.android.vpn.StatsGroups.Companion.TRACKERS


fun StringToValueMap.toStats() =
    try {
        StatsGroups(keys.toSet().associateWith { groupName ->
            val group = getMap(groupName)
            group.keys.toSet().associate { k -> toHumanReadableStat(k) to group.getInt(k) }
        })
    } catch (e: Exception) {
        // Data came in unexpected format
        ProtonLogger.log(LocalAgentError, "Unexpected stats in local agent")
        null
    }

private fun StringArray.toSet() =
    mutableSetOf<String>().also { result ->
        for (i in 0 until count)
            result += get(i)
    }

data class StatsGroups(
    val groups: Map<String, Map<String, Long>>
) {
    fun getNetShieldStats() = groups[LocalAgent.constants().statsNetshieldLevelKey]

    fun getAds() = getNetShieldStats()?.get(ADS) ?: 0L

    fun getTracking() =
        getNetShieldStats()?.get(TRACKERS) ?: 0L

    fun getBandwidth() = getNetShieldStats()?.get(SAVED_BYTES) ?: 0L

    companion object {
        const val MALWARE = "malware"
        const val ADS = "ads"
        const val TRACKERS = "tracking"
        const val SAVED_BYTES = "savedBytes"
    }
}

fun toHumanReadableStat(statName: String) = when (statName) {
    LocalAgent.constants().statsMalwareKey -> MALWARE
    LocalAgent.constants().statsAdsKey -> ADS
    LocalAgent.constants().statsTrackerKey -> TRACKERS
    else -> statName
}

