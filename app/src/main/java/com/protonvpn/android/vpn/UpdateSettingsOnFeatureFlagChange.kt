package com.protonvpn.android.vpn

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateSettingsOnFeatureFlagChange @Inject constructor(
    mainScope: CoroutineScope,
    appConfig: AppConfig,
    private val userSettingsManager: CurrentUserLocalSettingsManager
) {
    init {
        appConfig.appConfigUpdateEvent
            .onEach {
                checkForProtocolSupport(it.featureFlags)
            }
            .launchIn(mainScope)
    }

    private suspend fun checkForProtocolSupport(featureFlags: FeatureFlags) {
        val protocol = userSettingsManager.rawCurrentUserSettingsFlow.first().protocol
        if (!protocol.isSupported(featureFlags)) {
            userSettingsManager.updateProtocol(ProtocolSelection(VpnProtocol.Smart))
        }
    }
}
