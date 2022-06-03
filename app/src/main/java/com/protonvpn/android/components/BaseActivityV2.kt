/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.android.components

import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.protonvpn.android.R
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.ui.snackbar.DelegatedSnackManager
import com.protonvpn.android.ui.snackbar.DelegatedSnackbarHelper
import com.protonvpn.android.ui.snackbar.SnackbarHelper
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegateMobile
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.vpn.PermissionContract
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

interface VpnUiDelegateProvider {
    fun getVpnUiDelegate(): VpnUiActivityDelegate
}

abstract class BaseActivityV2 : AppCompatActivity(), VpnUiDelegateProvider {

    @Inject lateinit var delegatedSnackManager: DelegatedSnackManager

    lateinit var snackbarHelper: SnackbarHelper
        private set

    @Suppress("LeakingThis")
    private lateinit var vpnUiDelegate: VpnUiActivityDelegateMobile

    open fun retryConnection(profile: Profile) {}

    override fun getVpnUiDelegate(): VpnUiActivityDelegate = vpnUiDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = if (resources.getBoolean(R.bool.isTablet) || isTV())
            SCREEN_ORIENTATION_FULL_USER else SCREEN_ORIENTATION_PORTRAIT
        snackbarHelper = DelegatedSnackbarHelper(this, getContentView(), delegatedSnackManager)
        vpnUiDelegate = VpnUiActivityDelegateMobile(this) { retryConnection(it) }
    }

    fun initToolbarWithUpEnabled(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun getContentView(): View = findViewById(android.R.id.content)
}

suspend fun ComponentActivity.suspendForPermissions(permissionIntent: Intent?): Boolean {
    if (permissionIntent == null) return true
    val granted = suspendCancellableCoroutine<Boolean> { continuation ->
        val permissionCall = activityResultRegistry.register(
            "VPNPermission", PermissionContract(permissionIntent)
        ) { permissionGranted ->
            continuation.resume(permissionGranted)
        }
        permissionCall.launch(PermissionContract.VPN_PERMISSION_ACTIVITY)
    }
    return granted
}
