package com.protonvpn.testSuites

import com.protonvpn.testsTv.login.LoginRobotTestsTv
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        LoginRobotTestsTv::class
)
class TvSuite