/*
 * Copyright (c) 2019 Proton Technologies AG
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.LogItemBinding
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.AndroidUtils.setContentViewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LogActivity : BaseActivityV2() {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = setContentViewBinding(ActivityRecyclerWithToolbarBinding::inflate)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        setupLogDisplay(binding.recyclerItems)
    }

    private fun setupLogDisplay(recyclerView: RecyclerView) {
        val adapter = LogAdapter()
        val linearLayout = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = linearLayout
        recyclerView.itemAnimator = null

        var isScrolledToBottom = true
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                isScrolledToBottom = !recyclerView.canScrollVertically(1)
            }
        })

        lifecycleScope.launch {
            ProtonLogger.getLogLinesForDisplay().collect {
                adapter.addLogItem(it)
                if (isScrolledToBottom) recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private class LogLineVH(val binding: LogItemBinding) : RecyclerView.ViewHolder(binding.root)

    private class LogAdapter : RecyclerView.Adapter<LogLineVH>() {

        private val log = ArrayList<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            LogLineVH(LogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: LogLineVH, position: Int) {
            holder.binding.logLine.text = log[position]
        }

        override fun getItemCount() = log.size

        fun addLogItem(item: String) {
            log.add(item)
            notifyItemInserted(log.size - 1)
        }
    }
}
