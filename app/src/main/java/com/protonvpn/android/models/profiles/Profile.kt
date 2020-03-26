/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.models.profiles

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.protonvpn.android.R
import com.protonvpn.android.components.Listable
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.Log
import com.protonvpn.android.utils.ServerManager
import java.io.Serializable
import java.util.Random

data class Profile(val name: String, val color: String, val wrapper: ServerWrapper) : Serializable, Listable {

    private var protocol: String? = null
    private var transmissionProtocol: String? = null

    fun getDisplayName(context: Context): String {
        return (if (isPreBakedProfile) context.getString(
                if (wrapper.isPreBakedFastest) R.string.profileFastest else R.string.profileRandom) else name)!!
    }

    @get:DrawableRes val profileIcon: Int
        get() {
            if (wrapper.isPreBakedFastest) {
                return R.drawable.ic_fastest
            }
            return if (wrapper.isPreBakedRandom) {
                R.drawable.ic_random
            } else R.drawable.ic_location
        }

    val isPreBakedProfile: Boolean
        get() = wrapper.isPreBakedProfile

    val server: Server?
        get() {
            val server = wrapper.server
            if (server != null && (wrapper.isFastestInCountry || wrapper.isPreBakedFastest)) {
                server.selectedAsFastest = true
            }
            return server
        }

    val isSecureCore: Boolean
        get() = wrapper.isSecureCoreServer

    override fun getLabel(context: Context): String {
        return getDisplayName(context)
    }

    fun getTransmissionProtocol(userData: UserData): String {
        return (if (transmissionProtocol == null) userData.transmissionProtocol else transmissionProtocol)!!
    }

    fun setTransmissionProtocol(transmissionProtocol: String?) {
        this.transmissionProtocol = transmissionProtocol
    }

    fun isOpenVPNSelected(userData: UserData): Boolean {
        Log.e("protocol: " + getProtocol(userData))
        return getProtocol(userData) == "OpenVPN"
    }

    fun getProtocol(userData: UserData): String {
        return (if (protocol == null) userData.selectedProtocol.toString() else protocol!!)
    }

    fun setProtocol(protocol: String?) {
        this.protocol = protocol
    }

    companion object {
        fun getTemptProfile(server: Server?, manager: ServerManager?): Profile {
            return Profile("", "", ServerWrapper.makeWithServer(server, manager))
        }

        fun getRandomProfileColor(context: Context): String {
            val name = "pickerColor" + (Random().nextInt(18 - 1) + 1)
            val colorRes =
                    context.resources.getIdentifier(name, "color", context.packageName)
            return "#" + Integer.toHexString(ContextCompat.getColor(context, colorRes))
        }
    }
}
