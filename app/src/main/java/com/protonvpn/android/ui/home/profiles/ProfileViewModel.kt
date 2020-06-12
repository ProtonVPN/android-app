package com.protonvpn.android.ui.home.profiles

import androidx.lifecycle.ViewModel
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.VpnStateMonitor
import javax.inject.Inject

class ProfileViewModel @Inject constructor(
    val vpnStateMonitor: VpnStateMonitor,
    val serverManager: ServerManager,
    val userData: UserData,
    val appConfig: AppConfig
) : ViewModel() {

    var editableProfile: Profile? = null
    var secureCoreEnabled = userData.isSecureCoreEnabled
    val profileServer get() = editableProfile?.server

    val transmissionProtocol: TransmissionProtocol
        get() = editableProfile?.getTransmissionProtocol(userData) ?: userData.transmissionProtocol
    val netShieldProtocol: NetShieldProtocol
        get() = editableProfile?.getNetShieldProtocol(userData, appConfig) ?: userData.netShieldProtocol
    val serverValidateSelection = ProtonSpinner.OnValidateSelection<ServerWrapper> {
        userData.hasAccessToServer(serverManager.getServer(it))
    }

    fun getServerCountry(server: Server): VpnCountry? {
        return serverManager.getVpnExitCountry(if (secureCoreEnabled) server.exitCountry else server.flag,
                secureCoreEnabled)
    }

    val selectedProtocol: VpnProtocol
        get() = editableProfile?.getProtocol(userData) ?: userData.selectedProtocol

    fun initWithProfile(profile: Profile?) {
        editableProfile = profile
        if (profile != null) {
            secureCoreEnabled = profile.isSecureCore
            profile.wrapper.setDeliverer(serverManager)
        }
    }

    fun getCountryItems(): List<VpnCountry> =
        if (secureCoreEnabled)
            serverManager.getSecureCoreExitCountries()
        else
            serverManager.getVpnCountries()

    fun saveProfile(profile: Profile) {
        editableProfile?.let {
            serverManager.editProfile(it, profile)
        } ?: serverManager.addToProfileList(profile)
    }

    fun deleteProfile() {
        serverManager.deleteProfile(editableProfile)
    }
}
