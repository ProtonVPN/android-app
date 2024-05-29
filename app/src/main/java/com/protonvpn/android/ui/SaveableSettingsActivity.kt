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

package com.protonvpn.android.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseActivityV2
import com.protonvpn.android.utils.launchAndCollectIn
import kotlin.reflect.KClass

/**
 * An Activity for editing settings that need to be explicitly saved.
 *
 * The user is warned against losing changes on navigating back.
 *
 * Use SaveableSettingsActivity.createContract() for starting the activity to get result indicating
 * if anything has been saved.
 */
abstract class SaveableSettingsActivity<VM : SaveableSettingsViewModel> : BaseActivityV2() {

    protected abstract val viewModel: VM

    override fun onBackPressed() {
        viewModel.onGoBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.eventConfirmDiscardChanges.launchAndCollectIn(this) { showDiscardChangesDialog() }
        viewModel.eventGoBack.launchAndCollectIn(this) { super.onBackPressed() }
        viewModel.eventFinishActivity.launchAndCollectIn(this) { anythingSaved ->
            setResultAndFinish(anythingSaved)
        }
    }

    @CallSuper
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_save, menu)
        return true
    }

    @CallSuper
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_save -> {
            viewModel.saveAndClose()
            true
        }
        android.R.id.home -> {
            onBackPressed()
            true
        }
        else -> false
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.discardChanges)
            .setPositiveButton(R.string.discard) { _, _ -> viewModel.onDiscardChanges() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setResultAndFinish(hasSavedChanges: Boolean) {
        setResult(Activity.RESULT_OK, Intent().apply { putExtra(HAS_SAVED_CHANGES_KEY, hasSavedChanges) })
        finish()
    }

    companion object {
        private const val HAS_SAVED_CHANGES_KEY = "hasSavedChanges"

        fun createContract(clazz: KClass<out SaveableSettingsActivity<*>>) =
            createContract<Unit>(clazz) { /* nothing */ }

        fun <Input: Any> createContract(
            clazz: KClass<out SaveableSettingsActivity<*>>,
            inputHandler: Intent.(Input) -> Unit
        ) = object : ActivityResultContract<Input, Boolean?>() {
                override fun createIntent(context: Context, input: Input): Intent =
                    Intent(context, clazz.java).apply { inputHandler(input) }

                override fun parseResult(resultCode: Int, intent: Intent?): Boolean? =
                    if (resultCode == Activity.RESULT_OK) {
                        intent?.getBooleanExtra(HAS_SAVED_CHANGES_KEY, false)
                    } else {
                        null
                    }
            }
    }
}
