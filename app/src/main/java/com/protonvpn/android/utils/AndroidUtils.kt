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
package com.protonvpn.android.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.protonvpn.android.R

object AndroidUtils {

    fun isPackageSignedWith(
        context: Context,
        packageName: String,
        expectedSignature: String
    ): Boolean = with(context) {
        val oldAppInfo = packageManager.getPackageInfo(packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
                ?: return false

        val signatures = if (Build.VERSION.SDK_INT < 28)
            oldAppInfo.signatures
        else
            oldAppInfo.signingInfo.apkContentsSigners
        return signatures.iterator().asSequence().any { signature ->
            signature.toCharsString() == expectedSignature
        }
    }

    fun Context.isTV(): Boolean {
        val uiMode: Int = resources.configuration.uiMode
        return uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    }

    inline fun <reified T : Any> Context.launchActivity(
        options: Bundle? = null,
        noinline init: Intent.() -> Unit = {}
    ) {
        val intent = Intent(this, T::class.java)
        intent.init()
        startActivity(intent, options)
    }

    fun isPackageInstalled(context: Context, packageName: String) =
            try {
                context.packageManager.getApplicationInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }

    fun Context.registerBroadcastReceiver(
        intentFilter: IntentFilter,
        onReceive: (intent: Intent?) -> Unit
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                onReceive(intent)
            }
        }
        this.registerReceiver(receiver, intentFilter)
        return receiver
    }

    inline fun <E : Any, T : Collection<E>> T?.whenNotNullNorEmpty(func: (T) -> Unit) {
        if (this != null && this.isNotEmpty()) {
            func(this)
        }
    }

    fun playMarketIntentFor(appId: String) =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appId"))

    fun Resources.getFloatRes(@DimenRes id: Int) = TypedValue().also {
        getValue(id, it, true)
    }.float

    fun Context.isChromeOS() =
            packageManager.hasSystemFeature("org.chromium.arc.device_management")
}

fun Context.openUrl(url: Uri) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, url)
        if (this !is Activity)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        startActivity(browserIntent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.openUrlError, url), Toast.LENGTH_LONG).show()
    }
}

fun Context.openProtonUrl(url: String) =
    openUrl(Uri.parse(url).buildUpon().appendQueryParameter("utm_source", Constants.PROTON_URL_UTM_SOURCE).build())

fun ImageView.setColorTint(@ColorRes colorRes: Int) {
    this.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
        ContextCompat.getColor(context, colorRes),
        BlendModeCompat.SRC_OVER
    )
}

fun Button.setStartDrawable(@DrawableRes id: Int = 0) {
    this.setCompoundDrawablesWithIntrinsicBounds(id, 0, 0, 0)
}
