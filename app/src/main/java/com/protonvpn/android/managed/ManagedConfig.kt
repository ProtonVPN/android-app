/*
 * Copyright (c) 2024 Proton AG
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
package com.protonvpn.android.managed

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class AutoLoginConfig {

    data class UsernamePassword(
        val username: String,
        val password: String,
    ) : AutoLoginConfig()

    data class Token(
        val token: String,
        val group: String,
        val deviceId: String?,
    ) : AutoLoginConfig()

    val id by lazy { computeId(this) }
}

private fun computeId(config: AutoLoginConfig): String =
    when (config) {
        is AutoLoginConfig.Token -> {
            MessageDigest.getInstance("SHA-256").apply {
                update(config.token.toByteArray())
                update(config.group.toByteArray())
                config.deviceId?.let { update(it.toByteArray()) }
            }.digest().joinToString("") { "%02x".format(it) }
        }
        is AutoLoginConfig.UsernamePassword -> config.username
    }

@Singleton
class ManagedConfig(
    configFlow: StateFlow<AutoLoginConfig?>,
) : StateFlow<AutoLoginConfig?> by configFlow {

    @Inject constructor(
        @ApplicationContext context: Context
    ) : this(createConfigFlow(context))

    val isManaged: Boolean get() = value != null

    companion object {
        fun createConfigFlow(context: Context): StateFlow<AutoLoginConfig?> {
            val restrictionsManager: RestrictionsManager? =
                context.getSystemService(RestrictionsManager::class.java)
            if (restrictionsManager == null) {
                ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "RestrictionsManager not found")
                return MutableStateFlow(null)
            }

            val configFlow = MutableStateFlow(restrictionsManager.getConfig())
            context.registerBroadcastReceiver(IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)) {
                ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Received new restrictions")
                configFlow.value = restrictionsManager.getConfig()
            }
            return configFlow
        }

        private fun RestrictionsManager.getConfig() : AutoLoginConfig? {
            val restrictions = applicationRestrictions
            if (restrictions == null || restrictions.isEmpty) {
                ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "No restrictions")
                return null
            }

            val token = restrictions.getString("token")
            val group = restrictions.getString("group")
            val deviceId = restrictions.getString("deviceId")

            return if (token != null && group != null) {
                ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Token restrictions found")
                AutoLoginConfig.Token(token, group, deviceId)
            } else {
                val username = restrictions.getString("username")
                val password = restrictions.getString("password")
                if (username != null && password != null) {
                    ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "UsernamePassword Restrictions found")
                    AutoLoginConfig.UsernamePassword(username, password)
                } else {
                    ProtonLogger.logCustom(LogCategory.MANAGED_CONFIG, "Unexpected restrictions")
                    null
                }
            }
        }
    }
}
