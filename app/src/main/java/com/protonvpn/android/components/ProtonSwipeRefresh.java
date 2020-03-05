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
package com.protonvpn.android.components;

import android.content.Context;
import android.util.AttributeSet;

import com.protonvpn.android.api.ApiResult;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ProtonSwipeRefresh extends SwipeRefreshLayout implements LoaderUI {

    public ProtonSwipeRefresh(@NonNull Context context) {
        super(context);
    }

    public ProtonSwipeRefresh(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void switchToRetry(ApiResult.Error error) {
        // TODO pop snackbar with error
        setRefreshing(false);
    }

    @Override
    public void switchToEmpty() {
        setRefreshing(false);
    }

    @Override
    public void switchToLoading() {
        setRefreshing(true);
    }

    @Override
    public void setRetryListener(NetworkFrameLayout.OnRequestRetryListener listener) {

    }

    @Deprecated
    @Override
    public NetworkFrameLayout.State getState() {
        return null;
    }
}
