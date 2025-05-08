/*
 * Copyright (c) 2025. Proton AG
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
package com.protonvpn.android.tests.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.protonvpn.android.annotations.ProtonVpnTestPreview
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.widget.ui.OnboardingWidgetBottomSheetContent
import com.protonvpn.android.widget.ui.WidgetAddScreen

@ProtonVpnTestPreview
@Composable
fun WidgetSettingsScreenPreview() {
    ProtonVpnPreview(addSurface = true) {
        WidgetAddScreen(onClose = {})
    }
}

@ProtonVpnTestPreview
@Composable
fun WidgetOnboardingContent() {
    ProtonVpnPreview(addSurface = true) {
        OnboardingWidgetBottomSheetContent({}, {})
    }
}

@ProtonVpnTestPreview
@Composable
fun WidgetOnboardingContentFallback() {
    ProtonVpnPreview(addSurface = true) {
        OnboardingWidgetBottomSheetContent({}, null)
    }
}
