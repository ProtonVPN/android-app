/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.components;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.protonvpn.android.R;
import com.protonvpn.android.api.NetworkLoader;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.ui.snackbar.SnackbarHelper;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

// Deprecated in favor of using BaseFragmentV2
@Deprecated
public abstract class BaseFragment extends Fragment implements NetworkLoader {

    @Nullable @BindView(R.id.loadingContainer) LoaderUI loadingContainer;
    boolean isRegisteredForEvents = false;
    boolean hasRegistered = false;

    @Nullable private Unbinder unbinder;

    public abstract void onViewCreated();

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(AnnotationParser.getAnnotatedLayout(this), container, false);
        unbinder = ButterKnife.bind(this, view);
        onViewCreated();
        if (isRegisteredForEvents && !hasRegistered) {
            EventBus.getInstance().register(this);
            hasRegistered = true;
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public LoaderUI getNetworkFrameLayout() {
        if (loadingContainer == null) {
            throw new RuntimeException(
                "Must have NetworkFrameLayout named loadingContainer in layout to use API");
        }
        return loadingContainer;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isRegisteredForEvents && hasRegistered) {
            EventBus.getInstance().unregister(this);
            hasRegistered = false;
        }
    }

    public void registerForEvents() {
        isRegisteredForEvents = true;
    }
}
