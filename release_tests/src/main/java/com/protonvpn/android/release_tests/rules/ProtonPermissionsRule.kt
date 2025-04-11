package com.protonvpn.android.release_tests.rules

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class ProtonPermissionsRule : TestRule {
    private val rule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    override fun apply(base: Statement, description: Description): Statement {
        return rule.apply(base, description)
    }
}
