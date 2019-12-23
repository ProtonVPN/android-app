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
package com.protonvpn.android.ui.splittunneling;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseDialog;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.models.config.UserData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;

import androidx.core.util.Pair;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.OnClick;

@ContentLayout(R.layout.dialog_split_tunnel)
public class AppsDialog extends BaseDialog implements
    LoaderManager.LoaderCallbacks<Pair<List<SelectedApplicationEntry>, List<String>>> {

    private AppsAdapter adapter;
    private List<String> selection;
    @BindView(R.id.textTitle) TextView textTitle;
    @BindView(R.id.textDescription) TextView textDescription;
    @BindView(R.id.list) RecyclerView list;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @Inject UserData userData;

    @Override
    public void onViewCreated() {
        list.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new AppsAdapter(userData);
        selection = userData.getSplitTunnelApps();
        list.setAdapter(adapter);
        getLoaderManager().initLoader(0, null, this);
        textTitle.setText(R.string.excludeAppsTitle);
        textDescription.setText(R.string.excludeAppsDescription);
    }

    @OnClick(R.id.textDone)
    public void textDone() {
        dismiss();
    }

    @Override
    public Loader<Pair<List<SelectedApplicationEntry>, List<String>>> onCreateLoader(int id, Bundle args) {
        progressBar.setVisibility(View.VISIBLE);
        return new InstalledPackagesLoader(getActivity(), selection);
    }

    @Override
    public void onLoadFinished(Loader<Pair<List<SelectedApplicationEntry>, List<String>>> loader,
                               Pair<List<SelectedApplicationEntry>, List<String>> data) {
        adapter.setData(data.first);
        selection.removeAll(data.second);
        adapter.notifyDataSetChanged();
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onLoaderReset(Loader<Pair<List<SelectedApplicationEntry>, List<String>>> loader) {
        adapter.setData(null);
    }

    public static class InstalledPackagesLoader extends
        AsyncTaskLoader<Pair<List<SelectedApplicationEntry>, List<String>>> {

        private final PackageManager packageManager;
        private final List<String> selection;
        private Pair<List<SelectedApplicationEntry>, List<String>> data;

        InstalledPackagesLoader(Context context, List<String> selection) {
            super(context);
            packageManager = context.getPackageManager();
            this.selection = selection;
        }

        @Override
        public Pair<List<SelectedApplicationEntry>, List<String>> loadInBackground() {
            List<SelectedApplicationEntry> apps = new ArrayList<>();
            SortedSet<String> seen = new TreeSet<>();
            for (ApplicationInfo info : packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA)) {
                /* skip apps that can't access the network anyway */
                if (packageManager.checkPermission(Manifest.permission.INTERNET, info.packageName)
                    == PackageManager.PERMISSION_GRANTED) {
                    SelectedApplicationEntry entry = new SelectedApplicationEntry(packageManager, info);
                    entry.setSelected(selection.contains(info.packageName));
                    apps.add(entry);
                    seen.add(info.packageName);
                }
            }
            Collections.sort(apps);
            /* check for selected packages that don't exist anymore */
            List<String> missing = new ArrayList<>();
            for (String pkg : selection) {
                if (!seen.contains(pkg)) {
                    missing.add(pkg);
                }
            }
            return new Pair<>(apps, missing);
        }

        @Override
        protected void onStartLoading() {
            if (data != null) {    /* if we have data ready, deliver it directly */
                deliverResult(data);
            }
            if (takeContentChanged() || data == null) {
                forceLoad();
            }
        }

        @Override
        public void deliverResult(Pair<List<SelectedApplicationEntry>, List<String>> data) {
            if (isReset()) {
                return;
            }
            this.data = data;
            if (isStarted()) {
                super.deliverResult(data);
            }
        }

        @Override
        protected void onReset() {
            data = null;
            super.onReset();
        }
    }
}
