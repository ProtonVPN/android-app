package com.protonvpn.testSuites

import com.protonvpn.tests.account.RealConnectionTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        RealConnectionTests::class,
)
class RealConnectionSuite