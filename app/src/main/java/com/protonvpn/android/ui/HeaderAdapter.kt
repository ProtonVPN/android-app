/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.databinding.ItemHeaderBinding
import kotlin.properties.Delegates

class HeaderViewHolder(val views: ItemHeaderBinding) : RecyclerView.ViewHolder(views.root)

class HeaderAdapter(
    @StringRes private val labelRes: Int
) : RecyclerView.Adapter<HeaderViewHolder>() {

    var isEnabled: Boolean by Delegates.observable(true) { _, wasEnabled, isEnabled ->
        if (wasEnabled && !isEnabled) {
            notifyItemRemoved(0)
        } else if (!wasEnabled && isEnabled) {
            notifyItemInserted(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder =
        HeaderViewHolder(
            ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.views.textHeader.setText(labelRes)
    }

    override fun getItemCount(): Int = if (isEnabled) 1 else 0
}
