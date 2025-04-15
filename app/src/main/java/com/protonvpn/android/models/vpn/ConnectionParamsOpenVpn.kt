/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.models.vpn

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.models.vpn.usecase.toIPAddress
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.utils.Constants
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.Connection

class ConnectionParamsOpenVpn(
    connectIntent: AnyConnectIntent,
    server: Server,
    connectingDomain: ConnectingDomain,
    entryIp: String?,
    transmission: TransmissionProtocol,
    port: Int,
    ipv6SettingEnabled: Boolean,
) : ConnectionParams(
    connectIntent,
    server,
    connectingDomain,
    VpnProtocol.OpenVPN,
    entryIp,
    port,
    transmission,
    ipv6SettingEnabled = ipv6SettingEnabled,
), java.io.Serializable {

    override val info get() = "${super.info} $transmissionProtocol port: $port"

    fun openVpnProfile(
        myPackageName: String,
        userSettings: LocalUserSettings,
        clientCertificate: CertificateData?,
        computeAllowedIPs: ComputeAllowedIPs,
    ) = VpnProfile(server.getLabel()).apply {
        if (clientCertificate != null) {
            mAuthenticationType = VpnProfile.TYPE_CERTIFICATES
            mClientKeyFilename = inlineFile(clientCertificate.key)
            mClientCertFilename = inlineFile(clientCertificate.certificate)
        } else {
            mAuthenticationType = VpnProfile.TYPE_USERPASS
            mUsername = "guest"
            mPassword = "guest"
        }
        mCaFilename = Constants.VPN_ROOT_CERTS
        mTLSAuthFilename = TLS_AUTH_KEY
        mTLSAuthDirection = "tls-crypt"
        mCipher = "AES-256-GCM"
        mUseTLSAuth = true
        mTunMtu = 1500
        mMssFix = userSettings.mtuSize - 40
        mExpectTLSCert = true
        mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_SAN
        mCheckRemoteCN = true
        mRemoteCN = connectingDomain!!.entryDomain
        mPersistTun = true
        val splitsTunnel = userSettings.splitTunneling.isEnabled || userSettings.lanConnections
        if (connectIntent !is AnyConnectIntent.GuestHole && splitsTunnel) {
            val (allowedIPs4, allowedIPs6) = computeAllowedIPs(userSettings).partition { it.isIPv4 }
            val splitV4 = allowedIPs4 != listOf("0.0.0.0/0".toIPAddress())
            val splitV6 = allowedIPs6 != listOf("::/0".toIPAddress())
            if (splitV4 || splitV6) {
                mUseCustomConfig = true
                mCustomConfigOptions += "pull-filter ignore \"redirect-gateway\"\n"
                mUseDefaultRoute = false
                mUseDefaultRoutev6 = false
                mCustomRoutes = allowedIPs4.joinToString(" ") { it.toCanonicalString() }
                mCustomRoutesv6 = allowedIPs6.joinToString(" ") { it.toCanonicalString() }
            }
        }

        mOverrideDNS = userSettings.customDns.effectiveEnabled
        mCustomDNS = userSettings.customDns.effectiveDnsList

        val appsSplitTunnelingConfigurator = SplitTunnelAppsOpenVpnConfigurator(this)
        applyAppsSplitTunneling(
            appsSplitTunnelingConfigurator,
            connectIntent,
            myPackageName,
            userSettings.splitTunneling
        )
        mConnections[0] = Connection().apply {
            mServerName = entryIp ?: requireNotNull(connectingDomain.getEntryIp(protocolSelection))
            mUseUdp = transmissionProtocol == TransmissionProtocol.UDP
            mServerPort = port.toString()
            mCustomConfiguration = ""
        }
        if (enableIPv6 == true && server.isIPv6Supported) {
            ProtonLogger.logCustom(LogCategory.CONN, "OpenVPN IPv4+6 tunnel")
            // Will push custom config enabling v6 (UV_IPV6 1) to server
            mPushPeerInfo = true
            mUseCustomConfig = true
            mCustomConfigOptions += "setenv UV_IPV6 1\n"
        } else {
            ProtonLogger.logCustom(LogCategory.CONN, "OpenVPN IPv4 tunnel")
        }
    }

    private fun inlineFile(data: String) = "[[INLINE]]$data"

    private class SplitTunnelAppsOpenVpnConfigurator(private val profile: VpnProfile) : SplitTunnelAppsConfigurator {
        override fun includeApplications(packageNames: List<String>) {
            profile.mAllowedAppsVpn += packageNames
            profile.mAllowedAppsVpnAreDisallowed = false
        }

        override fun excludeApplications(packageNames: List<String>) {
            profile.mAllowedAppsVpnAreDisallowed = true
            profile.mAllowedAppsVpn += packageNames
        }
    }

    companion object {

        const val TLS_AUTH_KEY =
            "[[INLINE]]# 2048 bit OpenVPN static key\n" +
            "-----BEGIN OpenVPN Static key V1-----\n" +
            Constants.TLS_AUTH_KEY_HEX +
            "-----END OpenVPN Static key V1-----"
    }
}
