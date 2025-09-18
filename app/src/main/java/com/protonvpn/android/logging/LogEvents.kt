/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.logging

enum class LogCategory(private val categoryName: String) {
    API("API"),
    APP("APP"),
    APP_REVIEW("APP.REVIEW"),
    APP_UPDATE("APP.UPDATE"),
    APP_PERIODIC("APP.PERIODIC"),
    CONN("CONN"),
    CONN_CONNECT("CONN.CONNECT"),
    CONN_DISCONNECT("CONN.DISCONNECT"),
    CONN_GUEST_HOLE("CONN.GUEST_HOLE"),
    CONN_SERVER_SWITCH("CONN.SERVER_SWITCH"),
    CONN_WIREGUARD("CONN.WIREGUARD"),
    GO_ERROR("GO.ERROR"),
    HV("HV"),
    LOCAL_AGENT("LOCAL_AGENT"),
    MANAGED_CONFIG("MANAGED_CONFIG"),
    NETWORK("NETWORK"),
    OS("OS"),
    OS_POWER("OS.POWER"),
    PROFILES("PROFILES"),
    PROMO("PROMO"),
    PROTOCOL("PROTOCOL"),
    SECURE_STORE("SECURE_STORE"),
    SETTINGS("SETTINGS"),
    TELEMETRY("TELEMETRY"),
    UI("UI"),
    USER("USER"),
    USER_CERT("USER.CERT"),
    USER_PLAN("USER.PLAN"),
    WIDGET("WIDGET");

    fun toLog() = categoryName
}

class LogEventType(
    val category: LogCategory,
    val name: String,
    val level: LogLevel
)

// The API events get the "Log" infix to avoid confusion with types like ApiResult.
val ApiLogRequest = LogEventType(LogCategory.API, "REQUEST", LogLevel.INFO)
val ApiLogResponse = LogEventType(LogCategory.API, "RESPONSE", LogLevel.INFO)
val ApiLogError = LogEventType(LogCategory.API, "ERROR", LogLevel.WARN)

val AppCrash = LogEventType(LogCategory.APP, "CRASH", LogLevel.FATAL)
val AppDNS = LogEventType(LogCategory.APP, "DNS", LogLevel.INFO)
@JvmField
val AppProcessStart = LogEventType(LogCategory.APP, "PROCESS_START", LogLevel.INFO)
@JvmField
val AppUpdateUpdated = LogEventType(LogCategory.APP_UPDATE, "UPDATED", LogLevel.INFO)

val ConnCurrentState = LogEventType(LogCategory.CONN, "CURRENT", LogLevel.INFO)
val ConnStateChanged = LogEventType(LogCategory.CONN, "STATE_CHANGED", LogLevel.INFO)
val ConnError = LogEventType(LogCategory.CONN, "ERROR", LogLevel.ERROR)
val ConnConnectTrigger = LogEventType(LogCategory.CONN_CONNECT, "TRIGGER", LogLevel.INFO)
val ConnConnectScan = LogEventType(LogCategory.CONN_CONNECT, "SCAN", LogLevel.INFO)
val ConnConnectScanFailed = LogEventType(LogCategory.CONN_CONNECT, "SCAN_FAILED", LogLevel.INFO)
val ConnConnectScanResult = LogEventType(LogCategory.CONN_CONNECT, "SCAN_RESULT", LogLevel.INFO)
val ConnConnectStart = LogEventType(LogCategory.CONN_CONNECT, "START", LogLevel.INFO)
val ConnConnectConnected = LogEventType(LogCategory.CONN_CONNECT, "CONNECTED", LogLevel.INFO)

val ConnDisconnectTrigger = LogEventType(LogCategory.CONN_DISCONNECT, "TRIGGER", LogLevel.INFO)

val ConnServerSwitchTrigger = LogEventType(LogCategory.CONN_SERVER_SWITCH, "TRIGGER", LogLevel.INFO)
val ConnServerSwitchServerSelected =
    LogEventType(LogCategory.CONN_SERVER_SWITCH, "SERVER_SELECTED", LogLevel.INFO)
