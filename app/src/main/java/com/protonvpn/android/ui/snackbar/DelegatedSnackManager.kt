/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.snackbar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar
import com.protonvpn.android.R
import me.proton.core.presentation.utils.SnackType
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val SNACK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5)

/**
 * A mechanism for delegating showing a Snackbar to another activity.
 * Typical use case is when an activity closes and wants to show a message confirming that an
 * action has been performed (e.g. "Bug report sent"). The message is shown in a Snackbar in
 * the next activity on the back stack that is resumed.
 * A delegated snack is valid only for a few seconds so that if it doesn't have a chance to display
 * right away it won't appear anywhere later.
 *
 * Usage:
 *   in the delegating activity:
 *
 *     fun onSuccess() {
 *       delegatedSnackManager.post("Saved", true)
 *       finish()
 *     }
 *
 *   in all activities:
 *
 *     override fun onCreate(...) {
 *       ...
 *       DelegatedSnackHelper(this, viewBinding.root, delegatedSnackManager)
 *     }
 */
@Singleton
class DelegatedSnackManager(private val monoClock: () -> Long) {

    enum class SnackActionType(@StringRes val text: Int) {
        GOT_IT(R.string.got_it)
    }

    data class Snack(
        val timestampMs: Long,
        val text: String,
        val type: SnackType,
        val action: SnackActionType?,
        val length: Int
    )

    private var pendingSnack: Snack? = null

    fun postSnack(
        text: String,
        type: SnackType,
        action: SnackActionType? = null,
        length: Int = Snackbar.LENGTH_LONG
    ) {
        pendingSnack = Snack(monoClock(), text, type, action, length)
    }

    fun getPendingSnackOrNull(): Snack? {
        val snack = pendingSnack
        pendingSnack = null
        val now = monoClock()
        return snack?.takeIf { it.timestampMs > now - SNACK_TIMEOUT_MS }
    }
}
