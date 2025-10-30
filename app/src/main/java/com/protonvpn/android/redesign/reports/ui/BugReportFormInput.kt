/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.redesign.reports.ui

import com.protonvpn.android.models.config.bugreport.DropdownField

sealed interface BugReportFormInput {

    val value: String

    val isError: Boolean

    val isMandatory: Boolean

    val submitValue: String?

    fun copy(value: String = this.value, isError: Boolean = this.isError): BugReportFormInput

    data class Dropdown(
        override val value: String,
        override val isError: Boolean,
        override val isMandatory: Boolean,
        private val dropdownOptions: List<DropdownField>,
    ) : BugReportFormInput {

        override val submitValue: String? = dropdownOptions
            .firstOrNull { dropdownOption -> dropdownOption.label == value }
            ?.submitLabel

        override fun copy(value: String, isError: Boolean): BugReportFormInput = copy(
            value = value,
            isError = isError,
            isMandatory = this.isMandatory,
            dropdownOptions = this.dropdownOptions
        )
    }

    data class TextField(
        override val value: String,
        override val isError: Boolean,
        override val isMandatory: Boolean,
    ) : BugReportFormInput {

        override val submitValue: String = value

        override fun copy(value: String, isError: Boolean): BugReportFormInput = copy(
            value = value,
            isError = isError,
            isMandatory = this.isMandatory,
        )
    }

}
