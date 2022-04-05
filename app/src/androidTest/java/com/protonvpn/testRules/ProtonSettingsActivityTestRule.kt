/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.testRules

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.ui.settings.SettingsActivity
import org.hamcrest.CoreMatchers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ProtonSettingsActivityTestRule : TestWatcher() {

    private val intent =
        Intent(InstrumentationRegistry.getInstrumentation().targetContext, SettingsActivity::class.java)

    override fun starting(description: Description) {
        Intents.init()
        ActivityScenario.launch<SettingsActivity>(intent)
        //It blocks outgoing intents and won't navigate outside of app.
        Intents.intending(CoreMatchers.not(IntentMatchers.isInternal()))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    override fun finished(description: Description) {
        Intents.release()
        IdlingRegistry.getInstance().unregister()
    }
}