/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.ui.splittunneling

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import com.protonvpn.android.AppInfoService
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseDialog
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.sortedByLocaleAware
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val APP_INFO_RESULT_TIMEOUT_MS = 5_000L
private const val APP_ICON_SIZE_DP = 28
typealias ProgressCallback = (progress: Int, total: Int) -> Unit

@ContentLayout(R.layout.dialog_split_tunnel)
class AppsDialog : BaseDialog() {
    @BindView(R.id.textTitle)
    lateinit var textTitle: TextView

    @BindView(R.id.textDescription)
    lateinit var textDescription: TextView

    @BindView(R.id.list)
    lateinit var list: RecyclerView

    @BindView(R.id.progressBar)
    lateinit var progressBar: ProgressBar

    @Inject
    lateinit var userData: UserData
    @Inject
    lateinit var activityManager: ActivityManager
    @Inject
    lateinit var mainScope: CoroutineScope
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onViewCreated() {
        val layoutManager = LinearLayoutManager(activity)
        list.layoutManager = layoutManager

        val adapter = GroupAdapter<GroupieViewHolder>()
        val regularAppsSection = Section(AppsHeaderViewHolder(R.string.excludeAppsRegularSectionTitle))
        val systemAppsSection = Section(AppsHeaderViewHolder(R.string.excludeAppsSystemSectionTitle))
        systemAppsSection.add(LoadSystemAppsViewHolder { progressCallback ->
            loadSystemApps(layoutManager, adapter, systemAppsSection, progressCallback)
        })

        adapter.add(regularAppsSection)
        adapter.add(systemAppsSection)

        textTitle.setText(R.string.excludeAppsTitle)
        textDescription.setText(R.string.excludeAppsDescription)
        progressBar.visibility = View.VISIBLE

        val selection = userData.splitTunnelApps.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            regularAppsSection.addAll(
                getSortedAppViewHolders(requireContext(), true, selection) { progress, total ->
                    progressBar.isIndeterminate = false
                    progressBar.progress = progress
                    progressBar.max = total
                }
            )
            list.adapter = adapter
            progressBar.visibility = View.GONE
        }
        mainScope.launch {
            removeUninstalledApps(requireContext().packageManager, userData)
        }
    }

    @OnClick(R.id.textDone)
    fun textDone() {
        dismiss()
    }

    private fun loadSystemApps(
        layoutManager: LinearLayoutManager,
        adapter: GroupAdapter<GroupieViewHolder>,
        systemAppsSection: Section,
        progressCallback: ProgressCallback
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val selection = userData.splitTunnelApps.toSet()
            systemAppsSection.addAll(
                getSortedAppViewHolders(requireContext(), false, selection, progressCallback)
            )
            systemAppsSection.remove(systemAppsSection.getItem(1))
            val headerPosition = adapter.getAdapterPosition(systemAppsSection.getItem(0))
            layoutManager.scrollToPositionWithOffset(headerPosition, 0)
        }
    }

    private suspend fun removeUninstalledApps(packageManager: PackageManager, userData: UserData) {
        val installedPackages = withContext(dispatcherProvider.Io) {
            packageManager.getInstalledApplications(0).mapTo(mutableSetOf()) { it.packageName }
        }
        val userDataAppPackages = userData.splitTunnelApps
        userDataAppPackages
            .filterNot { installedPackages.contains(it) }
            .forEach { userData.removeAppFromSplitTunnel(it) }
    }

    private suspend fun getSortedAppViewHolders(
        context: Context,
        withLaunchIntent: Boolean,
        selection: Set<String>,
        onProgress: ProgressCallback
    ): List<AppViewHolder> {
        val regularApps = getInstalledInternetApps(context, withLaunchIntent, onProgress)
        val sortedRegularApps = withContext(dispatcherProvider.Comp) {
            regularApps.forEach { app ->
                if (selection.contains(app.packageName)) {
                    app.isSelected = true
                }
            }
            regularApps.sortedByLocaleAware { it.toString() }
        }
        return sortedRegularApps.map {
            AppViewHolder(
                it,
                onAdd = { userData.addAppToSplitTunnel(it.packageName) },
                onRemove = { userData.removeAppFromSplitTunnel(it.packageName) }
            )
        }
    }


    private suspend fun getInstalledInternetApps(
        context: Context,
        withLaunchIntent: Boolean,
        onProgress: ProgressCallback
    ): List<SelectedApplicationEntry> {
        val pm = context.packageManager
        val apps = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(0)
                .map { it.packageName }
                .filter { packageName ->
                    val hasInternet = (pm.checkPermission(Manifest.permission.INTERNET, packageName)
                            == PackageManager.PERMISSION_GRANTED)
                    val hasLaunchIntent = pm.getLaunchIntentForPackage(packageName) != null
                    hasInternet && hasLaunchIntent == withLaunchIntent
            }
        }
        return getAppInfos(context, apps, onProgress)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getAppInfos(
        context: Context,
        packages: List<String>,
        onProgress: ProgressCallback
    ): List<SelectedApplicationEntry> {
        val channel = getAppInfosChannel(context, packages)
        val results = ArrayList<SelectedApplicationEntry>(packages.size)
        try {
            do {
                val appInfo = withTimeoutOrNull(APP_INFO_RESULT_TIMEOUT_MS) {
                    channel.receiveOrNull()
                }
                if (appInfo != null) {
                    onProgress(results.size, packages.size)
                    results.add(appInfo)
                }
            } while (appInfo != null)
        } catch (cancellation : CancellationException) {
            channel.close()
        }
        if (results.size < packages.size) {
            coroutineContext.ensureActive()
            // Something went wrong, add missing items with no icon nor label.
            val defaultIcon = context.packageManager.defaultActivityIcon
            packages.subList(results.size, packages.size).forEach { packageName ->
                results.add(SelectedApplicationEntry(packageName, packageName, defaultIcon))
            }
        }
        return results
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getAppInfosChannel(
        context: Context,
        packages: List<String>
    ): Channel<SelectedApplicationEntry> {
        val requestCode = SystemClock.elapsedRealtime() // Unique value.
        val resultsChannel = Channel<SelectedApplicationEntry>()
        var resultCount = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val resultRequestCode = intent.getLongExtra(AppInfoService.EXTRA_REQUEST_CODE, 0)
                if (resultRequestCode != requestCode)
                    return

                val packageName = intent.getStringExtra(AppInfoService.EXTRA_PACKAGE_NAME) ?: return
                val name = intent.getStringExtra(AppInfoService.EXTRA_APP_LABEL) ?: packageName
                val iconBytes = intent.getByteArrayExtra(AppInfoService.EXTRA_APP_ICON)
                val iconDrawable =
                    if (iconBytes != null) {
                        val iconBitmap = BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                        BitmapDrawable(context.resources, iconBitmap)
                    } else {
                        context.packageManager.defaultActivityIcon
                    }
                resultsChannel.sendBlocking(SelectedApplicationEntry(packageName, name, iconDrawable))
                if (++resultCount == packages.size)
                    resultsChannel.close()
            }
        }

        context.registerReceiver(receiver, IntentFilter(AppInfoService.RESULT_ACTION))
        resultsChannel.invokeOnClose {
            context.unregisterReceiver(receiver)
            context.stopService(AppInfoService.createStopIntent(context))
        }
        val iconSizePx = APP_ICON_SIZE_DP.toPx()
        context.startService(AppInfoService.createIntent(context, packages, iconSizePx, requestCode))
        return resultsChannel
    }
}
