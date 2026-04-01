/*
 * Copyright (c) 2019 Proton AG
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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.recents.data.DefaultConnection
import java.util.concurrent.TimeUnit

object Constants {
    val APP_NOT_IN_USE_DELAY_MS = TimeUnit.DAYS.toMillis(2)

    const val NOTIFICATION_ID = 6
    const val NOTIFICATION_INFO_ID = 7
    const val NOTIFICATION_GUESTHOLE_ID = 8
    const val NOTIFICATION_STREAMING_BLOCKED_ID = 9
    const val NOTIFICATION_AUTOLOGIN_ID = 10

    const val TV_SIGNUP_LINK = "protonvpn.com/signup"
    const val TV_UPGRADE_LINK = "account.protonvpn.com/account"
    const val ALTERNATIVE_ROUTING_LEARN_URL =
            "https://protonmail.com/blog/anti-censorship-alternative-routing"
    const val URL_ACCOUNT_LOGIN = "https://account.protonvpn.com/login"
    const val URL_ACCOUNT_DELETE = "https://account.proton.me/u/0/vpn/account-password"
    const val URL_SUPPORT = "https://protonvpn.com/support"
    const val URL_SUPPORT_PERMISSIONS =
            "https://protonvpn.com/support/android-vpn-permissions-problem"
    const val FORCE_UPDATE_URL = "https://protonvpn.com/support/vpn-update/"
    const val URL_SUPPORT_ASSIGN_VPN_CONNECTION = "https://protonvpn.com/support/assign-vpn-connection"
    const val URL_NETSHIELD_LEARN_MORE = "https://protonvpn.com/support/netshield/"
    const val URL_NETSHIELD_CUSTOM_DNS_LEARN_MORE = "https://protonvpn.com/support/custom-dns#netshield"
    const val URL_NETSHIELD_PRIVATE_DNS_LEARN_MORE = "http://protonvpn.com/support/custom-dns#netshield"
    const val URL_LOAD_LEARN_MORE = "https://protonvpn.com/support/server-load-percentages-and-colors-explained"
    const val URL_SPEED_LEARN_MORE = "https://protonvpn.com/support/increase-vpn-speeds/"
    const val URL_PROTOCOL_LEARN_MORE = "https://protonvpn.com/support/how-to-change-vpn-protocols/"
    const val URL_TOR_LEARN_MORE = "https://protonvpn.com/support/tor-vpn/"
    const val URL_P2P_LEARN_MORE = "https://protonvpn.com/support/p2p-vpn-redirection/"
    const val URL_STREAMING_LEARN_MORE = "https://protonvpn.com/support/streaming-troubleshooting-guide/"
    const val URL_SMART_ROUTING_LEARN_MORE = "https://protonvpn.com/support/how-smart-routing-works/"
    const val URL_PROFILES_LEARN_MORE = "https://protonvpn.com/support/connection-profiles/"
    const val URL_IP_ADDRESS_LEARN_MORE = "https://protonvpn.com/blog/what-is-an-ip-address"
    const val URL_IPV6_ADDRESS_LEARN_MORE = "https://protonvpn.com/blog/what-is-an-ip-address#ipv6"
    const val URL_CUSTOM_DNS_LEARN_MORE = "https://protonvpn.com/support/custom-dns#android"
    const val URL_CUSTOM_DNS_PRIVATE_DNS_LEARN_MORE = "http://protonvpn.com/support/custom-dns#privatevscustom"
    const val URL_ENABLE_VPN_CONNECTION = "https://protonvpn.com/support/enable-vpn-connection"
    const val PROTON_URL_UTM_SOURCE = "androidvpn"
    const val MINIMUM_MAINTENANCE_CHECK_MINUTES = 5L
    const val DEFAULT_MAINTENANCE_CHECK_MINUTES = 40L
    const val VPN_INFO_REFRESH_INTERVAL_MINUTES = 3L
    const val WIREGUARD_TUNNEL_NAME = "ProtonTunnel"
    const val SECONDARY_PROCESS_TAG = "SecondaryProcess"
    const val DISTRIBUTION_AMAZON = "amazon"
    val DEFAULT_CONNECTION = DefaultConnection.FastestConnection

    const val MOBILE_CLIENT_ID: String = "android-vpn"
    const val TV_CLIENT_ID: String = "android_tv-vpn"
    val DEFAULT_NETSHIELD_AFTER_UPGRADE = NetShieldProtocol.ENABLED_EXTENDED

    const val VPN_ACCELERATOR_INFO_URL = "https://protonvpn.com/support/how-to-use-vpn-accelerator"
    const val SECURE_CORE_INFO_URL = "https://protonvpn.com/support/secure-core-vpn/"
    const val MODERATE_NAT_INFO_URL = "https://protonvpn.com/support/moderate-nat"
    const val TELEMETRY_INFO_URL = "https://protonvpn.com/support/share-usage-statistics"
    const val SPLIT_TUNNELING_INFO_URL = "https://protonvpn.com/support/protonvpn-split-tunneling"
    const val NO_LOGS_AUDIT_URL = "https://protonvpn.com/blog/no-logs-audit/"
    const val PROTOCOL_INFO_URL = "https://protonvpn.com/blog/whats-the-best-vpn-protocol/"
    const val CHANGE_ICON_URL = "https://protonvpn.com/support/hide-app-icon"
    const val KILL_SWITCH_INFO_URL = "https://protonvpn.com/support/what-is-kill-switch/"

    const val MAX_CONNECTIONS_IN_PLUS_PLAN = 10
    const val SERVER_SPEED_UP_TO_GBPS = 10
    const val FALLBACK_SERVER_COUNT = 6500
    const val FALLBACK_COUNTRY_COUNT = 110

    // Note: ideally these should come from dynamic plans.
    const val UNLIMITED_PLAN_VPN_CONNECTIONS = MAX_CONNECTIONS_IN_PLUS_PLAN
    const val UNLIMITED_PLAN_MAIL_ADDRESSES = 15
    const val UNLIMITED_PLAN_MAIL_DOMAINS = 3
    const val UNLIMITED_PLAN_MAIL_ATTACHMENT_MBS = 25
    const val UNLIMITED_PLAN_CALENDARS = 25
    const val UNLIMITED_PLAN_DRIVE_STORAGE_GB = 500
    const val UNLIMITED_PLAN_PASS_VAULTS = 50
    const val UNLIMITED_PLAN_PASS_USERS = 10

    private const val DEFAULT_VPN_ROOT_CERTS =
        "[[INLINE]]\n"+
        "-----BEGIN CERTIFICATE-----\n" + // Root
        "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\n" +
        "EA==\n" +
        "-----END CERTIFICATE-----\n" +
        "-----BEGIN CERTIFICATE-----\n" + // Intermediate
        "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\n" +
        "-----END CERTIFICATE-----\n"

    val VPN_ROOT_CERTS = BuildConfig.VPN_SERVER_ROOT_CERT ?: DEFAULT_VPN_ROOT_CERTS

    const val VPN_CLIENT_IP = "10.2.0.2"
    const val VPN_SERVER_IP = "10.2.0.1"

    const val VPN_CLIENT_IP_V6 = "2a07:b944::2:2"
    const val VPN_SERVER_IP_V6 = "2a07:b944::2:1"

    const val PROTON_DNS_LOCAL_IP = VPN_SERVER_IP
    const val PROTON_DNS_LOCAL_IP_V6 = VPN_SERVER_IP_V6

    const val LOCAL_AGENT_IP = VPN_SERVER_IP
    const val LOCAL_AGENT_ADDRESS = "$LOCAL_AGENT_IP:65432"

    // Plans
    const val CURRENT_PLUS_PLAN = "vpn2022"
    const val CURRENT_PLUS_PLAN_LABEL = "VPN Plus"
    const val CURRENT_BUNDLE_PLAN = "bundle2022"

    // TV
    const val TV_LOGIN_URL = "protonvpn.com/tv"
}
