/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.settings

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.protonvpn.android.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

private const val WEBP_QUALITY = 70

class AppInfoService : Service() {

    private val messenger = Messenger(IncomingHandler())

    override fun onBind(intent: Intent?): IBinder? {
        return messenger.binder
    }

    private inner class IncomingHandler : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when(msg.what) {
                MESSAGE_TYPE_REQUEST_APPS -> {
                    val packageNames = msg.data.getStringArray(EXTRA_PACKAGE_NAME_LIST) ?: return
                    val iconSizePx = msg.data.getInt(EXTRA_ICON_SIZE, 48)

                    val replyTo = msg.replyTo // msg will be recycled before data is ready, store replyTo.
                    processRequest(packageNames) { appMetaData ->
                        val reply = Message.obtain(this, MESSAGE_TYPE_APP_INFO).apply {
                            data = createResultBundle(appMetaData, iconSizePx)
                        }
                        replyTo.send(reply)
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRequest(packageNames: Array<String>, sendResult: (AppMetaData) -> Unit) {
        try {
            val scope = CoroutineScope(Dispatchers.Default)
            val appMetaDataChannel = Channel<AppMetaData>(10)
            scope.launch {
                for (appMetaData in appMetaDataChannel) {
                    sendResult(appMetaData)
                }
            }

            packageNames.forEach { pkgName ->
                appMetaDataChannel.trySendBlocking(
                    getAppMetaData(pkgName) ?: AppMetaData(pkgName, pkgName, null)
                )
            }

            appMetaDataChannel.close()
        } catch (e: Throwable) {
            Log.i(Constants.SECONDARY_PROCESS_TAG, "Exception while reading app metadata", e)
            throw e
        }
    }

    fun compressIcon(iconDrawable: Drawable, sizePx: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        with(iconDrawable) {
            setBounds(0, 0, sizePx, sizePx)
            draw(canvas)
        }

        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, bytes)
        return bytes.toByteArray()
    }

    private fun createResultBundle(appMetaData: AppMetaData, iconSizePx: Int): Bundle =
        Bundle().apply {
            putString(EXTRA_PACKAGE_NAME, appMetaData.packageName)
            putString(EXTRA_APP_LABEL, appMetaData.label)
            if (appMetaData.iconDrawable != null) {
                putByteArray(EXTRA_APP_ICON, compressIcon(appMetaData.iconDrawable, iconSizePx))
            }
        }

    companion object {
        private const val MESSAGE_TYPE_REQUEST_APPS = 1
        const val MESSAGE_TYPE_APP_INFO = 2
        private const val EXTRA_PACKAGE_NAME_LIST = "package names"
        private const val EXTRA_ICON_SIZE = "icon size"
        const val EXTRA_PACKAGE_NAME = "package name"
        const val EXTRA_APP_LABEL = "app label"
        const val EXTRA_APP_ICON = "app icon"

        fun createRequestMessage(packageNames: List<String>, iconSizePx: Int): Message = Message.obtain().apply {
            what = MESSAGE_TYPE_REQUEST_APPS
            data.putStringArray(EXTRA_PACKAGE_NAME_LIST, packageNames.toTypedArray())
            data.putInt(EXTRA_ICON_SIZE, iconSizePx)
        }

        fun createIntent(context: Context) = Intent(context, AppInfoService::class.java)
    }
}

data class AppMetaData(val packageName: String, val label: String, val iconDrawable: Drawable?)

// Gets label and icon for the given package name. Returns null if the package is not found.
// For querying large number of packages use InstalledAppsProvider.
fun Context.getAppMetaData(pkgName: String): AppMetaData? =
    try {
        val appInfo = packageManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
        AppMetaData(
            pkgName,
            appInfo.loadLabel(packageManager).toString(),
            // Don't extract process the default icon.
            if (appInfo.icon > 0) appInfo.loadIcon(packageManager) else null
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }