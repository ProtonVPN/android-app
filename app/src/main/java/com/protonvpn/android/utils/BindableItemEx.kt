/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.utils

import androidx.annotation.CallSuper
import androidx.viewbinding.ViewBinding
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.OnItemLongClickListener
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder

abstract class BindableItemEx<T : ViewBinding> : BindableItem<T>() {

    private var bindingInternal: T? = null

    protected val binding: T
        get() = bindingInternal ?: throw IllegalStateException("view used after unbind()")

    @CallSuper
    override fun bind(
        viewHolder: GroupieViewHolder<T>,
        position: Int,
        payloads: List<Any?>,
        onItemClickListener: OnItemClickListener?,
        onItemLongClickListener: OnItemLongClickListener?
    ) {
        // When data in groupie adapter is updated some of the items will not unbind, and we won't
        // be able to do any cleanup. Force cleanup in items whose view holder is being taken over
        // by new item.
        val currentItem = viewHolder.item
        if (currentItem != this && currentItem is BindableItemEx<*>) {
            // Do not remove cast even if IDE marks it as unnecessary
            // If removed it results in internal compilation error
            (currentItem as BindableItemEx<*>).clear()
        }

        super.bind(viewHolder, position, payloads, onItemClickListener, onItemLongClickListener)
    }

    override fun bind(viewBinding: T, position: Int) {
        // Sometimes we can get 2 binds in a row without unbind in between
        clear()
        bindingInternal = viewBinding
    }

    override fun unbind(viewHolder: GroupieViewHolder<T>) {
        super.unbind(viewHolder)
        clear()
        bindingInternal = null
    }

    // Clear anything that can cause memory leak here
    protected abstract fun clear()
}
