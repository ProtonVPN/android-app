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
package com.protonvpn.android.models.vpn

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.ProtocolSelection
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.Connection

class ConnectionParamsOpenVpn(
    profile: Profile,
    server: Server,
    connectingDomain: ConnectingDomain,
    transmission: TransmissionProtocol,
    private val port: Int
) : ConnectionParams(profile, server, connectingDomain, VpnProtocol.OpenVPN, transmission), java.io.Serializable {

    override val info get() = "${super.info} $transmissionProtocol port: $port"

    fun openVpnProfile(
        userData: UserData,
        clientCertificate: CertificateData?
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
        mTLSAuthDirection = "1"
        mAuth = "SHA512"
        mCipher = "AES-256-CBC"
        mUseTLSAuth = true
        mTunMtu = 1500
        mMssFix = userData.mtuSize - 40
        mExpectTLSCert = true
        mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_SAN
        mCheckRemoteCN = true
        mRemoteCN = connectingDomain!!.entryDomain
        mPersistTun = true
        mAllowLocalLAN = userData.shouldBypassLocalTraffic()
        if (userData.useSplitTunneling && userData.splitTunnelIpAddresses.isNotEmpty()) {
            mUseDefaultRoute = false
            mExcludedRoutes = userData.splitTunnelIpAddresses.joinToString(" ")
        }
        mConnections[0] = Connection().apply {
            if (userData.useSplitTunneling)
                mAllowedAppsVpn = HashSet<String>(userData.splitTunnelApps)
            mServerName = connectingDomain.getEntryIp(
                ProtocolSelection(VpnProtocol.OpenVPN, transmissionProtocol))
            mUseUdp = transmissionProtocol == TransmissionProtocol.UDP
            mServerPort = port.toString()
            mCustomConfiguration = ""
        }
    }

    override fun hasSameProtocolParams(other: ConnectionParams) =
        other is ConnectionParamsOpenVpn && other.transmissionProtocol == transmissionProtocol && other.port == port

    private fun inlineFile(data: String) = "[[INLINE]]$data"

    companion object {

        const val TLS_AUTH_KEY =
            "[[INLINE]]# 2048 bit OpenVPN static key\n" +
            "-----BEGIN OpenVPN Static key V1-----\n" +
            Constants.TLS_AUTH_KEY_HEX +
            "-----END OpenVPN Static key V1-----"
    }
}
