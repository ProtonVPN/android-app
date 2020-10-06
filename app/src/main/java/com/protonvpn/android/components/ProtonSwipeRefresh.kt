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
package com.protonvpn.android.components

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import me.proton.core.network.domain.ApiResult

class ProtonSwipeRefresh : SwipeRefreshLayout, LoaderUI {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun switchToRetry(error: ApiResult.Error) {
        // TODO pop snackbar with error
        isRefreshing = false
    }

    override fun switchToEmpty() {
        isRefreshing = false
    }

    override fun switchToLoading() {
        isRefreshing = true
    }

    override val state: NetworkFrameLayout.State? = null

    override fun setRetryListener(listener: () -> Unit) {}
}
