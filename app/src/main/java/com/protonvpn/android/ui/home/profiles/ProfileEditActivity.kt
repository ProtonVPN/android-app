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
import android.text.Editable
import android.view.Menu
import android.view.View
import android.widget.GridLayout
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.internal.TextWatcherAdapter
import com.protonvpn.android.R
import com.protonvpn.android.components.IntentExtras
import com.protonvpn.android.components.ProtonColorCircle
import com.protonvpn.android.databinding.ActivityProfileEditBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.ui.ProtocolSelectionActivity
import com.protonvpn.android.ui.SaveableSettingsActivity
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreDialogActivity
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.ViewUtils.hideKeyboard
import com.protonvpn.android.utils.ViewUtils.toPx
import com.protonvpn.android.utils.ViewUtils.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import me.proton.core.presentation.ui.view.ProtonAutoCompleteInput

@AndroidEntryPoint
class ProfileEditActivity : SaveableSettingsActivity<ProfileEditViewModel>() {

    private val binding by viewBinding(ActivityProfileEditBinding::inflate)
    private val paletteViews = mutableMapOf<ProtonColorCircle, ProfileColor>()

    private val countrySelection = registerForActivityResult(CountrySelectionActivity.createContract()) {
        if (it != null) viewModel.setCountryCode(it)
    }
    private val serverSelection = registerForActivityResult(ServerSelectionActivity.createContract()) {
        if (it != null) viewModel.setServer(it)
    }
    private val protocolSelection = registerForActivityResult(ProtocolSelectionActivity.createContract()) {
        if (it != null) viewModel.setProtocol(it)
    }

    override val viewModel: ProfileEditViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val profile = intent.getSerializableExtra(IntentExtras.EXTRA_PROFILE) as Profile?
        viewModel.initWithProfile(this, profile)
        initToolbarWithUpEnabled(binding.contentAppbar.toolbar)
        initProfileName(profile)
        initPalette()
        initDeleteButton()
        initServerAndProtocolFields()
        initSecureCoreSwitch()

        viewModel.profileColor.asLiveData().observe(this, Observer { setColorChecked(it) })
        viewModel.protocol.asLiveData().observe(this, Observer { updateProtocolField(it) })
        viewModel.serverViewState.asLiveData().observe(this, Observer {
            updateServerFields(it)
        })
        viewModel.eventSomethingWrong.asLiveData().observe(this, Observer {
            snackbarHelper.errorSnack(R.string.something_went_wrong)
        })
        viewModel.eventValidationFailed.asLiveData().observe(this, Observer {
            updateErrors(it)
        })
    }

    private fun initProfileName(profile: Profile?) = with(binding.contentProfile) {
        if (profile != null) inputName.text = profile.getDisplayName(this@ProfileEditActivity)
        inputName.addTextChangedListener(object : TextWatcherAdapter() {
            override fun afterTextChanged(s: Editable) {
                viewModel.onProfileNameTextChanged(s.toString())
            }
        })
    }

    private fun initPalette() {
        ProfileColor.values().forEach { profileColor ->
            val circle = ProtonColorCircle(this)
            binding.contentProfile.layoutPalette.addView(circle)
            circle.setColor(profileColor.colorRes)
            paletteViews[circle] = profileColor
            circle.updateLayoutParams<GridLayout.LayoutParams> {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER, 1f)
                topMargin = 8.toPx()
                bottomMargin = 8.toPx()
            }
            circle.setOnClickListener(this::selectProfileColor)
        }
    }

    private fun selectProfileColor(colorView: View) {
        viewModel.setProfileColor(requireNotNull(paletteViews[colorView]))
    }

    private fun setColorChecked(newColor: ProfileColor) {
        paletteViews.forEach { (view, color) ->
            view.setChecked(color == newColor, true)
        }
    }

    private fun initServerAndProtocolFields() = with(binding.contentProfile) {
        inputCountry.setOnClickListener { view ->
            hideKeyboard()
            view.clearFocus()
            countrySelection.launch(viewModel.isSecureCoreEnabled)
        }
        inputServer.setOnClickListener { view ->
            hideKeyboard()
            view.clearFocus()
            serverSelection.launch(
                ServerSelectionActivity.Config(
                    requireNotNull(viewModel.selectedCountryCode),
                    viewModel.isSecureCoreEnabled
                )
            )
        }
        inputProtocol.setOnClickListener { view ->
            hideKeyboard()
            view.clearFocus()
            protocolSelection.launch(viewModel.protocol.value)
        }
    }

    private fun updateProtocolField(protocol: ProtocolSelection) {
        binding.contentProfile.inputProtocol.text = getString(protocol.displayName)
    }

    private fun updateServerFields(state: ProfileEditViewModel.ServerViewState) {
        with(binding.contentProfile) {
            checkboxSecureCore.isChecked = state.secureCore
            inputCountry.text = state.countryName
            if (inputCountry.labelText != getString(state.serverLabel))
                inputCountry.clearInputError()
            inputCountry.labelText = getString(state.countryLabel)
            inputServer.text = if (state.serverNameRes != 0) {
                getString(state.serverNameRes, state.serverNameValue)
            } else {
                state.serverNameValue
            }
            inputServer.isVisible = state.serverNameVisible
            inputServer.labelText = getString(state.serverLabel)
            inputServer.hintText = getString(state.serverHint)
        }
    }

    private fun initDeleteButton() {
        if (viewModel.canDeleteProfile) {
            with(binding.contentProfile.buttonDelete) {
                isVisible = true
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@ProfileEditActivity)
                        .setMessage(R.string.deleteProfile)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            viewModel.deleteProfile()
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun initSecureCoreSwitch() = with(binding.contentProfile) {
        checkboxSecureCore.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSecureCore(isChecked)
        }
        buttonUpgrade.setOnClickListener { launchActivity<UpgradeSecureCoreDialogActivity>() }
        layoutSecureCoreUpgrade.isVisible = !viewModel.isSecureCoreAvailable
        checkboxSecureCore.isVisible = viewModel.isSecureCoreAvailable
    }

    private fun updateErrors(errors: ProfileEditViewModel.InputValidation) {
        with(binding.contentProfile) {
            if (errors.profileNameError != 0)
                inputName.setInputError(getString(errors.profileNameError))
            else
                inputName.clearInputError()
            updateError(inputCountry, errors.countryError)
            updateError(inputServer, errors.serverError)
        }
    }

    private fun updateError(input: ProtonAutoCompleteInput, errorRes: Int) {
        if (errorRes != 0) input.setInputError(getString(errorRes)) else input.clearInputError()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.findItem(R.id.action_save).setTitle(viewModel.saveButtonLabel)
        return true
    }

    companion object {

        fun navigateForCreation(fragment: ProfilesFragment) {
            fragment.startActivity(Intent(fragment.activity, ProfileEditActivity::class.java))
        }

        fun navigateForEdit(fragment: ProfilesFragment, profileToEdit: Profile) {
            val intent = Intent(fragment.activity, ProfileEditActivity::class.java)
            intent.putExtra(IntentExtras.EXTRA_PROFILE, profileToEdit)
            fragment.startActivity(intent)
        }
    }
}
