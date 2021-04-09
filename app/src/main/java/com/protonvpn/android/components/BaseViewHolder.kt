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

import android.view.View
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import butterknife.ButterKnife
import java.lang.IllegalStateException

@Deprecated("Use BaseViewHolderV2")
abstract class BaseViewHolder<T>(view: View) : RecyclerView.ViewHolder(view) {

    private var itemInternal: T? = null
    protected val item: T get() =
            itemInternal ?: throw IllegalStateException("Accessing data when not bound")

    init {
        ButterKnife.bind(this, view)
    }

    @CallSuper
    open fun bindData(newItem: T) {
        itemInternal = newItem
    }

    @CallSuper
    open fun unbind() {
        itemInternal = null
    }
}
