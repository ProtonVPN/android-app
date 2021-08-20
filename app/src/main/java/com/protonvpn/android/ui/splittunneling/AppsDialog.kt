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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContentLayout(R.layout.dialog_split_tunnel)
class AppsDialog : BaseDialog() {
    private lateinit var adapter: AppsAdapter

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

    override fun onViewCreated() {
        list.layoutManager = LinearLayoutManager(activity)
        adapter = AppsAdapter(userData)
        list.adapter = adapter
        textTitle.setText(R.string.excludeAppsTitle)
        textDescription.setText(R.string.excludeAppsDescription)
        progressBar.visibility = View.VISIBLE

        val selection = userData.splitTunnelApps.toSet()
        viewLifecycleOwner.lifecycleScope.launch {
            val allApps = getInstalledInternetApps(requireContext().packageManager)
            val sortedApps = withContext(Dispatchers.Default) {
                allApps.forEach { app ->
                    if (selection.contains(app.info.packageName)) {
                        app.isSelected = true
                    }
                }
                allApps.sortedByLocaleAware { it.toString() }
            }
            removeUninstalledApps(userData, allApps)
            adapter.setData(sortedApps)
            adapter.notifyDataSetChanged()
            progressBar.visibility = View.GONE
        }
    }

    @OnClick(R.id.textDone)
    fun textDone() {
        dismiss()
    }

    private fun removeUninstalledApps(userData: UserData, allApps: List<SelectedApplicationEntry>) {
        val userDataAppPackages = userData.splitTunnelApps
        val allAppPackages = HashSet<String>(allApps.size)
        allApps.mapTo(allAppPackages) { it.info.packageName }
        userDataAppPackages
            .filterNot { allAppPackages.contains(it) }
            .forEach { userData.removeAppFromSplitTunnel(it) }
    }

    private suspend fun getInstalledInternetApps(
        packageManager: PackageManager
    ): List<SelectedApplicationEntry> = withContext(Dispatchers.IO) {
        packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA
        ).filter { appInfo ->
            (packageManager.checkPermission(Manifest.permission.INTERNET, appInfo.packageName)
                    == PackageManager.PERMISSION_GRANTED)
        }.map { appInfo ->
            SelectedApplicationEntry(packageManager, appInfo)
        }
    }
}
