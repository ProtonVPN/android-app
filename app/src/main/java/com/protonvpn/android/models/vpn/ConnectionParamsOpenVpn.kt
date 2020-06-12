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

import android.content.Context
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.Constants
import de.blinkt.openpvpn.VpnProfile
import de.blinkt.openpvpn.core.Connection
import java.io.Serializable

class ConnectionParamsOpenVpn(
    profile: Profile,
    server: Server,
    connectingDomain: ConnectingDomain,
    private val transmissionProtocol: TransmissionProtocol,
    private val port: Int
) : ConnectionParams(profile, server, connectingDomain, VpnProtocol.OpenVPN), Serializable {

    override val info get() = "${super.info} $transmissionProtocol port=$port"

    fun openVpnProfile(
        context: Context,
        userData: UserData,
        appConfig: AppConfig
    ) = VpnProfile(server.getLabel(context)).apply {
        mAuthenticationType = VpnProfile.TYPE_USERPASS
        mCaFilename = ROOT_CERTS
        mTLSAuthFilename = TLS_AUTH_KEY
        mTLSAuthDirection = "1"
        mAuth = "SHA512"
        mCipher = "AES-256-CBC"
        mUsername = userData.vpnUserName + profile.getNetShieldProtocol(userData, appConfig).protocolString
        mUseTLSAuth = true
        mTunMtu = userData.mtuSize
        mExpectTLSCert = true
        mX509AuthType = VpnProfile.X509_VERIFY_TLSREMOTE_SAN
        mCheckRemoteCN = true
        mRemoteCN = connectingDomain.entryDomain
        mAllowLocalLAN = userData.bypassLocalTraffic()
        if (userData.useSplitTunneling && userData.splitTunnelIpAddresses.isNotEmpty()) {
            mUseDefaultRoute = false
            mExcludedRoutes = userData.splitTunnelIpAddresses.joinToString(" ")
        }
        mConnections[0] = Connection().apply {
            if (userData.useSplitTunneling)
                mAllowedAppsVpn = HashSet<String>(userData.splitTunnelApps)
            mServerName = if (server.isSecureCoreServer)
                connectingDomain.entryIp else connectingDomain.getExitIP()
            mUseUdp = transmissionProtocol == TransmissionProtocol.UDP
            mServerPort = port.toString()
            mCustomConfiguration = ""
        }
        mPassword = userData.vpnPassword
    }

    companion object {

        const val TLS_AUTH_KEY =
            "[[INLINE]]# 2048 bit OpenVPN static key\n" +
            "-----BEGIN OpenVPN Static key V1-----\n" +
            Constants.TLS_AUTH_KEY_HEX +
            "-----END OpenVPN Static key V1-----"

        private const val ROOT_CERTS =
            "[[INLINE]]-----BEGIN CERTIFICATE-----\n" +
            "MIIFozCCA4ugAwIBAgIBATANBgkqhkiG9w0BAQ0FADBAMQswCQYDVQQGEwJDSDEV\n" +
            "MBMGA1UEChMMUHJvdG9uVlBOIEFHMRowGAYDVQQDExFQcm90b25WUE4gUm9vdCBD\n" +
            "QTAeFw0xNzAyMTUxNDM4MDBaFw0yNzAyMTUxNDM4MDBaMEAxCzAJBgNVBAYTAkNI\n" +
            "MRUwEwYDVQQKEwxQcm90b25WUE4gQUcxGjAYBgNVBAMTEVByb3RvblZQTiBSb290\n" +
            "IENBMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt+BsSsZg7+AuqTq7\n" +
            "vDbPzfygtl9f8fLJqO4amsyOXlI7pquL5IsEZhpWyJIIvYybqS4s1/T7BbvHPLVE\n" +
            "wlrq8A5DBIXcfuXrBbKoYkmpICGc2u1KYVGOZ9A+PH9z4Tr6OXFfXRnsbZToie8t\n" +
            "2Xjv/dZDdUDAqeW89I/mXg3k5x08m2nfGCQDm4gCanN1r5MT7ge56z0MkY3FFGCO\n" +
            "qRwspIEUzu1ZqGSTkG1eQiOYIrdOF5cc7n2APyvBIcfvp/W3cpTOEmEBJ7/14RnX\n" +
            "nHo0fcx61Inx/6ZxzKkW8BMdGGQF3tF6u2M0FjVN0lLH9S0ul1TgoOS56yEJ34hr\n" +
            "JSRTqHuar3t/xdCbKFZjyXFZFNsXVvgJu34CNLrHHTGJj9jiUfFnxWQYMo9UNUd4\n" +
            "a3PPG1HnbG7LAjlvj5JlJ5aqO5gshdnqb9uIQeR2CdzcCJgklwRGCyDT1pm7eoiv\n" +
            "WV19YBd81vKulLzgPavu3kRRe83yl29It2hwQ9FMs5w6ZV/X6ciTKo3etkX9nBD9\n" +
            "ZzJPsGQsBUy7CzO1jK4W01+u3ItmQS+1s4xtcFxdFY8o/q1zoqBlxpe5MQIWN6Qa\n" +
            "lryiET74gMHE/S5WrPlsq/gehxsdgc6GDUXG4dk8vn6OUMa6wb5wRO3VXGEc67IY\n" +
            "m4mDFTYiPvLaFOxtndlUWuCruKcCAwEAAaOBpzCBpDAMBgNVHRMEBTADAQH/MB0G\n" +
            "A1UdDgQWBBSDkIaYhLVZTwyLNTetNB2qV0gkVDBoBgNVHSMEYTBfgBSDkIaYhLVZ\n" +
            "TwyLNTetNB2qV0gkVKFEpEIwQDELMAkGA1UEBhMCQ0gxFTATBgNVBAoTDFByb3Rv\n" +
            "blZQTiBBRzEaMBgGA1UEAxMRUHJvdG9uVlBOIFJvb3QgQ0GCAQEwCwYDVR0PBAQD\n" +
            "AgEGMA0GCSqGSIb3DQEBDQUAA4ICAQCYr7LpvnfZXBCxVIVc2ea1fjxQ6vkTj0zM\n" +
            "htFs3qfeXpMRf+g1NAh4vv1UIwLsczilMt87SjpJ25pZPyS3O+/VlI9ceZMvtGXd\n" +
            "MGfXhTDp//zRoL1cbzSHee9tQlmEm1tKFxB0wfWd/inGRjZxpJCTQh8oc7CTziHZ\n" +
            "ufS+Jkfpc4Rasr31fl7mHhJahF1j/ka/OOWmFbiHBNjzmNWPQInJm+0ygFqij5qs\n" +
            "51OEvubR8yh5Mdq4TNuWhFuTxpqoJ87VKaSOx/Aefca44Etwcj4gHb7LThidw/ky\n" +
            "zysZiWjyrbfX/31RX7QanKiMk2RDtgZaWi/lMfsl5O+6E2lJ1vo4xv9pW8225B5X\n" +
            "eAeXHCfjV/vrrCFqeCprNF6a3Tn/LX6VNy3jbeC+167QagBOaoDA01XPOx7Odhsb\n" +
            "Gd7cJ5VkgyycZgLnT9zrChgwjx59JQosFEG1DsaAgHfpEl/N3YPJh68N7fwN41Cj\n" +
            "zsk39v6iZdfuet/sP7oiP5/gLmA/CIPNhdIYxaojbLjFPkftVjVPn49RqwqzJJPR\n" +
            "N8BOyb94yhQ7KO4F3IcLT/y/dsWitY0ZH4lCnAVV/v2YjWAWS3OWyC8BFx/Jmc3W\n" +
            "DK/yPwECUcPgHIeXiRjHnJt0Zcm23O2Q3RphpU+1SO3XixsXpOVOYP6rJIXW9bMZ\n" +
            "A1gTTlpi7A==\n" +
            "-----END CERTIFICATE-----\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIFszCCA5ugAwIBAgIBBjANBgkqhkiG9w0BAQ0FADBAMQswCQYDVQQGEwJDSDEV\n" +
            "MBMGA1UEChMMUHJvdG9uVlBOIEFHMRowGAYDVQQDExFQcm90b25WUE4gUm9vdCBD\n" +
            "QTAeFw0xNzAyMTUxNTE3MDBaFw0yNzAyMTUxNDM4MDBaMEoxCzAJBgNVBAYTAkNI\n" +
            "MRUwEwYDVQQKEwxQcm90b25WUE4gQUcxJDAiBgNVBAMTG1Byb3RvblZQTiBJbnRl\n" +
            "cm1lZGlhdGUgQ0EgMTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBANv3\n" +
            "uwQMFjYOx74taxadhczLbjCTuT73jMz09EqFNv7O7UesXfYJ6kQgYV9YyE86znP4\n" +
            "xbsswNUZYh+XdZUpOoP6Zu3tR/iiYiuzi6jVYrJ66G89nPqS2mm5dn8Fbb8CRWkJ\n" +
            "ygm8AdlYkDwYNldhDUrERlQdCRDGsYYg/98dded+5pXnSG8Y/+iuLM6/YYhkUVQe\n" +
            "Cfq1L6XguSwu8CuvJjIjjE1PptUHa3Hc3tGziVydltKynxWlqb1dJqinGKiBZvYn\n" +
            "oiV4motpFYwhc3Wd09JLPzeobhD2IAZ2evSatikMWDingEv1EJXpI+V/E2AK3xHK\n" +
            "Skhw+YZx99tNxCiOu3U5BFAreZR3j2YnZzX1nEv9p02IGaWzzYJPNED0zSO2w07u\n" +
            "thSmKcxA39VTvs91lptbcV7VTxoJY0SErHIeVS3Scrnr7WvoOTuu3M3SCRqe6oI9\n" +
            "oJZMOdfNsceBdvG+qlpOFICoBjO53W4BK8KahzTd/PWlBRiVJ3UVv8xXwUDA+o98\n" +
            "34DXVAobaAHXQtM9jNobqT98FXhZktjOQEA2UORL581ZPxfKeHLRcgWJ5dmPsDBG\n" +
            "y/L6/qW/yrm6DUDAdN5+q41+gSNEjNBjLBJQFUmDk3l6Qxiu0uEDQ98oFvGHk5US\n" +
            "2Kbj0OAq1RpiDjHci/536yua9rTC+cxekTM2asdXAgMBAAGjga0wgaowEgYDVR0T\n" +
            "AQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUJbaTWcIB4t5ETvvhUy5/yQqqGjMwaAYD\n" +
            "VR0jBGEwX4AUg5CGmIS1WU8MizU3rTQdqldIJFShRKRCMEAxCzAJBgNVBAYTAkNI\n" +
            "MRUwEwYDVQQKEwxQcm90b25WUE4gQUcxGjAYBgNVBAMTEVByb3RvblZQTiBSb290\n" +
            "IENBggEBMAsGA1UdDwQEAwIBBjANBgkqhkiG9w0BAQ0FAAOCAgEAAgZ/BIVl+DcK\n" +
            "OTVJJBy+RZ1E8os11gFaMKy12lAT1XEXDqLAnitvVyQgG5lPZKFQ2wzUR/TCrYKT\n" +
            "SUZWdYaJIXkRWAU0aCDZ2I81T0OMpg9aS7xdxgHCGWOwwes8GhjtvQad9GJ8mUZH\n" +
            "GyzfMaGG6fAZrgHnlOb4OIoqhBWYla6D2bpvbKgGkMo5NLAaX/7+U0HcxjjSS9vm\n" +
            "/3XHTZU4q77pn+lhPWncajnVyMtm1mIZxMioyckR4+scyZse0mYJS6xli/7crH7j\n" +
            "qScX7c5sWcaN4J63a3+x3uGvzOXjCyoDl9IaeqnxQpi8yc0nsWxIyDalR3uRQ9tJ\n" +
            "7l/eRxJZ/1Pzz2LRHSQZuqN2ZReWVNTqJ42af8cWWH0fDOEt2468GLeSm08Hvyz0\n" +
            "lRjn7Tf5hxOJSw4/3oGihvzuTdquJMOi62kThbp7DS3mMaZsfbmDoU3oNDv91bvL\n" +
            "57z8wm7yRcGEoMsUNnrOZ4SU8dG/souvJM1BDStMLprFEgUbHEY5MjSR4/PLR6j9\n" +
            "3NZgocfnfk80nBvNtgWVHxW019nuT93WL0/5L5g4UVm0Ay1V6pNkGZCmgNUBaRY4\n" +
            "2JLzyY8p48OKapR5GnedLTJXJVbdd9GUNzIzm4iVITDH3p/u1g69dITCNXTO9EO5\n" +
            "sGEYLNPbV49XBnVAm1tUWuoByZAjoWs=\n" +
            "-----END CERTIFICATE-----"
    }
}
