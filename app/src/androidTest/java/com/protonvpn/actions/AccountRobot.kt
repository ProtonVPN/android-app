package com.protonvpn.actions

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.test.shared.TestUser

/**
 * [AccountRobot] Contains all actions related to account component
 */
class AccountRobot : BaseRobot() {

    fun clickManageAccount(): AccountRobot = clickElementById(R.id.buttonManageAccount)

    class Verify : BaseVerify(){
        fun checkIfAccountButtonOpensBrowser(browserPackageName: String) : Verify =
            checkIfBrowserIsOpened(browserPackageName)

        fun checkIfCorrectUsernameIsDisplayed(testUser: TestUser) : Verify =
            checkIfElementByIdContainsText(R.id.textUser, testUser.email)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}