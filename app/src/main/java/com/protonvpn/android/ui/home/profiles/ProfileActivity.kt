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
import android.widget.GridLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.IntentExtras
import com.protonvpn.android.components.ProtonColorCircle
import com.protonvpn.android.databinding.ActivityProfileBinding
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ProfileColor
import com.protonvpn.android.ui.ProtocolSelection
import com.protonvpn.android.ui.ProtocolSelectionActivity
import com.protonvpn.android.utils.ViewUtils.hideKeyboard
import com.protonvpn.android.utils.ViewUtils.toPx
import me.proton.core.presentation.ui.view.ProtonAutoCompleteInput
import javax.inject.Inject

@ContentLayout(R.layout.activity_profile)
class ProfileActivity : BaseActivityV2<ActivityProfileBinding, ProfileViewModel>() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    private val paletteViews = mutableMapOf<ProtonColorCircle, ProfileColor>()

    val countrySelection = registerForActivityResult(CountrySelectionActivity.createContract()) {
        if (it != null) viewModel.setCountryCode(it)
    }
    val serverSelection = registerForActivityResult(ServerSelectionActivity.createContract()) {
        if (it != null) viewModel.setServer(it)
    }
    val protocolSelection = registerForActivityResult(ProtocolSelectionActivity.createContract()) {
        if (it != null) viewModel.setProtocol(it)
    }

    override fun initViewModel() {
        viewModel =
                ViewModelProvider(this, viewModelFactory).get(ProfileViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = intent.getSerializableExtra(IntentExtras.EXTRA_PROFILE) as Profile?
        viewModel.initWithProfile(profile)
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
            Toast
                .makeText(this@ProfileActivity, R.string.something_went_wrong, Toast.LENGTH_SHORT)
                .show()
        })
    }

    private fun initProfileName(profile: Profile?) {
        if (profile != null) binding.contentProfile.inputName.text = profile.name
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

    private fun updateServerFields(state: ProfileViewModel.ServerViewState) {
        with(binding.contentProfile) {
            switchSecureCore.isChecked = state.secureCore
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
                    MaterialDialog.Builder(this@ProfileActivity).theme(Theme.DARK)
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
                }
            }
        }
    }

    private fun initSecureCoreSwitch() {
        binding.contentProfile.switchSecureCore.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSecureCore(isChecked)
        }
    }

    private fun updateErrors(errors: ProfileViewModel.InputValidation) {
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

    override fun onBackPressed() {
        if (viewModel.hasUnsavedChanges(binding.contentProfile.inputName.text.toString())) {
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
        menu.findItem(R.id.action_save).setTitle(viewModel.saveButtonLabel)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_save) {
            with(binding.contentProfile) {
                val validation = viewModel.verifyInput(inputName.text.toString())
                updateErrors(validation)
                if (validation.hasNoError) {
                    viewModel.saveProfile(inputName.text.toString())
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
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
