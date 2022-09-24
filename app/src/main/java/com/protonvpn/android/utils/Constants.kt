/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.utils

import android.app.Activity
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.android.utils.AndroidUtils.isTV

object Constants {

    const val MAX_LOG_SIZE = 200 * 1024.toLong()
    const val NOTIFICATION_ID = 6
    const val NOTIFICATION_INFO_ID = 7
    const val NOTIFICATION_GUESTHOLE_ID = 8
    const val TV_SIGNUP_LINK = "protonvpn.com/signup"
    const val TV_UPGRADE_LINK = "account.protonvpn.com/account"
    const val PRIMARY_VPN_API_URL = "https://" + BuildConfig.API_DOMAIN + "/"
    const val ALTERNATIVE_ROUTING_LEARN_URL =
            "https://protonmail.com/blog/anti-censorship-alternative-routing"
    const val ACCOUNT_LOGIN_URL = "https://account.protonvpn.com/login"
    const val URL_SUPPORT_PERMISSIONS =
            "https://protonvpn.com/support/android-vpn-permissions-problem"
    const val FORCE_UPDATE_URL = "https://protonvpn.com/support/vpn-update/"
    const val URL_SUPPORT_ASSIGN_VPN_CONNECTION = "https://protonvpn.com/support/assign-vpn-connection"
    const val PROTON_URL_UTM_SOURCE = "androidvpn"
    const val MINIMUM_MAINTENANCE_CHECK_MINUTES = 5L
    const val DEFAULT_MAINTENANCE_CHECK_MINUTES = 30L
    const val VPN_INFO_REFRESH_INTERVAL_MINUTES = 3
    const val WIREGUARD_TUNNEL_NAME = "ProtonTunnel"
    const val SECONDARY_PROCESS_TAG = "SecondaryProcess"
    const val REASON_GUEST_HOLE = "Guest hole"

    val CLIENT_ID: String
    val VPN_USERNAME_PRODUCT_SUFFIX: String
    val MAIN_ACTIVITY_CLASS: Class<out Activity>

    const val SMART_ROUTING_INFO_URL = "https://duckduckgo.com/?q=site%3Aprotonvpn.com+smart+routing+support&t=h_&ia=web"
    const val STREAMING_INFO_URL = "https://duckduckgo.com/?q=site%3Aprotonvpn.com+streaming+guide&t=h_&ia=web"
    const val P2P_INFO_URL = "https://duckduckgo.com/?q=site%3Aprotonvpn.com+share+files+securely+p2p+guide&t=h_&ia=web"
    const val TOR_INFO_URL = "https://duckduckgo.com/?q=site%3Aprotonvpn.com+tor+guide&t=h_&ia=web"
    const val SERVER_LOAD_INFO_URL = "https://duckduckgo.com/?q=site%3Aprotonvpn.com+server+load+support&t=h_&ia=web"
    const val VPN_ACCELERATOR_INFO_URL = "https://protonvpn.com/support/how-to-use-vpn-accelerator"
    const val SAFE_MODE_INFO_URL = "https://protonvpn.com/support/non-standard-ports"
    const val SECURE_CORE_INFO_URL = "https://protonvpn.com/support/secure-core-vpn/"
    const val MODERATE_NAT_INFO_URL = "https://protonvpn.com/support/moderate-nat"
    const val NEW_LOOK_INFO_URL = "https://protonvpn.com/blog/updated-proton-vpn"

    const val MAX_CONNECTIONS_IN_PLUS_PLAN = 10

    init {
        if (ProtonApplication.getAppContext().isTV()) {
            CLIENT_ID = "android_tv-vpn"
            VPN_USERNAME_PRODUCT_SUFFIX = "+pt"
            MAIN_ACTIVITY_CLASS = TvMainActivity::class.java
        } else {
            CLIENT_ID = "android-vpn"
            VPN_USERNAME_PRODUCT_SUFFIX = "+pa"
            MAIN_ACTIVITY_CLASS = MobileMainActivity::class.java
        }
    }

    const val TLS_AUTH_KEY_HEX =
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
            "16672ea16c012664f8a9f11255518deb\n"

    const val VPN_ROOT_CERTS =
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

    const val LOCAL_AGENT_ADDRESS = "10.2.0.1:65432"

    // TV
    const val TV_LOGIN_URL = "protonvpn.com/tv"
}
