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
package com.protonvpn.android.appconfig

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppConfig(scope: CoroutineScope, val api: ProtonApiRetroFit) {

    private var appConfigResponse: AppConfigResponse

    init {
        appConfigResponse =
                Storage.load(AppConfigResponse::class.java,
                        getDefaultConfig())
        scope.launch {
            update()
        }
    }

    suspend fun update() {
        val config = api.getAppConfig().valueOrNull
        if (config != null) {
            Storage.save(config)
            appConfigResponse = config
        }
    }

    fun getOpenVPNPorts(): DefaultPorts = appConfigResponse.defaultPorts

    fun getFeatureFlags(): FeatureFlags = appConfigResponse.featureFlags

    private fun getDefaultConfig(): AppConfigResponse {
        val defaultPorts = OpenVPNConfigResponse(DefaultPorts.getDefaults())
        val defaultFeatureFlags = FeatureFlags(0, 0)
        return AppConfigResponse(defaultPorts,
                defaultFeatureFlags)
    }
}
