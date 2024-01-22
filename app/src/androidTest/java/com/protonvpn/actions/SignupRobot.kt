package com.protonvpn.actions

import android.view.KeyEvent
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.protonvpn.base.BaseRobot
import me.proton.core.auth.presentation.R as AuthR

class SignupRobot : BaseRobot() {

    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun enterRecoveryEmail(email: String) : SignupRobot {
        replaceText<SignupRobot>(AuthR.id.email, email)
        view.withId(AuthR.id.next).hasSibling(view.withId(AuthR.id.terms)).click()
        return this
    }

    fun verifyViaSms() : OnboardingRobot {
        uiDevice.wait(Until.findObject(By.clazz(WebView::class.java)), 30_000L)
        repeat(2) { uiDevice.pressKeyCode(KeyEvent.KEYCODE_TAB) }
        uiDevice.pressEnter()
        uiDevice.wait(Until.findObject(By.text("Get Verification code")), 10_000L)
        uiDevice.pressEnter()
        uiDevice.wait(Until.findObject(By.text("Verify")), 10_000L)
        repeat(6) {uiDevice.pressKeyCode(KeyEvent.KEYCODE_6)}
        uiDevice.pressEnter()
        return OnboardingRobot()
    }
}
