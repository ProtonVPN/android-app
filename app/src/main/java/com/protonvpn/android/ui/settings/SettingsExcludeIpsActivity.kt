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
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ActivitySettingsExcludeIpsBinding
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.ui.HeaderViewHolder
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.utils.DefaultTextWatcher
import com.protonvpn.android.utils.ViewUtils.viewBinding
import com.protonvpn.android.utils.getSerializableExtraCompat
import com.protonvpn.android.utils.setMinSizeTouchDelegate
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Section
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.R as CoreR

@AndroidEntryPoint
class SettingsExcludeIpsActivity : SaveableSettingsActivity<SettingsExcludeIpsViewModel>() {

    private val binding by viewBinding(ActivitySettingsExcludeIpsBinding::inflate)
    override val viewModel: SettingsExcludeIpsViewModel by viewModels()
    private lateinit var mode: SplitTunnelingMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        mode = requireNotNull(intent.getSerializableExtraCompat<SplitTunnelingMode>(SPLIT_TUNNELING_MODE_KEY))
        title = when (mode) {
            SplitTunnelingMode.INCLUDE_ONLY -> getString(R.string.settings_split_tunneling_included_ips)
            SplitTunnelingMode.EXCLUDE_ONLY -> getString(R.string.settings_split_tunneling_excluded_ips)
        }
        initToolbarWithUpEnabled(binding.appbar.toolbar)

        val selectedIpsAdapter = GroupAdapter<GroupieViewHolder>()

        with(binding) {
            buttonAdd.setMinSizeTouchDelegate()
            buttonAdd.setOnClickListener {
                addIp(inputIp.text.toString())
            }
            updateAddButtonState(inputIp.text.toString())

            inputIp.addTextChangedListener(object : DefaultTextWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    updateAddButtonState(editable.toString())
                }
            })
            val editorActionListener = TextView.OnEditorActionListener { _, actionId, _ ->
                onEditorAction(actionId)
            }
            inputIp.setOnEditorActionListener(editorActionListener)

            recyclerSelectedIps.adapter = selectedIpsAdapter
            recyclerSelectedIps.layoutManager = LinearLayoutManager(this@SettingsExcludeIpsActivity)
            // Disable change animations for instant header updates.
            (recyclerSelectedIps.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        val removeAction = { item: LabeledItem -> confirmRemove(item) }
        viewModel.ipAddressItems.asLiveData().observe(this, Observer { selectedIps ->
            val groups = if (selectedIps.isNotEmpty()) {
                val headerTextRes = when (mode) {
                    SplitTunnelingMode.INCLUDE_ONLY -> R.string.settingsIncludedIPAddressesListHeader
                    SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settingsExcludedIPAddressesListHeader
                }
                val headerText = getString(headerTextRes, selectedIps.size)

                val section = Section(HeaderViewHolder(text = headerText), selectedIps.map {
                    LabeledItemActionViewHolder(it, CoreR.drawable.ic_proton_cross, removeAction)
                })
                listOf(section)
            } else {
                emptyList()
            }
            // Update the whole section to avoid calling Section.setHeader() and get thus get proper
            // update notifications.
            selectedIpsAdapter.updateAsync(groups)
        })
    }

    private fun addIp(text: String) {
        val isAdded = viewModel.addAddress(text.trim())
        if (isAdded) {
            binding.inputIp.text = ""
            // ProtonInput doesn't clear error when text changes to empty, do it explicitly.
            binding.inputIp.clearInputError()
        } else {
            val errorRes = when( mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> R.string.settings_split_tunneling_already_included
                SplitTunnelingMode.EXCLUDE_ONLY -> R.string.settings_split_tunneling_already_excluded
            }
            binding.inputIp.setInputError(getString(errorRes))
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
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.settings_split_tunneling_remove_ip_dialog_message)
            .setPositiveButton(R.string.remove) { _, _ -> viewModel.removeAddress(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isValidIp(ip: String) = Patterns.IP_ADDRESS.matcher(ip).matches()

    companion object {
        const val SPLIT_TUNNELING_MODE_KEY = "split tunneling mode"

        fun createContract() = createContract<SplitTunnelingMode>(SettingsExcludeIpsActivity::class) { mode ->
            putExtra(SPLIT_TUNNELING_MODE_KEY, mode)
        }
    }
}
