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
package com.protonvpn.android.ui.drawer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseFragment
import com.protonvpn.android.components.BaseViewHolder
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContentLayout(R.layout.fragment_log)
class LogFragment : BaseFragment() {

    private val logAdapter = LogAdapter()
    internal var log: MutableList<String> = ArrayList()

    @BindView(R.id.recyclerView) @JvmField var recyclerView: RecyclerView? = null
    @Inject lateinit var stateMonitor: VpnStateMonitor
    @Inject lateinit var userData: UserData

    override fun onViewCreated() {
        recyclerView?.adapter = logAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            ProtonLogger.getLogLines().collect { addToLog(it) }
        }
    }

    private fun addToLog(item: String) {
        log.add(item)
        logAdapter.notifyDataSetChanged()
        recyclerView?.scrollToPosition(log.size - 1)
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogLineViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogLineViewHolder {
            return LogLineViewHolder(
                    LayoutInflater.from(parent.context).inflate(R.layout.log_item, parent, false))
        }

        override fun onBindViewHolder(holder: LogLineViewHolder, position: Int) {
            holder.bindData(log[position])
        }

        override fun getItemCount() = log.size
    }

    inner class LogLineViewHolder internal constructor(view: View) : BaseViewHolder<String>(view) {

        @BindView(R.id.logLine) lateinit var logLine: TextView

        override fun bindData(message: String) {
            super.bindData(message)
            logLine.text = message
        }
    }
}
