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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ApiResult;
import com.protonvpn.android.utils.Log;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MinimizedNetworkLayout extends RelativeLayout implements LoaderUI {

    @BindView(R.id.textTitle) TextView textTitle;
    @BindView(R.id.loadingLayout) View loadingLayout;

    public MinimizedNetworkLayout(Context context) {
        super(context);
        init();
    }

    public MinimizedNetworkLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MinimizedNetworkLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.component_minimized_loader, this);
        ButterKnife.bind(this);
        loadingLayout.setVisibility(View.GONE);
    }

    @Override
    public void switchToRetry(ApiResult.Error error) {
        loadingLayout.setVisibility(View.GONE);
        if (!BuildConfig.DEBUG) {
            Log.exception(new Exception(error.getDebugMessage()));
        }
    }

    @Override
    public void switchToEmpty() {
        loadingLayout.setVisibility(View.GONE);
    }

    @Override
    public void switchToLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        textTitle.setText(R.string.loaderLoadingServers);
    }

    @Override
    public void setRetryListener(NetworkFrameLayout.OnRequestRetryListener listener) {

    }

    @Override
    @Deprecated
    public NetworkFrameLayout.State getState() {
        return null;
    }
}
