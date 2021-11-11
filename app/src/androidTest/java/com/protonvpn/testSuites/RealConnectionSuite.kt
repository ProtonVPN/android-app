package com.protonvpn.testSuites

import com.protonvpn.tests.account.RealConnectionRobotTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        RealConnectionRobotTests::class,
)
class RealConnectionSuite