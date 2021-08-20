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
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.util.Pair
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.OnClick
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseDialog
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import java.util.ArrayList
import java.util.Collections
import java.util.SortedSet
import java.util.TreeSet
import javax.inject.Inject

@ContentLayout(R.layout.dialog_split_tunnel)
class AppsDialog : BaseDialog(),
    LoaderManager.LoaderCallbacks<Pair<List<SelectedApplicationEntry>, List<String>>> {
    private lateinit var adapter: AppsAdapter
    private lateinit var selection: MutableList<String>

    @BindView(R.id.textTitle)
    lateinit var textTitle: TextView

    @BindView(R.id.textDescription)
    lateinit var textDescription: TextView

    @BindView(R.id.list)
    lateinit var list: RecyclerView

    @BindView(R.id.progressBar)
    lateinit var progressBar: ProgressBar

    @JvmField @Inject
    var userData: UserData? = null
    override fun onViewCreated() {
        list.layoutManager = LinearLayoutManager(activity)
        adapter = AppsAdapter(userData)
        selection = userData!!.splitTunnelApps
        list.adapter = adapter
        loaderManager.initLoader(0, null, this)
        textTitle.setText(R.string.excludeAppsTitle)
        textDescription.setText(R.string.excludeAppsDescription)
    }

    @OnClick(R.id.textDone)
    fun textDone() {
        dismiss()
    }

    override fun onCreateLoader(
        id: Int,
        args: Bundle?
    ): Loader<Pair<List<SelectedApplicationEntry>, List<String>>> {
        progressBar.visibility = View.VISIBLE
        return InstalledPackagesLoader(activity, selection)
    }

    override fun onLoadFinished(
        loader: Loader<Pair<List<SelectedApplicationEntry>, List<String>>>,
        data: Pair<List<SelectedApplicationEntry>, List<String>>
    ) {
        adapter.setData(data.first)
        selection.removeAll(data.second)
        adapter.notifyDataSetChanged()
        progressBar.visibility = View.GONE
    }

    override fun onLoaderReset(loader: Loader<Pair<List<SelectedApplicationEntry>, List<String>>>) {
        adapter.setData(null)
    }

    class InstalledPackagesLoader internal constructor(
        context: Context?,
        selection: List<String>?
    ) : AsyncTaskLoader<Pair<List<SelectedApplicationEntry>, List<String>>>(
        context!!
    ) {
        private val packageManager: PackageManager
        private val selection: List<String>?
        private var data: Pair<List<SelectedApplicationEntry>, List<String>>? = null

        override fun loadInBackground(): Pair<List<SelectedApplicationEntry>, List<String>> {
            val apps: MutableList<SelectedApplicationEntry> = ArrayList()
            val seen: SortedSet<String> = TreeSet()
            for (info in packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            )) {
                /* skip apps that can't access the network anyway */
                if (packageManager.checkPermission(Manifest.permission.INTERNET, info.packageName)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val entry = SelectedApplicationEntry(packageManager, info)
                    entry.isSelected = selection!!.contains(info.packageName)
                    apps.add(entry)
                    seen.add(info.packageName)
                }
            }
            Collections.sort(apps)
            /* check for selected packages that don't exist anymore */
            val missing: MutableList<String> = ArrayList()
            for (pkg in selection!!) {
                if (!seen.contains(pkg)) {
                    missing.add(pkg)
                }
            }
            return Pair(apps, missing)
        }

        override fun onStartLoading() {
            if (data != null) {    /* if we have data ready, deliver it directly */
                deliverResult(data)
            }
            if (takeContentChanged() || data == null) {
                forceLoad()
            }
        }

        override fun deliverResult(data: Pair<List<SelectedApplicationEntry>, List<String>>?) {
            if (isReset) {
                return
            }
            this.data = data
            if (isStarted) {
                super.deliverResult(data)
            }
        }

        override fun onReset() {
            data = null
            super.onReset()
        }

        init {
            packageManager = context!!.packageManager
            this.selection = selection
        }
    }
}
