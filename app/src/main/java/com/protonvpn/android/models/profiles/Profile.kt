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
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import java.io.Serializable
import java.util.Locale
import java.util.UUID

data class Profile @JvmOverloads constructor(
    val name: String,
    private val color: String?,
    val wrapper: ServerWrapper,
    private val colorId: Int?,
    val isSecureCore: Boolean?,
    private var protocol: String? = null,
    private var transmissionProtocol: String? = null,
    val id: UUID? = UUID.randomUUID()
) : Serializable {

    val profileColor: ProfileColor? = colorId?.let { ProfileColor.byId(it) }

    fun migrateFromOlderVersion(uuid: UUID?): Profile =
        migrateColor().migrateSecureCore().migrateUUID(uuid)

    private fun migrateColor(): Profile =
        if (color != null && colorId == null && !isPreBakedProfile) {
            val profileColor = ProfileColor.legacyColors.getOrElse(color.uppercase(Locale.US)) {
                ProfileColor.random() // Should not happen.
            }
            copy(color = null, colorId = profileColor.id)
        } else if (color != null && isPreBakedProfile) {
            copy(color = null, colorId = null)
        } else if (color == null && colorId != null && ProfileColor.byId(colorId) == null) {
            // Internal tester migration.
            copy(color = null, colorId = ProfileColor.values().first().id)
        } else {
            this
        }

    private fun migrateSecureCore(): Profile =
        if (isSecureCore == null && !isPreBakedProfile && !isPreBakedFastest) {
            copy(isSecureCore = wrapper.migrateSecureCoreCountry)
        } else {
            this
        }

    private fun migrateUUID(uuid: UUID?): Profile = if (id == null) copy(id = uuid ?: UUID.randomUUID()) else this

    fun getDisplayName(context: Context): String = if (isPreBakedProfile)
        context.getString(if (wrapper.isPreBakedFastest) R.string.profileFastest else R.string.profileRandom)
    else
        name

    @get:DrawableRes val profileSpecialIcon: Int? get() = when {
        wrapper.isPreBakedFastest -> R.drawable.ic_proton_bolt
        wrapper.isPreBakedRandom -> R.drawable.ic_proton_arrows_swap_right
        else -> null
    }

    val isPreBakedProfile: Boolean
        get() = wrapper.isPreBakedProfile
    val isPreBakedFastest: Boolean
        get() = wrapper.isPreBakedFastest
    val isDirectServer: Boolean
        get() = !directServerId.isNullOrBlank()

    val country: String get() = wrapper.country
    val directServerId: String? get() = wrapper.serverId

    fun getTransmissionProtocol(userData: UserData): TransmissionProtocol =
        transmissionProtocol?.let { TransmissionProtocol.valueOf(it) } ?: userData.transmissionProtocol

    fun setTransmissionProtocol(value: String?) {
        transmissionProtocol = value
    }

    fun getNetShieldProtocol(userData: UserData, vpnUser: VpnUser?, appConfig: AppConfig): NetShieldProtocol {
        return if (appConfig.getFeatureFlags().netShieldEnabled) {
            userData.getNetShieldProtocol(vpnUser)
        } else {
            NetShieldProtocol.DISABLED
        }
    }

    fun getProtocol(userData: UserData): VpnProtocol =
        protocol?.let { VpnProtocol.valueOf(it) } ?: userData.selectedProtocol

    fun setProtocol(protocol: VpnProtocol) {
        this.protocol = protocol.toString()
    }

    fun hasCustomProtocol() = protocol != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        @JvmStatic
        fun getTempProfile(server: Server) = getTempProfile(server, null)
        fun getTempProfile(server: Server, isSecureCore: Boolean?) =
            getTempProfile(ServerWrapper.makeWithServer(server), isSecureCore)
        fun getTempProfile(serverWrapper: ServerWrapper, isSecureCore: Boolean? = null) =
            Profile("", null, serverWrapper, null, isSecureCore)
    }
}
