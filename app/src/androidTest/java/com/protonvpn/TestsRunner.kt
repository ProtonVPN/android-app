/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.runner.AndroidJUnitRunner
import com.github.tmurakami.dexopener.DexOpener
import com.protonvpn.testsHelper.TestApplication_Application

class TestsRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        // Mocking final classes causes crashes on 32bit Android emulator (including Firebase Testlab virtual devices),
        // see VPNAND-797.
        // Opening classes with DexOpener mitigates the issue.
        // Use it only for newer APIs because its use causes crashes on Android 6 in
        // ProtonApplication.initDependencies().
        if (Build.VERSION.SDK_INT >= 28) {
            DexOpener.install(this)
        }
        return super.newApplication(cl, TestApplication_Application::class.java.name, context)
    }
}
