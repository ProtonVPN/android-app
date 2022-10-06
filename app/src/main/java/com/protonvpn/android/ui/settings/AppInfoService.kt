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

package com.protonvpn.android.ui.settings

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import com.protonvpn.android.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

private const val WEBP_QUALITY = 70

@Suppress("Deprecation")
class AppInfoService : IntentService("AppInfoService") {

    @Suppress("TooGenericExceptionCaught", "TooGenericExceptionThrown")
    override fun onHandleIntent(intent: Intent?) {
        try {
            val packageNames = intent?.getStringArrayExtra(EXTRA_PACKAGE_NAME_LIST) ?: return
            val iconSizePx = intent.getIntExtra(EXTRA_ICON_SIZE, 48)
            val requestCode = intent.getLongExtra(EXTRA_REQUEST_CODE, 0)

            Log.i(Constants.SECONDARY_PROCESS_TAG, "Request $requestCode for ${packageNames.size}")
            val scope = CoroutineScope(Dispatchers.Default)
            val appMetaDataChannel = Channel<AppMetaData>(10)
            val processAndSendJob = scope.launch {
                for (appMetaData in appMetaDataChannel) {
                    val resultIntent = createResultIntent(appMetaData, iconSizePx, requestCode)
                    sendBroadcast(resultIntent)
                }
            }

            packageNames.forEach { pkgName ->
                appMetaDataChannel.trySendBlocking(getAppMetaData(pkgName))
            }

            appMetaDataChannel.close()

            runBlocking {
                processAndSendJob.join()
            }
        } catch (e: Throwable) {
            Log.i(Constants.SECONDARY_PROCESS_TAG, "Exception while reading app metadata", e)
            throw e
        }
    }

    private data class AppMetaData(val packageName: String, val label: String, val iconDrawable: Drawable?)

    private fun getAppMetaData(pkgName: String): AppMetaData =
        try {
            val appInfo = packageManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
            AppMetaData(
                pkgName,
                appInfo.loadLabel(packageManager).toString(),
                // Don't extract process the default icon.
                if (appInfo.icon > 0) appInfo.loadIcon(packageManager) else null
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppMetaData(pkgName, pkgName, null)
        }

    private fun createResultIntent(appMetaData: AppMetaData, iconSizePx: Int, requestCode: Long): Intent =
        Intent(RESULT_ACTION).apply {
            setPackage(getPackageName())
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_PACKAGE_NAME, appMetaData.packageName)
            putExtra(EXTRA_APP_LABEL, appMetaData.label)
            if (appMetaData.iconDrawable != null) {
                putExtra(EXTRA_APP_ICON, compressIcon(appMetaData.iconDrawable, iconSizePx))
            }
        }

    private fun compressIcon(iconDrawable: Drawable, sizePx: Int): ByteArray {
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

    companion object {
        private const val EXTRA_PACKAGE_NAME_LIST = "package names"
        private const val EXTRA_ICON_SIZE = "icon size"
        const val RESULT_ACTION = "app info action"
        const val EXTRA_PACKAGE_NAME = "package name"
        const val EXTRA_APP_LABEL = "app label"
        const val EXTRA_APP_ICON = "app icon"
        const val EXTRA_REQUEST_CODE = "request code"

        fun createIntent(context: Context, packageNames: List<String>, iconSizePx: Int, requestCode: Long) =
            Intent(context, AppInfoService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME_LIST, packageNames.toTypedArray())
                putExtra(EXTRA_ICON_SIZE, iconSizePx)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
            }

        fun createStopIntent(context: Context) = Intent(context, AppInfoService::class.java)
    }
}
