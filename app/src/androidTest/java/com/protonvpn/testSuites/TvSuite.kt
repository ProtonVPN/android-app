package com.protonvpn.testSuites

import com.protonvpn.testsTv.login.LoginRobotTestsTv
import com.protonvpn.testsTv.login.TvLoginViewModelTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        LoginRobotTestsTv::class,
        TvLoginViewModelTests::class
)
class TvSuite
