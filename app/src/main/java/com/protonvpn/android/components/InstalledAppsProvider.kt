/*
 * Copyright (c) 2021. Proton Technologies AG
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

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.protonvpn.android.ui.settings.AppInfoService
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val APP_INFO_RESULT_TIMEOUT_MS = 5_000L

@Reusable
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val packageManager: PackageManager
) {
    data class AppInfo(
        val packageName: String,
        val name: String,
        val icon: Drawable
    )

    suspend fun getInstalledInternetApps(withLaunchIntent: Boolean): List<String> =
        withContext(dispatcherProvider.Io) {
            packageManager.getInstalledApplications(
                0
            ).map {
                it.packageName
            }.filter { packageName ->
                val hasLaunchIntent = packageManager.getLaunchIntentForPackage(packageName) != null
                val hasInternetPermission =
                    (packageManager.checkPermission(Manifest.permission.INTERNET, packageName)
                            == PackageManager.PERMISSION_GRANTED)
                hasInternetPermission && hasLaunchIntent == withLaunchIntent
            }
        }

    /**
     * Maps package names to application labels. Package names that are not found (e.g. the app has
     * been uninstalled) are ignored, so the resulting list can have fewer elements than the input.
     */
    suspend fun getNamesOfInstalledApps(packages: List<String>): List<CharSequence> =
        withContext(dispatcherProvider.Io) {
            packages.mapNotNull { packageName ->
                try {
                    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
        }

    suspend fun getAppInfos(iconSizePx: Int, packages: List<String>): List<AppInfo> {
        val channel = getAppInfosChannel(appContext, iconSizePx, packages)
        val results = ArrayList<AppInfo>(packages.size)
        try {
            do {
                val appInfo = withTimeoutOrNull(APP_INFO_RESULT_TIMEOUT_MS) {
                    channel.receiveCatching().getOrNull()
                }
                if (appInfo != null) {
                    results.add(appInfo)
                }
            } while (appInfo != null)
        } catch (cancellation: CancellationException) {
            channel.close()
        }
        if (results.size < packages.size) {
            coroutineContext.ensureActive()
            // Something went wrong, add missing items with no icon nor label.
            val defaultIcon = appContext.packageManager.defaultActivityIcon
            packages.subList(results.size, packages.size).forEach { packageName ->
                results.add(AppInfo(packageName, packageName, defaultIcon))
            }
        }
        return results
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getAppInfosChannel(
        context: Context,
        iconSizePx: Int,
        packages: List<String>
    ): Channel<AppInfo> {
        // The channel should not need much capacity but it doesn't hurt to have enough for the
        // worst possible case.
        val resultsChannel = Channel<AppInfo>(capacity = packages.size)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val outMessenger = Messenger(service)
                val inMessenger = Messenger(IncomingHandler(context, packages, resultsChannel))
                val message = AppInfoService.createRequestMessage(packages, iconSizePx).apply {
                    replyTo = inMessenger
                }
                outMessenger.send(message)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                resultsChannel.close()
            }
        }
        context.bindService(
            AppInfoService.createIntent(context), connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )
        resultsChannel.invokeOnClose {
            context.unbindService(connection)
        }
        return resultsChannel
    }

    private inner class IncomingHandler(
        private val context: Context,
        private val packages: List<String>,
        private val resultsChannel: Channel<AppInfo>
    ) : Handler(Looper.myLooper()!!) {

        private var resultCount = 0

        override fun handleMessage(msg: Message) {
            when(msg.what) {
                AppInfoService.MESSAGE_TYPE_APP_INFO -> {
                    val packageName = msg.data.getString(AppInfoService.EXTRA_PACKAGE_NAME) ?: return
                    val name = msg.data.getString(AppInfoService.EXTRA_APP_LABEL) ?: packageName
                    val iconBytes = msg.data.getByteArray(AppInfoService.EXTRA_APP_ICON)
                    val iconDrawable =
                        if (iconBytes != null) {
                            val iconBitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                            BitmapDrawable(context.resources, iconBitmap)
                        } else {
                            context.packageManager.defaultActivityIcon
                        }
                    resultsChannel.trySend(AppInfo(packageName, name, iconDrawable))
                    if (++resultCount == packages.size)
                        resultsChannel.close()
                }
                else -> super.handleMessage(msg)
            }
        }
    }
}
