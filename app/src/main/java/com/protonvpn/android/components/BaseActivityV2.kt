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

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.protonvpn.android.R
import com.protonvpn.android.ui.snackbar.DelegatedSnackManager
import com.protonvpn.android.ui.snackbar.DelegatedSnackbarHelper
import com.protonvpn.android.ui.snackbar.SnackbarHelper
import com.protonvpn.android.ui.vpn.NoVpnPermissionActivity
import com.protonvpn.android.ui.vpn.VpnPermissionActivityDelegate
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.vpn.VpnPermissionDelegate
import javax.inject.Inject

abstract class BaseActivityV2 : AppCompatActivity(), VpnPermissionDelegate {

    @Inject lateinit var delegatedSnackManager: DelegatedSnackManager

    lateinit var snackbarHelper: SnackbarHelper
        private set

    @Suppress("LeakingThis")
    private val vpnPermissionDelegate = VpnPermissionActivityDelegate(this) { onVpnPermissionDenied() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = if (resources.getBoolean(R.bool.isTablet) || isTV())
            SCREEN_ORIENTATION_FULL_USER else SCREEN_ORIENTATION_PORTRAIT
        snackbarHelper = DelegatedSnackbarHelper(this, getContentView(), delegatedSnackManager)
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

    override fun askForPermissions(intent: Intent, onPermissionGranted: () -> Unit) {
        vpnPermissionDelegate.askForPermissions(intent, onPermissionGranted)
    }

    override fun getContext(): Context = this

    private fun onVpnPermissionDenied() {
        onVpnPrepareFailed()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            showNoVpnPermissionDialog(this)
        }
    }

    protected open fun onVpnPrepareFailed() {}

    private fun getContentView(): View = findViewById(android.R.id.content)

    companion object {
        @TargetApi(Build.VERSION_CODES.N)
        fun showNoVpnPermissionDialog(activity: Activity) {
            activity.launchActivity<NoVpnPermissionActivity>()
        }
    }
}
