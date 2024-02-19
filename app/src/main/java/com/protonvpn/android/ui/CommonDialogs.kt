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

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.databinding.DialogContentWithCheckboxBinding
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.openProtonUrl

fun showDialogWithDontShowAgain(
    context: Context,
    title: CharSequence?,
    message: CharSequence,
    @StringRes positiveButtonRes: Int,
    @StringRes negativeButtonRes: Int,
    showDialogPrefsKey: String,
    learnMoreUrl: String?,
    onAccepted: () -> Unit
) {
    if (!Storage.getBoolean(showDialogPrefsKey, true)) {
        onAccepted()
        return
    }

    val dialogBuilder = MaterialAlertDialogBuilder(context)

    val contentBinding =
        DialogContentWithCheckboxBinding.inflate(LayoutInflater.from(dialogBuilder.context))

    with(contentBinding) {
        textMessage.setText(message)
        checkboxDontShowAgain.setText(R.string.dialogDontShowAgain)
        if (learnMoreUrl != null) {
            textLearnMore.visibility = View.VISIBLE
            textLearnMore.setOnClickListener {
                context.openProtonUrl(learnMoreUrl)
            }
        }
    }

    val positiveCallback = DialogInterface.OnClickListener { _, _ ->
        val dontShowAgain = contentBinding.checkboxDontShowAgain.isChecked
        Storage.saveBoolean(showDialogPrefsKey, !dontShowAgain)
        onAccepted()
    }

    dialogBuilder
        .setTitle(title)
        .setPositiveButton(positiveButtonRes, positiveCallback)
        .setNegativeButton(negativeButtonRes, null)
        .setView(contentBinding.root)
        .show()
}
