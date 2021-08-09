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

package com.protonvpn.android.ui.settings

import android.os.Bundle
import android.text.Editable
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.internal.TextWatcherAdapter
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.databinding.ActivitySettingsExcludeIpsBinding
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.utils.setMinSizeTouchDelegate
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import javax.inject.Inject

@ContentLayout(R.layout.activity_settings_exclude_ips)
class SettingsExcludeIpsActivity
    : BaseActivityV2<ActivitySettingsExcludeIpsBinding, SettingsExcludeIpsViewModel>()
{
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
            ViewModelProvider(this, viewModelFactory).get(SettingsExcludeIpsViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        val excludedIpsAdapter = GroupAdapter<GroupieViewHolder>()

        with(binding) {
            buttonAdd.setMinSizeTouchDelegate()
            buttonAdd.setOnClickListener {
                addIp(inputIp.text.toString())
            }
            updateAddButtonState(inputIp.text.toString())

            inputIp.addTextChangedListener(object : TextWatcherAdapter() {
                override fun afterTextChanged(editable: Editable) {
                    updateAddButtonState(editable.toString())
                }
            })
            val editorActionListener = TextView.OnEditorActionListener { _, actionId, _ ->
                onEditorAction(actionId)
            }
            inputIp.setOnEditorActionListener(editorActionListener)

            recyclerExcludedIps.adapter = excludedIpsAdapter
            recyclerExcludedIps.layoutManager = LinearLayoutManager(this@SettingsExcludeIpsActivity)
            // Disable change animations for instant header updates.
            (recyclerExcludedIps.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        val removeAction = { item: LabeledItem -> confirmRemove(item) }
        viewModel.ipAddresses.observe(this, Observer { excludedIps ->
            val groups = if (excludedIps.isNotEmpty()) {
                val headerText =
                    getString(R.string.settingsExcludedIPAddressesListHeader, excludedIps.size)

                val section = Section(HeaderViewHolder(text = headerText), excludedIps.map {
                    LabeledItemActionViewHolder(it, R.drawable.ic_clear, removeAction)
                })
                listOf(section)
            } else {
                emptyList()
            }
            // Update the whole section to avoid calling Section.setHeader() and get thus get proper
            // update notifications.
            excludedIpsAdapter.updateAsync(groups)
        })
    }

    private fun addIp(text: String) {
        val isAdded = viewModel.addAddress(text.trim())
        if (isAdded) {
            binding.inputIp.text = ""
            // ProtonInput doesn't clear error when text changes to empty, do it explicitly.
            binding.inputIp.clearInputError()
        } else {
            binding.inputIp.setInputError(getString(R.string.excludeAlreadyExcluded))
        }
    }

    private fun updateAddButtonState(text: String) {
        binding.buttonAdd.isEnabled = isValidIp(text)
    }

    private fun onEditorAction(actionId: Int): Boolean {
        val isActionDone = actionId == EditorInfo.IME_ACTION_DONE
        if (isActionDone) {
            val text = binding.inputIp.text.toString()
            if (isValidIp(text)) {
                addIp(text)
            } else {
                binding.inputIp.setInputError(getString(R.string.inputIpAddressErrorInvalid))
            }
        }
        return isActionDone
    }

    private fun confirmRemove(item: LabeledItem) {
        MaterialDialog.Builder(this).theme(Theme.DARK)
            .title(R.string.warning)
            .content(R.string.removeExcludedIpDialogDescription)
            .positiveText(R.string.yes)
            .onPositive { _: MaterialDialog?, _: DialogAction? -> viewModel.removeAddress(item) }
            .negativeText(R.string.cancel)
            .show()
    }

    private fun isValidIp(ip: String) = Patterns.IP_ADDRESS.matcher(ip).matches()
}
