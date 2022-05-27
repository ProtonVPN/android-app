/*
 * Copyright (c) 2022. Proton AG
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
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.protonvpn.android.R
import com.protonvpn.android.databinding.DialogNewLookBinding
import com.protonvpn.android.databinding.DialogTvNewLookBinding
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.HtmlTools
import com.protonvpn.android.utils.Storage
import javax.inject.Inject

private const val PREF_NEW_LOOK_DIALOG_NEEDED = "PREF_NEW_LOOK_DIALOG_NEEDED"
private const val PREF_NEW_LOOK_DIALOG_SHOWN = "PREF_NEW_LOOK_DIALOG_SHOWN"

open class NewLookDialogProvider @Inject constructor() {

    open fun show(context: Context, tv: Boolean) {
        if (shouldShowDialog()) {
            if (tv) showTv(context)
            else showMaterial(context)

            dialogShown()
        }
    }

    fun noNewLookDialogNeeded() {
        Storage.saveBoolean(PREF_NEW_LOOK_DIALOG_NEEDED, false)
    }

    private fun showMaterial(context: Context) {
        val builder = MaterialAlertDialogBuilder(context)
        val binding = DialogNewLookBinding.inflate(LayoutInflater.from(builder.context))

        var dialog: AlertDialog? = null
        with(binding) {
            setDialogMessageText(textMessage)
            buttonGotIt.setOnClickListener { dialog?.dismiss() }
        }

        dialog = builder
            .setView(binding.root)
            .show()
    }

    private fun showTv(context: Context) {
        val builder = MaterialDialog.Builder(context).theme(Theme.DARK)
        val binding = DialogTvNewLookBinding.inflate(LayoutInflater.from(builder.context))

        var dialog: MaterialDialog? = null
        with(binding.buttonGotIt) {
            setOnClickListener { dialog?.dismiss() }
            requestFocus()
        }

        setDialogMessageText(binding.textMessage)

        dialog = builder
            .customView(binding.root, false)
            .show()
    }

    private fun setDialogMessageText(textView: TextView) {
        textView.text = HtmlTools.fromHtml(
            textView.resources.getString(R.string.new_look_dialog_message_new_plans, Constants.NEW_LOOK_INFO_URL)
        )
        textView.movementMethod = LinkMovementMethod()
    }

    private fun shouldShowDialog() =
        Storage.getBoolean(PREF_NEW_LOOK_DIALOG_NEEDED, true) && !Storage.getBoolean(PREF_NEW_LOOK_DIALOG_SHOWN)

    private fun dialogShown() {
        Storage.saveBoolean(PREF_NEW_LOOK_DIALOG_SHOWN, true)
    }
}
