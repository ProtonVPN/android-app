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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseDialog
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.sortedByLocaleAware
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import javax.inject.Inject

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
        systemAppsSection.add(LoadSystemAppsViewHolder {
            loadSystemApps(layoutManager, adapter, systemAppsSection)
        })

        adapter.add(regularAppsSection)
        adapter.add(systemAppsSection)

        textTitle.setText(R.string.excludeAppsTitle)
        textDescription.setText(R.string.excludeAppsDescription)
        progressBar.visibility = View.VISIBLE

        val packageManager = requireContext().packageManager
        val selection = userData.splitTunnelApps.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            regularAppsSection.addAll(
                getSortedAppViewHolders(packageManager, true, selection)
            )
            list.adapter = adapter
            progressBar.visibility = View.GONE
        }
        mainScope.launch {
            removeUninstalledApps(packageManager, userData)
        }
    }

    @OnClick(R.id.textDone)
    fun textDone() {
        dismiss()
    }

    private fun loadSystemApps(
        layoutManager: LinearLayoutManager,
        adapter: GroupAdapter<GroupieViewHolder>,
        systemAppsSection: Section
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val selection = userData.splitTunnelApps.toSet()
            systemAppsSection.addAll(
                getSortedAppViewHolders(requireContext().packageManager, false, selection)
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
        packageManager: PackageManager,
        withLaunchIntent: Boolean,
        selection: Set<String>
    ): List<AppViewHolder> {
        val regularApps = getInstalledInternetApps(packageManager, withLaunchIntent)
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
        packageManager: PackageManager,
        withLaunchIntent: Boolean
    ): List<SelectedApplicationEntry> = withContext(dispatcherProvider.Io) {
        val apps = packageManager.getInstalledApplications(
            0
        ).filter { appInfo ->
            val hasInternet = (packageManager.checkPermission(Manifest.permission.INTERNET, appInfo.packageName)
                    == PackageManager.PERMISSION_GRANTED)
            val hasLaunchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            hasInternet && hasLaunchIntent == withLaunchIntent
        }.map { appInfo ->
            ensureActive()
            getAppMetadata(packageManager, appInfo)
        }
        apps
    }

    private fun getAppMetadata(
        packageManager: PackageManager,
        appInfo: ApplicationInfo
    ): SelectedApplicationEntry {
        val label = appInfo.loadLabel(packageManager)
        val icon = appInfo.loadIcon(packageManager)
        return SelectedApplicationEntry(appInfo.packageName, label.toString(), icon)
    }
}
