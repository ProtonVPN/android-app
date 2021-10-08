package com.protonvpn.kotlinActions

import com.protonvpn.android.R
import com.protonvpn.base.BaseVerify
import com.protonvpn.test.shared.TestUser

class AccountRoobot {

    class Verify : BaseVerify(){
        fun checkIfAccountButtonOpenUrl() : Verify = checkIfButtonOpensUrl(R.id.buttonManageAccount)
        fun checkIfCorrectUsernameIsDisplayed(testUser: TestUser) : Verify =
            checkIfElementByIdContainsText(R.id.textUser, testUser.email)
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}