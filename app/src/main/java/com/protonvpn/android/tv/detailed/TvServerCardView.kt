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

package com.protonvpn.android.tv.detailed

import android.content.Context
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.BaseCardView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.protonvpn.android.R
import com.protonvpn.android.databinding.TvServerCardBinding

class TvServerCardView(context: Context, val lifecycleOwner: LifecycleOwner) :
    BaseCardView(context, null, R.style.DefaultCardTheme) {

    val binding = TvServerCardBinding.inflate(LayoutInflater.from(getContext()), this, true)

    private var currentServer: TvServerListViewModel.ServerViewModel? = null
    private var actionStateObserver: Observer<TvServerListViewModel.ServerActionState>? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun bind(server: TvServerListViewModel.ServerViewModel) = with(binding) {
        currentServer = server
        serverName.text = server.name
        serverName.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, server.planDrawable(context), null)

        lock.isVisible = server.locked
        val alpha = if (server.locked) 0.5f else 1f
        serverName.alpha = alpha
        serverState.alpha = alpha

        serverLoadLabel.alpha = alpha
        serverLoadLabel.text = server.stateText(context)
        val stateColorRes = when (server.loadState) {
            TvServerListViewModel.ServerLoadState.MAINTENANCE -> R.color.tvInMaintenance
            TvServerListViewModel.ServerLoadState.LOW_LOAD -> R.color.tvServerLoadLow
            TvServerListViewModel.ServerLoadState.MEDIUM_LOAD -> R.color.tvServerLoadMedium
            TvServerListViewModel.ServerLoadState.HIGH_LOAD -> R.color.tvServerLoadHigh
        }
        serverLoadColor.setColorFilter(ContextCompat.getColor(context, stateColorRes))
        serverMaintenanceIcon.isVisible = server.loadState == TvServerListViewModel.ServerLoadState.MAINTENANCE
    }

    fun unbind() {
        removeObservers()
    }

    private fun updateAction(actionState: TvServerListViewModel.ServerActionState) = with(binding) {
        val bgColorRes = when (actionState) {
            TvServerListViewModel.ServerActionState.DISCONNECTED -> {
                actionButton.setText(R.string.connect)
                R.color.tvAccent
            }
            TvServerListViewModel.ServerActionState.CONNECTING -> {
                actionButton.setText(R.string.cancel)
                R.color.tvDisconnect
            }
            TvServerListViewModel.ServerActionState.CONNECTED -> {
                actionButton.setText(R.string.disconnect)
                R.color.tvDisconnect
            }
            TvServerListViewModel.ServerActionState.UPGRADE -> {
                actionButton.setText(R.string.upgrade)
                R.color.tvAccent
            }
            TvServerListViewModel.ServerActionState.UNAVAILABLE -> {
                actionButton.setText(R.string.tv_server_list_action_unavailable)
                R.color.tvInMaintenance
            }
        }
        actionButton.background = actionButton.background.mutate().apply {
            setTint(ContextCompat.getColor(context, bgColorRes))
        }
    }

    override fun setSelected(selected: Boolean) = with(binding) {
        super.setSelected(selected)
        actionButton.visibility = if (selected) VISIBLE else INVISIBLE
        root.setBackgroundResource(if (selected) R.drawable.tv_focused_server_background else 0)
        if (selected) {
            if (actionStateObserver == null) {
                val observer = Observer<TvServerListViewModel.ServerActionState> { updateAction(it) }
                actionStateObserver = observer
                requireServer().actionStateObservable.observe(lifecycleOwner, observer)
            }
        } else {
            removeObservers()
        }
    }

    private fun removeObservers() {
        actionStateObserver?.let {
            requireServer().actionStateObservable.removeObserver(it)
            actionStateObserver = null
        }
    }

    private fun requireServer() = currentServer ?: error("Action on unbind server view")
}
