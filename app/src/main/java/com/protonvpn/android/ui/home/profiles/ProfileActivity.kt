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
import android.view.View
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.textfield.TextInputLayout
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.IntentExtras
import com.protonvpn.android.components.ProtonColorCircle
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.databinding.ActivityProfileBinding
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import javax.inject.Inject

@ContentLayout(R.layout.activity_profile)
class ProfileActivity : BaseActivityV2<ActivityProfileBinding, ProfileViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private val paletteViews = mutableMapOf<ProtonColorCircle, ProfileColor>()

    override fun initViewModel() {
        viewModel =
                ViewModelProviders.of(this, viewModelFactory).get(ProfileViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = intent.getSerializableExtra(IntentExtras.EXTRA_PROFILE) as Profile?
        viewModel.initWithProfile(profile)
        initPalette()
        initSaveButton()
        initServerSelection()
        initProtocolSelection()
        initSecureCoreSwitch()
        initEditableProfile(profile)

        viewModel.selectedColor.observe(this, Observer { setColorChecked(it) })
    }

    private fun checkInputFields(): Boolean {
        with(binding.contentProfile) {
            val nameNotEmpty = editTextNotEmpty(inputName, editName, R.string.errorEmptyName)
            val countryNotEmpty = editTextNotEmpty(
                    inputLayoutCountry, spinnerCountry,
                    if (switchSecureCore.isChecked) R.string.errorEmptyExitCountry else R.string.errorEmptyCountry)
            val serverNotEmpty = editTextNotEmpty(inputLayoutServer, spinnerServer,
                    if (switchSecureCore.isChecked) R.string.errorEmptyEntryCountry else R.string.errorEmptyServer)
            return nameNotEmpty && countryNotEmpty && serverNotEmpty
        }
    }

    private fun initPalette() {
        ProfileColor.values().forEach { profileColor ->
            val circle = ProtonColorCircle(this)
            binding.contentProfile.layoutPalette.addView(circle);
            circle.setColor(profileColor.colorRes)
            paletteViews[circle] = profileColor
            circle.setOnClickListener(this::selectProfileColor)
        }
    }

    private fun selectProfileColor(colorView: View) {
        viewModel.selectProfileColor(requireNotNull(paletteViews.get(colorView)))
    }

    private fun setColorChecked(newColor: ProfileColor) {
        paletteViews.forEach { (view, color) ->
            view.setChecked(color == newColor, true)
        }
    }

    private fun initServerSelection() {
        with(binding.contentProfile) {
            val spinnerCountry = spinnerCountry as ProtonSpinner<VpnCountry>
            spinnerCountry.setText("")
            spinnerCountry.setItems(viewModel.getCountryItems())
            spinnerCountry.setOnItemSelectedListener { item, _ ->
                spinnerServer.setItems(item.wrapperServers)
                inputLayoutServer.isVisible = true
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

            inputLayoutServer.isVisible = false
            spinnerServer.setOnItemSelectedListener { _, _ ->
                inputLayoutServer.error = ""
            }
            spinnerServer.setOnValidateSelection(viewModel.serverValidateSelection)
        }
    }

    private fun initSaveButton() {
        with(binding.contentProfile) {
            binding.fabSave.setOnClickListener {
                if (checkInputFields()) {
                    val serverWrapper = spinnerServer.selectedItem as ServerWrapper
                    serverWrapper.setSecureCore(switchSecureCore.isChecked)
                    viewModel.saveProfile(
                        editName.text.toString(),
                        serverWrapper,
                        protocolSelection.transmissionProtocol,
                        protocolSelection.protocol
                    )
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
        protocolSelection.init(protocol == VpnProtocol.Smart, manualProtocol,
                viewModel.transmissionProtocol) { }
    }

    private fun editTextNotEmpty(errorLayout: TextInputLayout?, editText: EditText, @StringRes errorId: Int): Boolean {
        if (editText.text.toString().isEmpty()) {
            errorLayout!!.error = getString(errorId)
            return false
        }
        return true
    }

    private fun initEditableProfile(profile: Profile?) {
        val server = profile?.server
        with(binding.contentProfile) {
            val spinnerCountry = spinnerCountry as ProtonSpinner<VpnCountry>
            val spinnerServer = spinnerServer as ProtonSpinner<ServerWrapper>
            editName.setText(profile?.getDisplayName(baseContext))

            // Profile server or country might be null if it was removed from API server list responses
            if (server != null) {
                val country = viewModel.getServerCountry(server)
                if (country != null) {
                    spinnerCountry.selectedItem = country
                    spinnerServer.selectedItem = profile.wrapper
                    spinnerServer.setItems(country.wrapperServers)
                    inputLayoutServer.isVisible = true
                }
            }
        }
    }

    private fun hasUnsavedSettings(): Boolean {
        with(binding.contentProfile) {
            val currentName = editName.text.toString()
            return viewModel.hasUnsavedChanges(
                currentName,
                spinnerServer.selectedItem as? ServerWrapper,
                spinnerServer.text?.toString()
            )
        }
    }

    override fun onBackPressed() {
        if (hasUnsavedSettings()) {
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
        menu.findItem(R.id.action_delete).isVisible = viewModel.canDeleteProfile
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
