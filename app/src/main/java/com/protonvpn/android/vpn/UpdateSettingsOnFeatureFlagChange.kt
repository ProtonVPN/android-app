package com.protonvpn.android.vpn

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateSettingsOnFeatureFlagChange @Inject constructor(
    mainScope: CoroutineScope,
    appConfig: AppConfig,
    private val userData: UserData,
    private val serverManager: ServerManager
) {
    init {
        appConfig.appConfigUpdateEvent
            .onEach {
                checkForProtocolSupport(it.featureFlags)
            }
            .launchIn(mainScope)
    }

    private fun checkForProtocolSupport(featureFlags: FeatureFlags) {
        if (!userData.protocol.isSupported(featureFlags)) {
            serverManager.findDefaultProfile()?.let {
                if (!it.getProtocol(userData).isSupported(featureFlags)) {
                    userData.defaultProfileId = null
                }
            }
            userData.protocol = ProtocolSelection(VpnProtocol.Smart)
        }
    }
}
