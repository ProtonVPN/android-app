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
package com.protonvpn.android.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import butterknife.BindView
import butterknife.ButterKnife
import com.protonvpn.android.R
import me.proton.core.network.domain.ApiResult

class MinimizedNetworkLayout : RelativeLayout, LoaderUI {

    @BindView(R.id.textTitle)
    lateinit var textTitle: TextView

    @BindView(R.id.loadingLayout)
    lateinit var loadingLayout: View

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        View.inflate(context, R.layout.component_minimized_loader, this)
        ButterKnife.bind(this)
        loadingLayout.isVisible = false
    }

    override fun switchToRetry(error: ApiResult.Error) {
        loadingLayout.isVisible = false
    }

    override fun switchToEmpty() {
        loadingLayout.isVisible = false
    }

    override fun switchToLoading() {
        loadingLayout.isVisible = true
        textTitle.setText(R.string.loaderLoadingServers)
    }

    override fun setRetryListener(listener: () -> Unit) {}

    override val state: NetworkFrameLayout.State? = null
}
