package com.protonvpn.testSuites

import com.protonvpn.tests.auth.ui.sessionfork.SessionForkConfirmationActivityTests
import com.protonvpn.tests.login.mobile.AutoLoginTestsBlack
import com.protonvpn.tests.login.mobile.LoginCredentialessTestsCoreBlack
import com.protonvpn.tests.login.mobile.LoginSsoTestsCoreBlack
import com.protonvpn.tests.login.mobile.LoginTestsBlack
import com.protonvpn.tests.login.mobile.TwoPassTestsBlack
import com.protonvpn.tests.reports.mobile.BugReportTestsBlack
import com.protonvpn.tests.signup.SignupTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AutoLoginTestsBlack::class,
    BugReportTestsBlack::class,
    LoginCredentialessTestsCoreBlack::class,
    LoginSsoTestsCoreBlack::class,
    LoginTestsBlack::class,
    SessionForkConfirmationActivityTests::class,
    SignupTests::class,
    TwoPassTestsBlack::class,
    //TokenExpirationTests::class, TODO Implement this when there is a way to trigger API call from UI.
)
class MobileBlackSuite
