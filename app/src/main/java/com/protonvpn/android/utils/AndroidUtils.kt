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
import android.app.ActivityManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextUtils.getChars
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.ViewCompat
import com.protonvpn.android.R
import com.protonvpn.android.logging.ProtonLogger
import me.proton.core.util.kotlin.times
import okhttp3.internal.toHexString
import java.io.Serializable
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

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

    fun Context.isRtl() =
        resources.configuration.layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL

    fun Boolean.toInt() = if (this) 1 else 0

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

    @JvmStatic
    fun Resources.getFloatRes(@DimenRes id: Int) = TypedValue().also {
        getValue(id, it, true)
    }.float

    fun Context.isChromeOS() =
            packageManager.hasSystemFeature("org.chromium.arc.device_management")
}

fun Context.openUrl(url: String) = openUrl(Uri.parse(url))

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

// Need to drop alpha channel as android won't handle it properly in TextView
fun Int.toStringHtmlColorNoAlpha() = "#${toHexString().padStart(8, '0').drop(2)}"

fun Context.getStringHtmlColorNoAlpha(@ColorRes res: Int) =
    ContextCompat.getColor(this, res).toStringHtmlColorNoAlpha()

fun Context.openProtonUrl(url: String) =
    openUrl(Uri.parse(url).buildUpon().appendQueryParameter("utm_source", Constants.PROTON_URL_UTM_SOURCE).build())

@RequiresApi(24)
fun Activity.openVpnSettings() =
    startActivity(
        Intent(Settings.ACTION_VPN_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    )

fun ImageView.setColorTint(@ColorInt colorValue: Int) {
    this.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(colorValue, BlendModeCompat.SRC_OVER)
}

fun Button.setStartDrawable(@DrawableRes id: Int = 0) {
    this.setCompoundDrawablesWithIntrinsicBounds(id, 0, 0, 0)
}

fun Context.getThemeColor(@AttrRes attr: Int): Int =
    TypedValue().apply {
        theme.resolveAttribute(attr, this, true)
    }.data

@ColorRes
fun Context.getThemeColorId(@AttrRes attr: Int): Int =
    TypedValue().apply {
        theme.resolveAttribute(attr, this, true)
    }.resourceId

// Extracts text as byte array without leaving char content in temporary objects
fun CharSequence.toSafeUtf8ByteArray(): ByteArray {
    val chars = CharArray(length)
    getChars(this, 0, length, chars, 0)
    val charBuffer = CharBuffer.wrap(chars)
    val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
    val bytes = ByteArray(byteBuffer.remaining())
    byteBuffer.get(bytes)
    Arrays.fill(chars, '0')
    if (byteBuffer.hasArray())
        Arrays.fill(byteBuffer.array(), 0)
    return bytes
}

// Clears editable by overriding memory - undocumented behavior
fun Editable.overrideMemoryClear() {
    replace(0, length, " " * length)
    clear()
}

fun Context.getAppExitReasonForLog(): String? =
    if (Build.VERSION.SDK_INT >= 30) {
        val am = getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(packageName, 0, 5)
            .firstOrNull { it.processName == packageName } // Filter out non-main processes.
            ?.let {
                "${it.description}; reason: ${it.reason}; importance: ${it.importance}; " +
                    "time: ${ProtonLogger.formatTime(it.timestamp)}"
            }
    } else {
        null
    }

fun Context.isMainProcess() = packageName == getCurrentProcessName()

fun Context.getCurrentProcessName(): String? =
    if (Build.VERSION.SDK_INT >= 28) {
        Application.getProcessName()
    } else {
        val myPid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.runningAppProcesses?.find { it.pid == myPid }?.processName
    }

fun TelephonyManager.mobileCountryCode() =
    if (phoneType != TelephonyManager.PHONE_TYPE_CDMA && !networkCountryIso.isNullOrBlank())
        networkCountryIso else null


// Catch all errors, rethrow RuntimeExceptions. Use with care.
@Suppress("TooGenericExceptionCaught")
fun <T> (() -> T).runCatchingCheckedExceptions(catchBlock: (e: Exception) -> T) =
    try {
        this()
    } catch (e: Exception) {
        if (e is RuntimeException) throw e
        catchBlock(e)
    }

@Suppress("TooGenericExceptionCaught")
suspend fun <T> (suspend () -> T).runCatchingCheckedExceptions(catchBlock: (e: Exception) -> T) =
    try {
        this()
    } catch (e: Exception) {
        if (e is RuntimeException) throw e
        catchBlock(e)
    }

// Google should add this to compat library at some point: https://issuetracker.google.com/issues/242048899
inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String) : T? =
    // 34 because there are some bugs in 33 (at least for Parcelable, but let's not risk it)
    if (Build.VERSION.SDK_INT >= 34) getSerializableExtra(key, T::class.java)
    else getSerializableExtra(key) as? T

fun formatPrice(amount: Double, currency: String) : String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val format = android.icu.text.NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 2
        format.currency = android.icu.util.Currency.getInstance(currency)
        format.format(amount)
    } else {
        val format = java.text.NumberFormat.getCurrencyInstance()
        format.maximumFractionDigits = 2
        format.currency = java.util.Currency.getInstance(currency)
        format.format(amount)
    }

private const val MAX_STACK_TRACES = 100
tailrec fun Throwable.stacktraceMessage(
    builder: StringBuilder = StringBuilder(),
    maxTraces: Int = MAX_STACK_TRACES
) : StringBuilder {
    fun Collection<StackTraceElement>.append() = forEach { builder.append("\n    at $it") }

    builder.append(toString())
    if (stackTrace.size > maxTraces) {
        stackTrace.take(maxTraces / 2).append()
        builder.append("\n    ...")
        stackTrace.takeLast(maxTraces / 2).append()
    } else
        stackTrace.asList().append()

    val valCause = cause
    if (valCause != null) {
        builder.append("\n\n")
        builder.append("Caused by: ")
        return valCause.stacktraceMessage(builder)
    }
    return builder
}
