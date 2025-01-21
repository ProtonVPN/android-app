/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.tests.bottomSheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.InfoSheet
import com.protonvpn.android.redesign.base.ui.InfoSheetContent
import com.protonvpn.android.redesign.base.ui.InfoSheetState
import com.protonvpn.android.redesign.base.ui.InfoType

@Preview
@Composable
private fun Previews(
    @PreviewParameter(InfoTypePreviewProvider::class) info: InfoType
) {
    // TODO: On newer compose versions check if full bottom sheet can be generated using SimpleModalBottomSheet.
    ProtonVpnPreview {
        InfoSheetContent(info = info, onOpenUrl = {})
    }
}

@OptIn(ExperimentalStdlibApi::class)
private class InfoTypePreviewProvider : PreviewParameterProvider<InfoType> {
    override val values get() = InfoType.entries.asSequence()
}
