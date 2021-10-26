package com.protonvpn.testsHelper

import android.widget.Toast
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation

import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * Toast the name of each test to the screen to make the test easier to identify in a
 * Firebase video stream.
 */
internal class ToastingRunListener : RunListener() {
    override fun testStarted(description: Description) {
        val testName = description.displayName
        getInstrumentation().runOnMainSync {
            Toast.makeText(getInstrumentation().targetContext, testName, Toast.LENGTH_LONG).show() }
    }
}