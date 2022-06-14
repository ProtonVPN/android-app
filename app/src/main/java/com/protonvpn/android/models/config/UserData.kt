/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.models.config

import android.os.Build
import androidx.lifecycle.MutableLiveData
import com.google.gson.annotations.SerializedName
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.Serializable
import java.util.UUID

enum class Setting(val logName: String) {
    QUICK_CONNECT_PROFILE("Quick connect"),
    DEFAULT_PROTOCOL("Default protocol"),
    NETSHIELD_PROTOCOL("NetShield protocol"),
    SECURE_CORE("Secure Core"),
    LAN_CONNECTIONS("LAN connections"),
    SPLIT_TUNNEL_ENABLED("Split Tunneling enabled"),
    SPLIT_TUNNEL_APPS("Split Tunneling excluded apps"),
    SPLIT_TUNNEL_IPS("Split Tunneling excluded IPs"),
    DEFAULT_MTU("Default MTU"),
    SAFE_MODE("Safe Mode"),
    RESTRICTED_NAT("Restricted NAT"),
    VPN_ACCELERATOR_ENABLED("VPN Accelerator enabled"),
    VPN_ACCELERATOR_NOTIFICATIONS("VPN Accelerator notifications"),
    API_DOH("Use DoH for API"),
    CONNECT_ON_BOOT("Connect on boot")
}

class UserData private constructor() : Serializable {

    // TODO: remove some time after migration
    @SerializedName("user") var migrateUser: String? = null
    @SerializedName("isLoggedIn") var migrateIsLoggedIn = false
    @SerializedName("vpnInfoResponse") var migrateVpnInfoResponse: VpnInfoResponse? = null

    @SerializedName("defaultConnection") var migrateDefaultConnection: Profile? = null

    var connectOnBoot = false
        get() = Build.VERSION.SDK_INT < 26 && field
        set(value) { field = value; commitUpdate(Setting.CONNECT_ON_BOOT) }

    var mtuSize = 1375
        set(value) { field = value; commitUpdate(Setting.DEFAULT_MTU) }

    var useSplitTunneling = false
        set(value) { field = value; commitUpdate(Setting.SPLIT_TUNNEL_ENABLED) }

    var splitTunnelApps: List<String> = emptyList()
        set(value) { field = value; commitUpdate(Setting.SPLIT_TUNNEL_APPS) }

    var splitTunnelIpAddresses: List<String> = emptyList()
        set(value) { field = value; commitUpdate(Setting.SPLIT_TUNNEL_IPS) }

    var defaultProfileId: UUID? = null
        set(value) { field = value; commitUpdate(Setting.QUICK_CONNECT_PROFILE) }

    var showVpnAcceleratorNotifications = true
        set(value) { field = value; commitUpdate(Setting.VPN_ACCELERATOR_NOTIFICATIONS) }

    var bypassLocalTraffic = false
        set(value) { field = value; commitUpdate(Setting.LAN_CONNECTIONS) }

    var secureCoreEnabled = false
        set(value) { field = value; commitUpdate(Setting.SECURE_CORE) }

    var apiUseDoH: Boolean = true
        set(value) { field = value; commitUpdate(Setting.API_DOH) }

    var vpnAcceleratorEnabled: Boolean = true
        set(value) {
            field = value
            vpnAcceleratorLiveData.postValue(value)
            commitUpdate(Setting.VPN_ACCELERATOR_ENABLED)
        }

    var safeModeEnabled: Boolean = true
        set(value) {
            field = value
            safeModeLiveData.value = value
            commitUpdate(Setting.SAFE_MODE)
        }

    var randomizedNatEnabled: Boolean = true
        set(value) {
            field = value
            randomizedNatLiveData.value = value
            commitUpdate(Setting.RESTRICTED_NAT)
        }

    var selectedProtocol: VpnProtocol = VpnProtocol.Smart
        private set

    var transmissionProtocol: TransmissionProtocol = TransmissionProtocol.TCP
        private set

