package com.protonvpn.testSuites

import com.protonvpn.tests.login.LoginTests
import com.protonvpn.tests.signin.SignInGuestTests
import com.protonvpn.tests.signin.SignInTests
import com.protonvpn.tests.signup.SignupTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    LoginTests::class,
    SignInTests::class,
    SignupTests::class,
    SignInGuestTests::class,
    //BugReportTests::class, TODO Implement this when bug report is implemented on redesign.
    //TokenExpirationTests::class, TODO Implement this when there is a way to trigger API call from UI.
)
class MobileBlackSuite {
}
