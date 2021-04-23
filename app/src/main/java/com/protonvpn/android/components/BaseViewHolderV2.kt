/*
 * Copyright (c) 2021 Proton Technologies AG
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

import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import java.lang.IllegalStateException

abstract class BaseViewHolderV2<T, Binding : ViewBinding>(
    val binding: Binding
) : RecyclerView.ViewHolder(binding.root) {

    private var itemInternal: T? = null
    protected val item: T get() =
        itemInternal ?: throw IllegalStateException("Accessing data when not bound")

    @CallSuper
    open fun bindData(newItem: T) {
        itemInternal = newItem
    }

    @CallSuper
    open fun unbind() {
        itemInternal = null
    }
}
