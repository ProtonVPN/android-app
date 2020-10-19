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
package com.protonvpn.android.ui.home.profiles

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.textfield.TextInputLayout
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.IntentExtras
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.databinding.ActivityProfileBinding
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import javax.inject.Inject

@ContentLayout(R.layout.activity_profile)
class ProfileActivity : BaseActivityV2<ActivityProfileBinding, ProfileViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun initViewModel() {
        viewModel =
                ViewModelProviders.of(this, viewModelFactory).get(ProfileViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initWithProfile(intent.getSerializableExtra(IntentExtras.EXTRA_PROFILE) as Profile?)
        initSpinners()
        initProtocolSelection()
        initSecureCoreSwitch()
        initEditableProfile()
    }

    private fun checkInputFields(): Boolean {
        with(binding.contentProfile) {
            val nameNotEmpty = editTextNotEmpty(inputName, editName, R.string.errorEmptyName)
            val countryNotEmpty = editTextNotEmpty(
                    inputLayoutCountry, spinnerCountry,
                    if (switchSecureCore.isChecked) R.string.errorEmptyExitCountry else R.string.errorEmptyCountry)
            val serverNotEmpty = editTextNotEmpty(inputLayoutServer, spinnerServer,
                    if (switchSecureCore.isChecked) R.string.errorEmptyEntryCountry else R.string.errorEmptyServer)
            return nameNotEmpty && countryNotEmpty && serverNotEmpty &&
                    profileColorSelected()
        }
    }

    private fun initServerSelection() {
        with(binding.contentProfile) {
            val spinnerCountry = spinnerCountry as ProtonSpinner<VpnCountry>
            spinnerCountry.setText("")
            spinnerCountry.setItems(viewModel.getCountryItems())
            spinnerCountry.setOnItemSelectedListener { item, _ ->
                spinnerServer.setItems(item.wrapperServers)
                spinnerServer.isVisible = true
                spinnerServer.setText("")
                inputLayoutCountry.error = ""
                spinnerServer.selectedItem = item.wrapperServers.firstOrNull()
            }
            val spinnerServer = spinnerServer as ProtonSpinner<ServerWrapper>
            spinnerServer.setText("")

            inputLayoutCountry.hint =
                    getString(if (viewModel.secureCoreEnabled) R.string.exitCountry else R.string.country)
            inputLayoutServer.hint =
                    getString(if (viewModel.secureCoreEnabled) R.string.entryCountry else R.string.serverSelection)

            spinnerServer.isVisible = false
            spinnerServer.setOnItemSelectedListener { _, _ ->
                inputLayoutServer.error = ""
            }
            spinnerServer.setOnValidateSelection(viewModel.serverValidateSelection)
        }
    }

    private fun initSpinners() {
        with(binding.contentProfile) {
            initServerSelection()
            binding.fabSave.setOnClickListener {
                if (checkInputFields()) {
                    val newProfile =
                            Profile(editName.text.toString(), palette.selectedColor,
                                    spinnerServer.selectedItem as ServerWrapper)
                    newProfile.apply {
                        setTransmissionProtocol(protocolSelection.transmissionProtocol.toString())
                        setProtocol(protocolSelection.protocol)
                        wrapper.setSecureCoreCountry(switchSecureCore.isChecked)
                    }
                    viewModel.saveProfile(newProfile)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun initSecureCoreSwitch() {
        with(binding.contentProfile) {
            switchSecureCore.isChecked = viewModel.secureCoreEnabled
            switchSecureCore.setOnCheckedChangeListener { _, isChecked ->
                viewModel.secureCoreEnabled = isChecked
                initServerSelection()
            }
        }
    }

    private fun initProtocolSelection() = with(binding.contentProfile) {
        val protocol = viewModel.selectedProtocol
        val manualProtocol = if (protocol == VpnProtocol.Smart) VpnProtocol.IKEv2 else protocol
        protocolSelection.init(protocol == VpnProtocol.Smart, manualProtocol, viewModel.transmissionProtocol) {
            viewModel.editableProfile?.setProtocol(protocolSelection.protocol)
            viewModel.editableProfile?.setTransmissionProtocol(
                    protocolSelection.transmissionProtocol.toString())
        }
    }

    private fun editTextNotEmpty(errorLayout: TextInputLayout?, editText: EditText, @StringRes errorId: Int): Boolean {
        if (editText.text.toString().isEmpty()) {
            errorLayout!!.error = getString(errorId)
            return false
        }
        return true
    }

    private fun profileColorSelected(): Boolean {
        if (binding.contentProfile.palette.selectedColor.isEmpty()) {
            Toast.makeText(this, R.string.selectedProfileColor, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun initEditableProfile() {
        val profile = viewModel.editableProfile
        val server = viewModel.profileServer
        with(binding.contentProfile) {
            val spinnerCountry = spinnerCountry as ProtonSpinner<VpnCountry>
            val spinnerServer = spinnerServer as ProtonSpinner<ServerWrapper>
            editName.setText(profile?.getDisplayName(baseContext))
            palette.setSelectedColor(profile?.color
                    ?: Profile.getRandomProfileColor(this@ProfileActivity), false)

            // Profile server might be null if it was removed from API server list responses
            if (server != null) {
                val country = viewModel.getServerCountry(server)
                spinnerCountry.selectedItem = country
                spinnerServer.selectedItem = profile?.wrapper
                spinnerServer.setItems(country!!.wrapperServers)
                spinnerServer.isVisible = true
            }
        }
    }

    private fun hasUnchangedSettings(): Boolean {
        with(binding.contentProfile) {
            val currentName = editName.text.toString()
            val editableProfile = viewModel.editableProfile
            return if (editableProfile != null) {
                editableProfile.wrapper != spinnerServer.selectedItem || currentName.isNotEmpty() &&
                        currentName != editableProfile.getDisplayName(baseContext)
            } else currentName.isNotEmpty() || spinnerCountry.text!!.toString().isNotEmpty() ||
                    spinnerServer.text!!.toString().isNotEmpty()
        }
    }

    override fun onBackPressed() {
        if (hasUnchangedSettings()) {
            MaterialDialog.Builder(this).theme(Theme.DARK)
                    .title(R.string.warning)
                    .content(R.string.discardChanges)
                    .positiveText(R.string.discard)
                    .onPositive { _, _ -> finish() }
                    .negativeText(R.string.cancel)
                    .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_profile, menu)
        menu.findItem(R.id.action_delete).isVisible = viewModel.editableProfile != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete) {
            MaterialDialog.Builder(this).theme(Theme.DARK)
                    .title(R.string.warning)
                    .content(R.string.deleteProfile)
                    .positiveText(R.string.delete)
                    .onPositive { _, _ ->
                        viewModel.deleteProfile()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                    .negativeText(R.string.cancel)
                    .show()
            return true
        }
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    companion object {

        fun navigateForCreation(fragment: ProfilesFragment) {
            fragment.startActivity(Intent(fragment.activity, ProfileActivity::class.java))
        }

        fun navigateForEdit(fragment: ProfilesFragment, profileToEdit: Profile) {
            val intent = Intent(fragment.activity, ProfileActivity::class.java)
            intent.putExtra(IntentExtras.EXTRA_PROFILE, profileToEdit)
            fragment.startActivity(intent)
        }
    }
}
