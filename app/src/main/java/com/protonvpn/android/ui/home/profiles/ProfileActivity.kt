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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import butterknife.BindView
import butterknife.OnCheckedChanged
import butterknife.OnClick
import butterknife.OnTextChanged
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.textfield.TextInputLayout
import com.protonvpn.android.R
import com.protonvpn.android.bus.EventBus
import com.protonvpn.android.bus.OnProfilesChanged
import com.protonvpn.android.components.BaseActivity
import com.protonvpn.android.components.ContentLayout
import com.protonvpn.android.components.IntentExtras
import com.protonvpn.android.components.ProtonPallete
import com.protonvpn.android.components.ProtonSpinner
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.ui.drawer.SettingsActivity
import com.protonvpn.android.utils.ServerManager
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_protocol_selection.*
import rx_activity_result2.RxActivityResult

@ContentLayout(R.layout.activity_profile)
class ProfileActivity : BaseActivity() {

    @BindView(R.id.spinnerCountry) lateinit var spinnerCountry: ProtonSpinner<VpnCountry>
    @BindView(R.id.spinnerServer) lateinit var spinnerServer: ProtonSpinner<ServerWrapper>
    @BindView(R.id.palette) lateinit var palette: ProtonPallete
    @BindView(R.id.inputName) lateinit var inputName: TextInputLayout
    @BindView(R.id.inputLayoutCountry) lateinit var inputLayoutCountry: TextInputLayout
    @BindView(R.id.inputLayoutServer) lateinit var inputLayoutServer: TextInputLayout
    @BindView(R.id.editName) lateinit var editName: EditText
    @BindView(R.id.switchSecureCore) lateinit var switchSecureCore: SwitchCompat
    @BindView(R.id.coordinator) lateinit var coordinator: CoordinatorLayout
    @BindView(R.id.spinnerTransmissionProtocol)
    lateinit var spinnerTransmissionProtocol: ProtonSpinner<SettingsActivity.MockUDP>
    @BindView(R.id.spinnerDefaultProtocol)
    lateinit var spinnerDefaultProtocol: ProtonSpinner<SettingsActivity.MockUDP>
    private var editableProfile: Profile? = null
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var userData: UserData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editableProfile = intent.getSerializableExtra(IntentExtras.EXTRA_PROFILE) as Profile?
        if (editableProfile != null) {
            editableProfile!!.serverWrapper.setDeliverer(serverManager)
            initSpinners(editableProfile!!.isSecureCore)
            initProfileEdit(editableProfile!!)
        } else {
            initSpinners(false)
            palette.setSelectedColor(Profile.getRandomProfileColor(this), false)
        }
        addHideKeyboard(coordinator)
    }

    private fun addHideKeyboard(view: View?) {
        if (view !is EditText) {
            view!!.setOnTouchListener { _, _ ->
                hideKeyboard(editName)
                false
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val innerView = view.getChildAt(i)
                addHideKeyboard(innerView)
            }
        }
    }

    @OnTextChanged(R.id.editName)
    fun onTextChanged() {
        if (!TextUtils.isEmpty(inputName.error)) {
            inputName.error = ""
        }
    }

    private fun initProfileEdit(profile: Profile) {
        val server = profile.server!!

        val country = serverManager
                .getVpnExitCountry(if (server.isSecureCoreServer) server.exitCountry else server.flag,
                        server.isSecureCoreServer)

        editName.setText(profile.name)
        palette.setSelectedColor(profile.color, false)
        spinnerCountry.selectedItem = country
        spinnerServer.selectedItem = profile.serverWrapper
        spinnerServer.setItems(country!!.wrapperServers)
        spinnerServer.isEnabled = true
    }

    private fun initSpinners(secureCoreEnabled: Boolean) {
        spinnerCountry.setText("")
        spinnerServer.setText("")
        switchSecureCore.isChecked = secureCoreEnabled
        inputLayoutCountry.hint =
                getString(if (secureCoreEnabled) R.string.exitCountry else R.string.country)
        inputLayoutServer.hint =
                getString(if (secureCoreEnabled) R.string.entryCountry else R.string.serverSelection)

        spinnerServer.isEnabled = false
        spinnerCountry.setItems(
                if (secureCoreEnabled) serverManager.secureCoreExitCountries else serverManager.vpnCountries)
        spinnerCountry.setOnItemSelectedListener { item, _ ->
            spinnerServer.setItems(item.wrapperServers)
            spinnerServer.isEnabled = true
            spinnerServer.setText("")
            inputLayoutCountry.error = ""
        }
        spinnerServer.setOnItemSelectedListener { _, _ ->
            inputLayoutServer.error = ""
        }
        spinnerServer.setOnValidateSelection { item -> userData.hasAccessToServer(serverManager.getServerFromWrap(item)) }
        initProtocolSelection()
    }

    @OnCheckedChanged(R.id.switchSecureCore)
    fun switchSecureCore(isChecked: Boolean) {
        initSpinners(isChecked)
    }

    @OnClick(R.id.fabSave)
    fun onSave() {
        if (checkInput() && checkForDuplicates() && isProfileColorSelected()) {
            val newProfile = Profile(editName.text.toString(), palette.selectedColor,
                    spinnerServer.selectedItem!!)
            newProfile.setTransmissionProtocol(spinnerTransmissionProtocol.selectedItem!!.label)
            newProfile.setProtocol(spinnerDefaultProtocol.selectedItem!!.label)
            newProfile.serverWrapper.setSecureCoreCountry(switchSecureCore.isChecked)
            if (editableProfile != null) {
                serverManager.editProfile(editableProfile, newProfile)
            } else {
                serverManager.addToProfileList(newProfile)
            }

            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun checkTransmissionLayoutVisibility(protocol: String) {
        layoutTransmissionProtocol.visibility =
                if (protocol == "OpenVPN") View.VISIBLE else View.GONE
    }

    private fun initProtocolSelection() {
        spinnerDefaultProtocol.selectedItem =
                SettingsActivity.MockUDP(if (editableProfile == null) userData.selectedProtocol.toString() else editableProfile!!.getProtocol(userData))
        spinnerDefaultProtocol.setItems(listOf(SettingsActivity.MockUDP(VpnProtocol.IKEv2.toString()), SettingsActivity.MockUDP(VpnProtocol.OpenVPN.toString())))
        spinnerDefaultProtocol.setOnItemSelectedListener { item, _ ->
            checkTransmissionLayoutVisibility(item.label)
        }
        checkTransmissionLayoutVisibility(if (editableProfile == null) userData.selectedProtocol.toString() else editableProfile!!.getProtocol(userData))
        spinnerTransmissionProtocol.selectedItem =
                SettingsActivity.MockUDP(if (editableProfile == null) userData.transmissionProtocol else editableProfile!!.getTransmissionProtocol(userData))
        spinnerTransmissionProtocol.setItems(listOf(SettingsActivity.MockUDP(TransmissionProtocol.UDP.toString()), SettingsActivity.MockUDP(TransmissionProtocol.TCP.toString())))
    }

    private fun isProfileColorSelected(): Boolean {
        if (palette.selectedColor.isEmpty()) {
            Toast.makeText(this, R.string.selectedProfileColor, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun checkForDuplicates(): Boolean {
        for (profile in serverManager.savedProfiles) {
            if (profile.serverWrapper == spinnerServer.selectedItem &&
                    profile.getTransmissionProtocol(userData) == spinnerTransmissionProtocol.selectedItem?.label &&
                    profile.getProtocol(userData) == spinnerDefaultProtocol.selectedItem?.label) {

                if (editableProfile != null && editableProfile!!.server == profile.server && editableProfile!!.getProtocol(userData) == profile.getProtocol(userData)) {
                    return true
                }
                Toast.makeText(context, getString(R.string.profileErrorDuplicate, profile.name),
                        Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun checkInput(): Boolean {
        return (inputIsCorrect(inputName, editName, R.string.errorEmptyName) && inputIsCorrect(
                inputLayoutCountry, spinnerCountry,
                if (switchSecureCore.isChecked) R.string.errorEmptyExitCountry else R.string.errorEmptyCountry) &&
                inputIsCorrect(inputLayoutServer, spinnerServer,
                        if (switchSecureCore.isChecked) R.string.errorEmptyEntryCountry else R.string.errorEmptyServer))
    }

    private fun inputIsCorrect(errorLayout: TextInputLayout?, editText: EditText, @StringRes stringId: Int): Boolean {
        if (editText.text.toString().isEmpty()) {
            errorLayout!!.error = getString(stringId)
            return false
        }
        return true
    }

    private fun hasUnchangedSettings(): Boolean {
        val currentName = editName.text.toString()
        return if (editableProfile != null) {
            editableProfile!!.serverWrapper != spinnerServer.selectedItem || currentName.isNotEmpty() && currentName != editableProfile!!.name
        } else currentName.isNotEmpty() || spinnerCountry.text!!.toString().isNotEmpty() ||
                spinnerServer.text!!.toString().isNotEmpty()
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
        menu.findItem(R.id.action_delete).isVisible = editableProfile != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_delete) {
            MaterialDialog.Builder(this).theme(Theme.DARK)
                    .title(R.string.warning)
                    .content(R.string.deleteProfile)
                    .positiveText(R.string.delete)
                    .onPositive { _, _ ->
                        serverManager.deleteProfile(editableProfile)
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

        fun navigateForCreation(fromActivity: ProfilesFragment) {
            startForResult(fromActivity, Intent(fromActivity.activity, ProfileActivity::class.java))
        }

        fun navigateForEdit(fromActivity: ProfilesFragment, profileToEdit: Profile) {
            val intent = Intent(fromActivity.activity, ProfileActivity::class.java)
            intent.putExtra(IntentExtras.EXTRA_PROFILE, profileToEdit)
            startForResult(fromActivity, intent)
        }

        @SuppressLint("CheckResult")
        private fun startForResult(fromActivity: ProfilesFragment, intent: Intent) {
            RxActivityResult.on(fromActivity).startIntent(intent).subscribe { result ->
                if (result.resultCode() == Activity.RESULT_OK) {
                    EventBus.post(OnProfilesChanged.INSTANCE)
                }
            }
        }
    }
}
