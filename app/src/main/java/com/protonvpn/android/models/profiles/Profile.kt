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
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.vpn.ProtocolSelection
import java.io.Serializable
import java.util.Locale
import java.util.UUID
import me.proton.core.presentation.R as CoreR

data class Profile @JvmOverloads constructor(
    val name: String,
    private val color: String?,
    val wrapper: ServerWrapper,
    private val colorId: Int?,
    val isSecureCore: Boolean?,
    private var protocol: String? = null,
    private var transmissionProtocol: String? = null,
    val id: UUID? = UUID.randomUUID(),
    var isGuestHoleProfile: Boolean = false
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

    fun migrateProtocol(): Profile =
        if (protocol == PROTOCOL_IKEv2) copy(protocol = VpnProtocol.Smart.name, transmissionProtocol = null) else this

    fun getDisplayName(context: Context): String = if (isPreBakedProfile)
        context.getString(if (wrapper.isPreBakedFastest) R.string.profileFastest else R.string.profileRandom)
    else
        name

    @get:DrawableRes val profileSpecialIcon: Int? get() = when {
        wrapper.isPreBakedFastest -> CoreR.drawable.ic_proton_bolt
        wrapper.isPreBakedRandom -> CoreR.drawable.ic_proton_arrows_swap_right
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

    fun getProtocol(settings: LocalUserSettings) = protocol?.let { protocol ->
        val vpnProtocol = if (protocol == PROTOCOL_IKEv2)
            VpnProtocol.Smart else VpnProtocol.valueOf(protocol)
        ProtocolSelection(vpnProtocol, transmissionProtocol?.let(TransmissionProtocol::valueOf))
    } ?: settings.protocol

    fun setProtocol(protocol: ProtocolSelection) {
        this.protocol = protocol.vpn.toString()
        this.transmissionProtocol = protocol.transmission?.toString()
    }

    fun hasCustomProtocol() = protocol != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    fun isUnsupportedIKEv2() = protocol == PROTOCOL_IKEv2

    companion object {
        @JvmStatic
        fun getTempProfile(server: Server) = getTempProfile(server, null)
        fun getTempProfile(server: Server, isSecureCore: Boolean?) =
            getTempProfile(ServerWrapper.makeWithServer(server), isSecureCore)
        fun getTempProfile(serverWrapper: ServerWrapper, isSecureCore: Boolean? = null) =
            Profile("", null, serverWrapper, null, isSecureCore)

        const val PROTOCOL_IKEv2 = "IKEv2"
    }
}
