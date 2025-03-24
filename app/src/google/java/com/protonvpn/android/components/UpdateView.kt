/*
 * Copyright (c) 2022 Proton AG
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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.doOnAttach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.protonvpn.android.BuildConfig.APPLICATION_ID
import com.protonvpn.android.databinding.UpdateViewBinding
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.launch

class UpdateView(
    context: Context,
    attributeSet: AttributeSet? = null
) : FrameLayout(
    context,
    attributeSet
) {

    private val binding: UpdateViewBinding

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = UpdateViewBinding.inflate(inflater, this, true)
        binding.updateView.visibility = GONE

        binding.buttonUpdate.setOnClickListener {
            openPlayStore()
        }

        doOnAttach {
            val lifeCycleOwner = findViewTreeLifecycleOwner()
            lifeCycleOwner?.lifecycleScope?.launch {
                lifeCycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    checkForUpdate()
                }
            }
        }
    }

    private suspend fun checkForUpdate() {
        try {
            val updateInfo = AppUpdateManagerFactory.create(context).requestAppUpdateInfo()
            if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                ProtonLogger.logCustom(
                    LogLevel.INFO,
                    LogCategory.APP_UPDATE,
                    "New update available"
                )
                binding.updateView.visibility = VISIBLE
            } else {
                ProtonLogger.logCustom(
                    LogLevel.INFO,
                    LogCategory.APP_UPDATE,
                    "No update available"
                )
                binding.updateView.visibility = GONE
            }
        } catch (e: Exception) {
            ProtonLogger.logCustom(
                LogLevel.WARN,
                LogCategory.APP,
                "Failure to contact google play: ${e.message}"
            )
        }
    }

    @SuppressWarnings("UnsafeImplicitIntentLaunch")
    private fun openPlayStore() {
        val packageName = APPLICATION_ID
        try {
            val appStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            )
            appStoreIntent.setPackage("com.android.vending")
            context.startActivity(appStoreIntent)
        } catch (exception: ActivityNotFoundException) {
            ProtonLogger.logCustom(
                LogLevel.WARN,
                LogCategory.APP,
                "Failure to load google play market: ${exception.message}"
            )
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        }
    }

}
