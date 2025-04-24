package com.protonvpn.testSuites

import com.protonvpn.tests.login.mobile.AutoLoginTestsBlack
import com.protonvpn.tests.login.mobile.LoginTestsBlack
import com.protonvpn.tests.login.mobile.LoginCredentialessTestsCoreBlack
import com.protonvpn.tests.login.mobile.LoginSsoTestsCoreBlack
import com.protonvpn.tests.signup.SignupTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    LoginTestsBlack::class,
    LoginSsoTestsCoreBlack::class,
    SignupTests::class,
    LoginCredentialessTestsCoreBlack::class,
    AutoLoginTestsBlack::class
    //BugReportTests::class, TODO Implement this when bug report is implemented on redesign.
    //TokenExpirationTests::class, TODO Implement this when there is a way to trigger API call from UI.
)
class MobileBlackSuite {
}
