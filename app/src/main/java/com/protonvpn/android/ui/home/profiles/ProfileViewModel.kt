package com.protonvpn.android.ui.home.profiles

import android.content.Context
import androidx.annotation.StringRes
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.logUiSettingChange
import com.protonvpn.android.models.config.Setting
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.ProtocolSelection
import com.protonvpn.android.ui.SaveableSettingsViewModel
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val userData: UserData,
    private val currentUser: CurrentUser
) : SaveableSettingsViewModel() {

    data class ServerViewState(
        val secureCore: Boolean,
        val countryName: String,
        @StringRes val countryLabel: Int,
        @StringRes val serverNameRes: Int,
        val serverNameValue: String,
        val serverNameVisible: Boolean,
        @StringRes val serverLabel: Int,
        @StringRes val serverHint: Int
    )

    data class InputValidation(
        @StringRes val profileNameError: Int,
        @StringRes val countryError: Int,
        @StringRes val serverError: Int
    )

    private var editedProfile: Profile? = null
    private var profileNameInput: String = ""

    val profileColor = MutableStateFlow(ProfileColor.random())
    val protocol = MutableStateFlow(getDefaultProtocol(userData))
    val eventSomethingWrong = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventValidationFailed = MutableSharedFlow<InputValidation>(extraBufferCapacity = 1)
    private val secureCore = MutableStateFlow(userData.secureCoreEnabled)
    private val country = MutableStateFlow<VpnCountry?>(null)
    private val server = MutableStateFlow<ServerSelection?>(null)

    val isSecureCoreAvailable: Boolean get() = currentUser.vpnUserCached()?.isUserPlusOrAbove == true
    val isSecureCoreEnabled: Boolean get() = secureCore.value
    val selectedCountryCode: String? get() = country.value?.flag
    val canDeleteProfile: Boolean get() = editedProfile != null
    val saveButtonLabel: Int get() =
        if (editedProfile != null) R.string.saveButton else R.string.doneButton

    val serverViewState: Flow<ServerViewState> = combine(
        secureCore,
        country,
        server
    ) { secureCore, country, server ->
        val serverNameRes = when (server) {
            ServerSelection.FastestInCountry -> R.string.profileFastest
            ServerSelection.RandomInCountry -> R.string.profileRandom
            is ServerSelection.Specific -> if (secureCore) R.string.secureCoreConnectVia else 0
            null -> 0
        }
        val serverName = if (server is ServerSelection.Specific) {
            if (secureCore) CountryTools.getFullName(server.server.entryCountry) else server.server.serverName
        } else {
            ""
        }
        ServerViewState(
            secureCore,
            country?.countryName ?: "",
            if (secureCore) R.string.exitCountry else R.string.country,
            serverNameRes,
            serverName,
            country != null,
            if (secureCore) R.string.entryCountry else R.string.serverSelection,
            if (secureCore) R.string.selectCountry else R.string.selectServer
        )
    }

    fun initWithProfile(context: Context, profile: Profile?) {
        if (profile != null) {
            profile.wrapper.setDeliverer(serverManager)
            editedProfile = profile
            profileNameInput = profile.getDisplayName(context)
            profileColor.value = requireNotNull(profile.profileColor)
            secureCore.value = profile.isSecureCore
            server.value = getServerSelection(profile)
            country.value = profile.server?.let { getServerCountry(it) }
            protocol.value = ProtocolSelection.from(
                profile.getProtocol(userData),
                profile.getTransmissionProtocol(userData)
            )
        }
    }

    fun onProfileNameTextChanged(name: String) {
        profileNameInput = name
    }

    fun setProfileColor(color: ProfileColor) {
        profileColor.value = color
    }

    fun setSecureCore(enabled: Boolean) {
        if (secureCore.value != enabled) {
            secureCore.value = enabled
            val currentCountry = country.value
            if (currentCountry == null || serverManager.getVpnExitCountry(currentCountry.flag, enabled) == null)
                country.value = null
            server.value = ServerSelection.FastestInCountry
        }
    }

    fun setCountryCode(newCountryCode: String) {
        val newCountry = serverManager.getVpnExitCountry(newCountryCode, secureCore.value)
        if (newCountry == null) {
            ProtonLogger.logCustom(
                LogLevel.ERROR,
                LogCategory.APP,
                "ProfileViewModel: no country found for code `$newCountryCode`"
            )
            eventSomethingWrong.tryEmit(Unit)
        }
        country.value = newCountry
        server.value = ServerSelection.FastestInCountry
    }

    fun setServer(serverIdSelection: ServerIdSelection) {
        val serverSelection: ServerSelection? = when (serverIdSelection) {
            ServerIdSelection.FastestInCountry -> ServerSelection.FastestInCountry
            ServerIdSelection.RandomInCountry -> ServerSelection.RandomInCountry
            is ServerIdSelection.Specific ->
                serverManager.getServerById(serverIdSelection.id)?.let { ServerSelection.Specific(it) }
        }
        if (serverSelection == null) {
            ProtonLogger.logCustom(
                LogLevel.ERROR,
                LogCategory.APP,
                "ProfileViewModel: no server found for $serverIdSelection"
            )
            eventSomethingWrong.tryEmit(Unit)
        }
        server.value = serverSelection
    }

    fun setProtocol(newProtocol: ProtocolSelection) {
        protocol.value = newProtocol
    }

    override fun validate(): Boolean {
        val profileNameError = if (profileNameInput.isBlank()) R.string.errorEmptyName else 0
        val countryError = if (country.value == null) {
            if (secureCore.value) R.string.errorEmptyExitCountry else R.string.errorEmptyCountry
        } else {
            0
        }
        val serverError = if (server.value == null && country.value != null) {
            if (secureCore.value) R.string.errorEmptyEntryCountry else R.string.errorEmptyServer
        } else {
            0
        }
        val isValid = profileNameError == 0 && countryError == 0 && serverError == 0
        if (!isValid)
            eventValidationFailed.tryEmit(InputValidation(profileNameError, countryError, serverError))
        return isValid
    }

    override fun hasUnsavedChanges(): Boolean {
        val currentProfile = editedProfile
        return if (currentProfile != null) {
            profileNameInput != currentProfile.name ||
                profileColor.value != currentProfile.profileColor ||
                currentProfile.country != country.value?.flag ||
                server.value != getServerSelection(currentProfile)
        } else {
            profileNameInput.isNotBlank() || country.value != null || server.value != null
        }
    }

    override fun saveChanges() {
        val serverWrapper = createServerWrapper(
            requireNotNull(server.value),
            requireNotNull(country.value),
            secureCore.value,
            serverManager
        )
        val transmissionProtocol = (protocol.value as? ProtocolSelection.OpenVPN)?.transmission
        val newProfile =
            Profile(profileNameInput, null, serverWrapper, requireNotNull(profileColor.value).id).apply {
                setTransmissionProtocol(transmissionProtocol?.toString())
                setProtocol(protocol.value.protocol)
            }
        editedProfile?.let {
            if (it == userData.defaultConnection) {
                ProtonLogger.logUiSettingChange(Setting.QUICK_CONNECT_PROFILE, "profile edit")
            }
            serverManager.editProfile(it, newProfile)
        } ?: serverManager.addToProfileList(newProfile)
    }

    fun deleteProfile() {
        serverManager.deleteProfile(editedProfile)
    }

    private fun getServerCountry(server: Server): VpnCountry? {
        return serverManager.getVpnExitCountry(
            if (secureCore.value) server.exitCountry else server.flag,
            secureCore.value
        )
    }

    private fun getServerSelection(profile: Profile): ServerSelection? = when {
        profile.wrapper.isFastestInCountry -> ServerSelection.FastestInCountry
        profile.wrapper.isRandomInCountry -> ServerSelection.RandomInCountry
        profile.server != null -> ServerSelection.Specific(profile.server!!)
        else -> null
    }

    private fun getDefaultProtocol(userData: UserData) =
        ProtocolSelection.from(userData.selectedProtocol, userData.transmissionProtocol)

    private fun createServerWrapper(
        serverSelection: ServerSelection,
        country: VpnCountry,
        isSecureCore: Boolean,
        serverManager: ServerManager
    ): ServerWrapper = when (serverSelection) {
        ServerSelection.FastestInCountry ->
            ServerWrapper.makeFastestForCountry(country.flag, serverManager)
        ServerSelection.RandomInCountry ->
            ServerWrapper.makeRandomForCountry(country.flag, serverManager)
        is ServerSelection.Specific ->
            ServerWrapper.makeWithServer(serverSelection.server, serverManager)
    }.apply {
        setSecureCore(isSecureCore)
    }
}