    private var netShieldProtocol: NetShieldProtocol? = null

    @Transient val netShieldSettingUpdateEvent = LiveEvent()
    @Transient val vpnAcceleratorLiveData = MutableLiveData<Boolean>()
    @Transient val selectedProtocolLiveData = MutableLiveData<VpnProtocol>()
    @Transient val safeModeLiveData = MutableLiveData<Boolean?>()
    @Transient val randomizedNatLiveData = MutableLiveData<Boolean>()
    @Transient val updateEvent = LiveEvent()
    // settingChangeEvent is not equivalent to updateEvent because it doesn't emit events
    // when observer resumes.
    @Transient val settingChangeEvent = MutableSharedFlow<Setting>(extraBufferCapacity = 1)

    // Handles post-deserialization initialization
    private fun init() {
        vpnAcceleratorLiveData.value = vpnAcceleratorEnabled
        selectedProtocolLiveData.value = selectedProtocol
    }

    private fun commitUpdate(setting: Setting) {
        Storage.save(this)
        settingChangeEvent.tryEmit(setting)
        updateEvent.emit()
    }

    /**
     * @return true if changing "useSplitTunneling" has no effect.
     */
    val isSplitTunnelingConfigEmpty: Boolean
        get() = splitTunnelApps.isEmpty() && splitTunnelIpAddresses.isEmpty()

    fun isVpnAcceleratorEnabled(featureFlags: FeatureFlags) =
        !featureFlags.vpnAccelerator || vpnAcceleratorEnabled

    fun isSafeModeEnabled(featureFlags: FeatureFlags): Boolean? =
        safeModeEnabled.takeIf { featureFlags.safeMode }

    fun setProtocols(protocol: VpnProtocol, transmissionProtocol: TransmissionProtocol?) {
        if (transmissionProtocol != null) {
            this.transmissionProtocol = transmissionProtocol
        }
        selectedProtocol = protocol
        selectedProtocolLiveData.postValue(selectedProtocol)
        commitUpdate(Setting.DEFAULT_PROTOCOL)
    }

    fun shouldBypassLocalTraffic() =
        ProtonApplication.getAppContext().isTV() || bypassLocalTraffic

    fun setNetShieldProtocol(value: NetShieldProtocol?) {
        netShieldProtocol = value
        netShieldSettingUpdateEvent.emit()
        commitUpdate(Setting.NETSHIELD_PROTOCOL)
    }

    fun getNetShieldProtocol(vpnUser: VpnUser?) = if (vpnUser == null || vpnUser.isFreeUser)
        NetShieldProtocol.DISABLED
    else
        netShieldProtocol ?: NetShieldProtocol.ENABLED

    fun finishUserMigration() {
        migrateIsLoggedIn = false
        migrateUser = null
        migrateVpnInfoResponse = null
        Storage.save(this)
    }

    fun migrateDefaultProfile(serverManager: ServerManager) {
        val oldProfile = migrateDefaultConnection?.migrateFromOlderVersion(null)
        migrateDefaultConnection = null
        if (oldProfile != null) {
            val matchingProfile = serverManager.getSavedProfiles().find {
                it.name == oldProfile.name &&
                    it.wrapper == oldProfile.wrapper &&
                    it.isSecureCore == oldProfile.isSecureCore &&
                    it.profileColor == oldProfile.profileColor &&
                    it.getProtocol(this) == oldProfile.getProtocol(this) &&
                    it.getTransmissionProtocol(this) == oldProfile.getTransmissionProtocol(this)
            }
            if (matchingProfile == null) {
                // On TV the default profile was not on the list of saved profiles, add it.
                serverManager.addToProfileList(oldProfile)
                defaultProfileId = oldProfile.id
            } else {
                defaultProfileId = matchingProfile.id
            }
        }
    }

    companion object {
        fun load() = (Storage.load(UserData::class.java) ?: UserData()).apply { init() }
        fun create() = UserData().apply { init() }
    }
}