val ConnServerSwitchFailed = LogEventType(LogCategory.CONN_SERVER_SWITCH, "FAILED", LogLevel.INFO)

val LocalAgentStateChanged = LogEventType(LogCategory.LOCAL_AGENT, "STATE_CHANGED", LogLevel.INFO)
val LocalAgentError = LogEventType(LogCategory.LOCAL_AGENT, "ERROR", LogLevel.ERROR)
val LocalAgentStatus = LogEventType(LogCategory.LOCAL_AGENT, "STATUS", LogLevel.INFO)
val LocalAgentStats = LogEventType(LogCategory.LOCAL_AGENT, "STATS", LogLevel.INFO)

val NetworkCurrent = LogEventType(LogCategory.NETWORK, "CURRENT", LogLevel.INFO)
val NetworkChanged = LogEventType(LogCategory.NETWORK, "CHANGED", LogLevel.INFO)
val NetworkUnavailable = LogEventType(LogCategory.NETWORK, "UNAVAILABLE", LogLevel.INFO)

val OsPowerCurrent = LogEventType(LogCategory.OS_POWER, "CURRENT", LogLevel.INFO)
val OsPowerChanged = LogEventType(LogCategory.OS_POWER, "CHANGED", LogLevel.INFO)

val ProfilesAutoOpen = LogEventType(LogCategory.PROFILES, "AUTO_OPEN", LogLevel.INFO)

val SettingsChanged = LogEventType(LogCategory.SETTINGS, "CHANGED", LogLevel.INFO)
val SettingsCurrent = LogEventType(LogCategory.SETTINGS, "CURRENT", LogLevel.INFO)

@JvmField
val UiConnect = LogEventType(LogCategory.UI, "CONNECT", LogLevel.INFO)
@JvmField
val UiReconnect = LogEventType(LogCategory.UI, "RECONNECT", LogLevel.INFO)
@JvmField
val UiDisconnect = LogEventType(LogCategory.UI, "DISCONNECT", LogLevel.INFO)
val UiSetting = LogEventType(LogCategory.UI, "SETTING", LogLevel.INFO)

val UserCertCurrentState = LogEventType(LogCategory.USER_CERT, "CURRENT", LogLevel.INFO)
val UserCertRefresh = LogEventType(LogCategory.USER_CERT, "REFRESH", LogLevel.INFO)
val UserCertRevoked = LogEventType(LogCategory.USER_CERT, "REVOKED", LogLevel.INFO)
val UserCertNew = LogEventType(LogCategory.USER_CERT, "NEW", LogLevel.INFO)
val UserCertRefreshError = LogEventType(LogCategory.USER_CERT, "REFRESH_ERROR", LogLevel.WARN)
val UserCertScheduleRefresh = LogEventType(LogCategory.USER_CERT, "SCHEDULE_REFRESH", LogLevel.INFO)
val UserCertStoreError = LogEventType(LogCategory.USER_CERT, "STORE_ERROR", LogLevel.ERROR)

val UserPlanCurrent = LogEventType(LogCategory.USER_PLAN, "CURRENT", LogLevel.INFO)
val UserPlanChanged = LogEventType(LogCategory.USER_PLAN, "CHANGED", LogLevel.INFO)
val UserPlanMaxSessionsReached = LogEventType(LogCategory.USER_PLAN, "MAX_SESSIONS_REACHED", LogLevel.INFO)

val WidgetUpdate = LogEventType(LogCategory.WIDGET, "UPDATE", LogLevel.INFO)
val WidgetRemoved = LogEventType(LogCategory.WIDGET, "REMOVED", LogLevel.INFO)
val WidgetsRestored = LogEventType(LogCategory.WIDGET, "RESTORED", LogLevel.INFO)
val WidgetStateUpdate = LogEventType(LogCategory.WIDGET, "STATE_UPDATE", LogLevel.INFO)
