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
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.models.login.VpnInfoResponse
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.LiveEvent
import com.protonvpn.android.utils.Storage
import org.joda.time.DateTime
import org.joda.time.Minutes
import java.io.Serializable

class UserData private constructor() : Serializable {

    // TODO: remove some time after migration
    @SerializedName("user") var migrateUser: String? = null
    @SerializedName("isLoggedIn") var migrateIsLoggedIn = false
    @SerializedName("vpnInfoResponse") var migrateVpnInfoResponse: VpnInfoResponse? = null

    var connectOnBoot = false
        get() = Build.VERSION.SDK_INT < 26 && field
        set(value) { field = value; saveToStorage() }

    var showIcon = true
        get() = Build.VERSION.SDK_INT >= 26 || field
        set(value) { field = value; saveToStorage() }

    var useSplitTunneling = false
        set(value) { field = value; saveToStorage() }

    var mtuSize = 1375
        set(value) { field = value; saveToStorage() }

    var splitTunnelApps: List<String> = emptyList()
        set(value) { field = value; saveToStorage() }

    var splitTunnelIpAddresses: List<String> = emptyList()
        set(value) { field = value; saveToStorage() }

    var defaultConnection: Profile? = null
        set(value) { field = value; saveToStorage() }

    var showVpnAcceleratorNotifications = true
        set(value) { field = value; saveToStorage() }

    var bypassLocalTraffic = false
        set(value) { field = value; saveToStorage() }

    var isSecureCoreEnabled = false
        set(value) { field = value; saveToStorage() }

    var apiUseDoH: Boolean = true
        set(value) { field = value; saveToStorage() }

    var vpnAcceleratorEnabled: Boolean = true
        set(value) {
            field = value
            vpnAcceleratorLiveData.postValue(value)
            saveToStorage()
        }

    private var trialDialogShownAt: DateTime? = null

    var selectedProtocol: VpnProtocol = VpnProtocol.Smart
        private set

    var transmissionProtocol: TransmissionProtocol = TransmissionProtocol.TCP
        private set

    private var netShieldProtocol: NetShieldProtocol? = null

    @Transient val netShieldSettingUpdateEvent = LiveEvent()
    @Transient val vpnAcceleratorLiveData = MutableLiveData<Boolean>()
    @Transient val selectedProtocolLiveData = MutableLiveData<VpnProtocol>()
    @Transient val updateEvent = LiveEvent()

    // Handles post-deserialization initialization
    private fun init() {
        vpnAcceleratorLiveData.value = vpnAcceleratorEnabled
        selectedProtocolLiveData.value = selectedProtocol
    }

    private fun saveToStorage() {
        Storage.save(this)
        updateEvent.emit()
    }

    fun onLogout() {
        setTrialDialogShownAt(null)
        defaultConnection = null
        setNetShieldProtocol(null)
    }

    fun wasTrialDialogRecentlyShowed() =
        trialDialogShownAt != null && Minutes.minutesBetween(trialDialogShownAt, DateTime()).minutes < 360

    fun setTrialDialogShownAt(trialDialogShownAt: DateTime?) {
        this.trialDialogShownAt = trialDialogShownAt
        saveToStorage()
    }

    /**
     * @return true if changing "useSplitTunneling" has no effect.
     */
    val isSplitTunnelingConfigEmpty: Boolean
        get() = splitTunnelApps.isEmpty() && splitTunnelIpAddresses.isEmpty()

    val isVpnAcceleratorEnabled get() = vpnAcceleratorEnabled

    fun setProtocols(protocol: VpnProtocol, transmissionProtocol: TransmissionProtocol?) {
        if (transmissionProtocol != null) {
            this.transmissionProtocol = transmissionProtocol
        }
        selectedProtocol = protocol
        selectedProtocolLiveData.postValue(selectedProtocol)
        saveToStorage()
    }

    fun shouldBypassLocalTraffic() =
        ProtonApplication.getAppContext().isTV() || bypassLocalTraffic

    fun setNetShieldProtocol(value: NetShieldProtocol?) {
        netShieldProtocol = value
        netShieldSettingUpdateEvent.emit()
        saveToStorage()
    }

    fun getNetShieldProtocol(vpnUser: VpnUser?) = if (vpnUser == null || vpnUser.isFreeUser)
        NetShieldProtocol.DISABLED
    else
        netShieldProtocol ?: NetShieldProtocol.ENABLED

    fun finishUserMigration() {
        migrateIsLoggedIn = false
        migrateUser = null
        migrateVpnInfoResponse = null
        saveToStorage()
    }

    companion object {
        fun load() = (Storage.load(UserData::class.java) ?: UserData()).apply { init() }
        fun create() = UserData().apply { init() }
    }
}
