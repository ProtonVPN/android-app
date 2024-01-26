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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.databinding.ActivityRecyclerWithToolbarBinding
import com.protonvpn.android.databinding.LogItemBinding
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.utils.ViewUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class LogActivity : BaseActivityV2() {

    private val binding by viewBinding(ActivityRecyclerWithToolbarBinding::inflate)
    private val viewModel by viewModels<LogActivityViewModel>()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        with(binding.contentAppbar.toolbar) {
            initToolbarWithUpEnabled(this)
        }
        addMenuProvider(LogMenuProvider(this::shareLogFile), this)

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

        ProtonLogger.getLogLinesForDisplay()
            .flowWithLifecycle(lifecycle)
            .onEach {
                adapter.addLogItem(it)
                if (isScrolledToBottom) recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
            .launchIn(lifecycleScope)
    }

    private fun shareLogFile() {
        lifecycleScope.launch {
            val intent = viewModel.shareLogFile()
            if (intent != null) {
                startActivity(intent)
            }
        }
    }

    private class LogMenuProvider(private val onItemSelected: () -> Unit) : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menu.add(R.string.action_share)
                .setIcon(CoreR.drawable.ic_proton_arrow_up_from_square)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            onItemSelected()
            return true
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
