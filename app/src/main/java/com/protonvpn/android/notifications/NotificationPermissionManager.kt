/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.notifications

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ComposeBottomSheetDialogFragment
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnTextButton
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.compose.theme.headlineNorm
import javax.inject.Inject
import javax.inject.Singleton

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Singleton
class NotificationPermissionManager @Inject constructor(
    scope: CoroutineScope,
    @ApplicationContext private val appContext: Context,
    vpnStateMonitor: VpnStateMonitor,
    private val foregroundActivityTracker: ForegroundActivityTracker,
    private val notificationPrefs: NotificationPermissionPrefs,
    private val isTv: IsTvCheck,
) {
    init {
        if (!appContext.isNotificationPermissionGranted()) {
            vpnStateMonitor.status
                .takeWhile { !notificationPrefs.rationaleDismissed }
                .distinctUntilChanged()
                .onEach { status ->
                    if (status.state == VpnState.Connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        checkAndRequestNotificationPermission()
                    }
                }
                .launchIn(scope)

        }
    }

    private fun checkAndRequestNotificationPermission() {
        val activity = foregroundActivityTracker.foregroundActivity
        if (!isTv() &&
            activity is AppCompatActivity &&
            !activity.supportFragmentManager.isStateSaved &&
            !activity.isNotificationPermissionGranted()
        ) {
            showDialogAndRequestPermission(activity)
        }
    }

    private fun showDialogAndRequestPermission(activity: AppCompatActivity) {
        val bottomSheetFragment = NotificationPermissionBottomSheetFragment()
        bottomSheetFragment.showNowAndExpand(activity.supportFragmentManager, bottomSheetFragment.tag)
    }
}

inline fun Context.isNotificationPermissionGranted(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    return ContextCompat.checkSelfPermission(
        this,
        POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@AndroidEntryPoint
class NotificationPermissionBottomSheetFragment : ComposeBottomSheetDialogFragment() {

    private val viewModel: NotificationPermissionViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Mark rationale as don't show again in both permission granted and permission denied cases
            viewModel.doNotShowRationaleAgain()
            dismiss()
        }

    @Composable
    override fun Content() {
        NotificationPermissionUI(onTurnOnClicked = {
            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
        }, onDontShowAgainClicked = {
            viewModel.doNotShowRationaleAgain()
            dismiss()
        })
    }

    @Composable
    private fun NotificationPermissionUI(
        onTurnOnClicked: (() -> Unit),
        onDontShowAgainClicked: (() -> Unit)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.noNotificationPermissionGrantTitle),
                style = ProtonTheme.typography.headlineNorm,
            )
            Text(
                text = stringResource(id = R.string.noNotificationPermissionGrantDescription),
                style = ProtonTheme.typography.defaultWeak,
            )
            Spacer(modifier = Modifier.padding(8.dp))
            VpnSolidButton(
                text = stringResource(R.string.noNotificationPermissionGrantNowTitle),
                onClick = onTurnOnClicked,
            )
            VpnTextButton(
                text = stringResource(R.string.no_thanks),
                onClick = onDontShowAgainClicked,
            )
        }
    }
}
