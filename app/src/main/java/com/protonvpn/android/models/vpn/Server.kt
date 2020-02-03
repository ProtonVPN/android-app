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
package com.protonvpn.android.models.vpn

import android.content.Context
import androidx.annotation.ColorRes
import com.fasterxml.jackson.annotation.JsonProperty
import com.protonvpn.android.R
import com.protonvpn.android.components.Listable
import com.protonvpn.android.components.Markable
import com.protonvpn.android.components.UnavailableException
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.login.Session
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.User
import de.blinkt.openpvpn.core.Connection
import java.io.Serializable
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import org.strongswan.android.data.VpnProfile
import org.strongswan.android.data.VpnType

class Server(
    @param:JsonProperty(value = "ID", required = true) val serverId: String,
    @param:JsonProperty(value = "EntryCountry") val entryCountry: String?,
    @param:JsonProperty(value = "ExitCountry") val exitCountry: String,
    @param:JsonProperty(value = "Name", required = true) val serverName: String,
    @param:JsonProperty(value = "Servers", required = true) private val connectingDomains: List<ConnectingDomain>,
    @param:JsonProperty(value = "Domain", required = true) val domain: String,
    @param:JsonProperty(value = "Load", required = true) val load: String,
    @param:JsonProperty(value = "Tier", required = true) val tier: Int,
    @param:JsonProperty(value = "Region", required = true) val region: String?,
    @param:JsonProperty(value = "City", required = true) val city: String?,
    @param:JsonProperty(value = "Features", required = true) private val features: Int,
    @param:JsonProperty(value = "Location", required = true) private val location: Location,
    @param:JsonProperty(value = "Score", required = true) val score: Float,
    @param:JsonProperty(value = "Status", required = true) val isOnline: Boolean
) : VpnProfile(), Markable, Serializable, Listable {

    private lateinit var connectingDomain: ConnectingDomain
    val keywords: MutableList<String>
    private val translatedCoordinates: TranslatedCoordinates = TranslatedCoordinates(exitCountry)
    private var bestScore = false
    var selectedAsFastest = false

    val entryCountryCoordinates: TranslatedCoordinates? =
            if (entryCountry != null) TranslatedCoordinates(this.entryCountry) else null

    val isFreeServer: Boolean
        get() = domain.contains("-free")

    val flag: String
        get() = if (exitCountry == "GB") "UK" else exitCountry

    val isBasicServer: Boolean
        get() = tier == 1

    val isPlusServer: Boolean
        get() = tier == 2

    val loadColor: Int
        @ColorRes
        get() = when {
            Integer.parseInt(load) < 50 -> R.color.colorAccent
            Integer.parseInt(load) < 90 -> R.color.yellow
            else -> R.color.dimmedRed
        }

    val isSecureCoreServer: Boolean
        get() = features and 1 == 1

    val serverNumber: Int
        get() {
            val name = serverName
            val pattern = Pattern.compile("#(\\d+(\\d+)?)")
            val m = pattern.matcher(name)
            return if (m.find()) {
                Integer.valueOf(m.group(1))
            } else {
                1
            }
        }

    val ipAddress: String
        get() = connectingDomain.exitIp

    private val secureCoreServerNaming: String
        get() = CountryTools.getFullName(entryCountry) + " >> " + CountryTools.getFullName(
                exitCountry)

    init {
        this.bestScore = false
        this.keywords = ArrayList()
        initKeywords(features)
        name = if (isSecureCoreServer) secureCoreServerNaming else CountryTools.getFullName(flag)
    }

    private fun initKeywords(features: Int) {
        if (features and 4 == 4) {
            keywords.add("p2p")
        }
        if (features and 2 == 2) {
            keywords.add("tor")
        }
    }

    fun getDisplayName(): String {
        return if (isSecureCoreServer) secureCoreServerNaming else CountryTools.getFullName(flag)
    }

    @Throws(UnavailableException::class)
    fun validateForConnection(sessionList: List<Session>, userData: UserData) {
        val availableDomains = if (sessionList.isNotEmpty())
            connectingDomains.filter { domain -> sessionList.all { session -> domain.exitIp != session.exitIp } } else connectingDomains

        if (availableDomains.isEmpty()) {
            throw UnavailableException()
        } else {
            initConnectionSettings(availableDomains, userData)
        }
    }

    private fun initConnectionSettings(connectingDomains: List<ConnectingDomain>, userData: UserData) {
        mtu = userData.mtuSize
        this.connectingDomain = connectingDomains[Random().nextInt(connectingDomains.size)]
        vpnType = VpnType.IKEV2_EAP
        id = 1

        splitTunneling = SPLIT_TUNNELING_BLOCK_IPV6
        flags = FLAGS_SUPPRESS_CERT_REQS
        gateway = if (isSecureCoreServer) connectingDomain.entryIp else connectingDomain.exitIp
        remoteId = this.connectingDomain.entryDomain

        setExcludedSubnets(if (userData.useSplitTunneling) userData.splitTunnelIpAddresses else ArrayList())
        setSelectedApps(if (userData.useSplitTunneling) userData.splitTunnelApps else ArrayList())
    }

    fun reinitFromOldProfile(oldServer: Server): Server {
        this.connectingDomain = oldServer.connectingDomain
        gateway =
                if (oldServer.isSecureCoreServer) connectingDomain.entryIp else connectingDomain.exitIp
        return this
    }

    fun prepareForConnection(userData: UserData): Server {
        initConnectionSettings(connectingDomains, userData)
        return this
    }

    fun notReadyForConnection(): Boolean {
        return gateway.isNullOrEmpty()
    }

    override fun getCoordinates(): TranslatedCoordinates {
        return translatedCoordinates
    }

    override fun isSecureCoreMarker(): Boolean {
        return false
    }

    override fun getMarkerText(): String {
        return secureCoreServerNaming
    }

    override fun getConnectableServers(): List<Server> {
        return listOf(this)
    }

    fun hasBestScore(): Boolean {
        return bestScore
    }

    fun setBestScore(bestScore: Boolean) {
        this.bestScore = bestScore
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Server) {
            return false
        }

        val server = other as Server?

        return bestScore == server!!.bestScore && domain == server.domain
    }

    override fun toString(): String {
        return "$domain $entryCountry"
    }

    override fun hashCode(): Int {
        var result = domain.hashCode()
        result = 31 * result + connectingDomains.hashCode()
        result = 31 * result + if (bestScore) 1 else 0
        return result
    }

    override fun getLabel(context: Context): String {
        return if (isSecureCoreServer) CountryTools.getFullName(entryCountry) else serverName
    }

    fun openVPNProfile(context: Context, userData: UserData, transmissionProtocol: String): de.blinkt.openpvpn.VpnProfile {
        val profileToConnect: de.blinkt.openpvpn.VpnProfile = de.blinkt.openpvpn.VpnProfile(getLabel(context))
        val connectingDomain = connectingDomains[Random().nextInt(connectingDomains.size)]

        profileToConnect.mAuthenticationType = de.blinkt.openpvpn.VpnProfile.TYPE_USERPASS
        profileToConnect.mCaFilename = "[[INLINE]]-----BEGIN CERTIFICATE-----\n" +
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

        profileToConnect.mTLSAuthFilename = "[[INLINE]]# 2048 bit OpenVPN static key\n" +
                "-----BEGIN OpenVPN Static key V1-----\n" +
                "6acef03f62675b4b1bbd03e53b187727\n" +
                "423cea742242106cb2916a8a4c829756\n" +
                "3d22c7e5cef430b1103c6f66eb1fc5b3\n" +
                "75a672f158e2e2e936c3faa48b035a6d\n" +
                "e17beaac23b5f03b10b868d53d03521d\n" +
                "8ba115059da777a60cbfd7b2c9c57472\n" +
                "78a15b8f6e68a3ef7fd583ec9f398c8b\n" +
                "d4735dab40cbd1e3c62a822e97489186\n" +
                "c30a0b48c7c38ea32ceb056d3fa5a710\n" +
                "e10ccc7a0ddb363b08c3d2777a3395e1\n" +
                "0c0b6080f56309192ab5aacd4b45f55d\n" +
                "a61fc77af39bd81a19218a79762c3386\n" +
                "2df55785075f37d8c71dc8a42097ee43\n" +
                "344739a0dd48d03025b0450cf1fb5e8c\n" +
                "aeb893d9a96d1f15519bb3c4dcb40ee3\n" +
                "16672ea16c012664f8a9f11255518deb\n" +
                "-----END OpenVPN Static key V1-----"
        profileToConnect.mTLSAuthDirection = "1"
        profileToConnect.mAuth = "SHA512"
        profileToConnect.mCipher = "AES-256-CBC"
        profileToConnect.mUsername = userData.vpnInfoResponse.vpnUserName
        profileToConnect.mUseTLSAuth = true
        profileToConnect.mTunMtu = userData.mtuSize
        profileToConnect.mExpectTLSCert = true
        profileToConnect.mX509AuthType = de.blinkt.openpvpn.VpnProfile.X509_VERIFY_TLSREMOTE_SAN
        profileToConnect.mCheckRemoteCN = true
        profileToConnect.mRemoteCN = connectingDomain.entryDomain
        profileToConnect.mAllowLocalLAN = userData.bypassLocalTraffic()
        val conn = Connection()

        if (userData.useSplitTunneling) {
            profileToConnect.mAllowedAppsVpn = HashSet<String>(userData.splitTunnelApps)
        }
        conn.mServerName =
                if (isSecureCoreServer) connectingDomain.entryIp else connectingDomain.exitIp
        conn.mUseUdp = transmissionProtocol == TransmissionProtocol.UDP.toString()

        val ports =
                if (conn.mUseUdp) User.getOpenVPNPorts().udpPorts else User.getOpenVPNPorts().tcpPorts
        conn.mServerPort = ports.random().toString()
        conn.mCustomConfiguration = ""
        profileToConnect.mConnections[0] = conn
        profileToConnect.mPassword = userData.vpnInfoResponse.password
        return profileToConnect
    }
}
