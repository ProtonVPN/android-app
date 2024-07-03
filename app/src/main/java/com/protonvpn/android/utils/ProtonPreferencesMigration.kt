/*
 * Copyright (c) 2024. Proton AG
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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import io.sentry.Sentry

fun migrateProtonPreferences(appContext: Context, protonPrefsName: String, destinationPrefsName: String) {
    {
        if (AndroidUtils.sharedPrefsExists(appContext, protonPrefsName)) {
            ProtonLogger.logCustom(LogCategory.APP, "Storage migration: starting");
            try {
                val src = ProtonPreferences(appContext, BuildConfig.PREF_SALT, BuildConfig.PREF_KEY, protonPrefsName)
                val dst = appContext.getSharedPreferences(destinationPrefsName, Context.MODE_PRIVATE)

                migrateData(src, dst)
                ProtonLogger.logCustom(LogCategory.APP, "Storage migration: finished");
            } finally {
                AndroidUtils.deleteSharedPrefs(appContext, protonPrefsName)
            }
        }
    }.runCatchingCheckedExceptions { e ->
        ProtonLogger.logCustom(LogLevel.ERROR, LogCategory.APP, "Storage migration: failed with $e")
        Sentry.captureException(e)
    }
}

private fun migrateData(src: ProtonPreferences, dst: SharedPreferences) {
    dst.edit {
        // Integers.
        listOf("VERSION_CODE").forEach { key ->
            if (src.contains(key)) putInt(key, src.getInt(key, 0))
        }

        // Booleans.
        listOf("sentry_is_enabled").forEach { key ->
            if (src.contains(key)) putBoolean(key, src.getBoolean(key, false))
        }

        // Strings and serialized objects.
        listOf(
            "IP_ADDRESS",
            "LAST_USER",
            "VpnStateMonitor.VPN_STATE_NAME",
            "sentry_installation_id",
            "com.protonvpn.android.appconfig.ApiNotificationsResponse",
            "com.protonvpn.android.appconfig.AppConfigResponse",
            "com.protonvpn.android.models.config.bugreport.DynamicReportModel",
            "com.protonvpn.android.models.config.UserData",
            "com.protonvpn.android.models.profiles.SavedProfilesV3",
            "com.protonvpn.android.models.vpn.ConnectionParams",
            "com.protonvpn.android.models.vpn.PartnersResponse",
            "com.protonvpn.android.utils.ServerManager",
            "com.protonvpn.android.vpn.RecentsManager",
        ).forEach { key ->
            if (src.contains(key)) putString(key, src.getString(key, ""))
        }
    }
}
