/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.tv.presenters

import android.content.Context
import android.view.ViewGroup
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter

abstract class AbstractCardPresenter<C, T : BaseCardView>(val context: Context) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup) =
        ViewHolder(onCreateView())

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        onBindViewHolder(item as C, viewHolder.view as T)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        onUnbindViewHolder(viewHolder.view as T)
    }

    protected abstract fun onCreateView(): T
    protected abstract fun onBindViewHolder(card: C, cardView: T)
    protected open fun onUnbindViewHolder(cardView: T) {}
}
