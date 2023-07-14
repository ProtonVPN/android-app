package com.protonvpn.testSuites

import com.protonvpn.tests.login.LoginTests
import com.protonvpn.tests.login.LogoutTests
import com.protonvpn.tests.login.TwoFaTests
import com.protonvpn.tests.signin.SignInTests
import com.protonvpn.tests.signup.SignupTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    LoginTests::class,
    TwoFaTests::class,
    SignInTests::class,
    SignupTests::class,
    LogoutTests::class,
    //BugReportTests::class, TODO Implement this when bug report is implemented on redesign.
    //TokenExpirationTests::class, TODO Implement this when there is a way to trigger API call from UI.
)
class MobileBlackSuite {
}