package com.protonvpn.android.ui.home.profiles

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
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

    private var editableProfile: Profile? = null

    private val _selectedColor = MutableLiveData<ProfileColor>()
    val selectedColor: LiveData<ProfileColor> get() { return _selectedColor }

    var secureCoreEnabled = userData.isSecureCoreEnabled
    val canDeleteProfile: Boolean get() { return editableProfile != null }

    val transmissionProtocol: TransmissionProtocol
        get() = editableProfile?.getTransmissionProtocol(userData) ?: userData.transmissionProtocol
    val serverValidateSelection = ProtonSpinner.OnValidateSelection<ServerWrapper> {
        userData.hasAccessToServer(serverManager.getServer(it))
    }

    fun getServerCountry(server: Server): VpnCountry? {
        return serverManager.getVpnExitCountry(
            if (secureCoreEnabled) server.exitCountry else server.flag,
            secureCoreEnabled
        )
    }

    val selectedProtocol: VpnProtocol
        get() = editableProfile?.getProtocol(userData) ?: userData.selectedProtocol

    fun initWithProfile(profile: Profile?) {
        editableProfile = profile
        if (profile != null) {
            profile.wrapper.setDeliverer(serverManager)
            secureCoreEnabled = profile.isSecureCore
        }
        _selectedColor.value =  editableProfile?.profileColor ?: ProfileColor.random()
    }

    fun selectProfileColor(color: ProfileColor) {
        _selectedColor.value = color
    }

    fun getCountryItems(): List<VpnCountry> =
        if (secureCoreEnabled)
            serverManager.getSecureCoreExitCountries()
        else
            serverManager.getVpnCountries()

    fun hasUnsavedChanges(profileName: String, serverWrapper: ServerWrapper?, serverName: String?): Boolean {
        val profile = editableProfile
        return if (profile != null) {
            profileName != profile.name || profile.wrapper != serverWrapper
        } else {
            profileName.isNotEmpty() || !serverName.isNullOrEmpty()
        }
    }

    fun saveProfile(
        name: String,
        serverWrapper: ServerWrapper,
        transmissionProtocol: TransmissionProtocol,
        protocol: VpnProtocol
    ) {
        val newProfile =
            Profile(name, null, serverWrapper, requireNotNull(selectedColor.value).id).apply {
                setTransmissionProtocol(transmissionProtocol.toString())
                setProtocol(protocol)
            }
        editableProfile?.let {
            serverManager.editProfile(it, newProfile)
        } ?: serverManager.addToProfileList(newProfile)
    }

    fun deleteProfile() {
        serverManager.deleteProfile(editableProfile)
    }
}
