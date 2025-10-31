/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.mocks

import com.protonvpn.android.update.AppUpdateBannerState
import com.protonvpn.android.update.AppUpdateBannerStateFlow
import com.protonvpn.android.update.ShouldShowAppUpdateDotFlow
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow

// A class implementing a MutableStateFlow - for use in making fake flow implementations for tests.
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
open class FakeFlow<T>(flow: MutableStateFlow<T>): MutableStateFlow<T> by flow {
    constructor(initialValue: T): this(MutableStateFlow(initialValue))
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class FakeShouldShowAppUpdateDotFlow
    : ShouldShowAppUpdateDotFlow, FakeFlow<Boolean>(false)

class FakeAppUpdateBannerStateFlow
    : AppUpdateBannerStateFlow, FakeFlow<AppUpdateBannerState>(AppUpdateBannerState.Hidden)