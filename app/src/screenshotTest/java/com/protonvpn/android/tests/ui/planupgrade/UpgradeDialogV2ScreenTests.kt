/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tests.ui.planupgrade

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.ui.planupgrade.comparison_table.PlanUpgradeDialog
import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeContentProvider
import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2.BenefitsViewState

@PreviewTest
@ProtonVpnTestPreview
@Composable
private fun PreviewPlanUpgradeDialog(
    @PreviewParameter(UpgradeContentProvider::class) benefitsViewState: BenefitsViewState
) {
    ProtonVpnPreview {
        PlanUpgradeDialog(benefitsViewState, {}, Modifier.fillMaxSize())
    }
}