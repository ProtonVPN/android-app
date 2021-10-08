package com.protonvpn.kotlinActions

import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot

class HomeRobot : BaseRobot() {

    fun openAccountView() : AccountRoobot{
        clickElementByContentDescription<HomeRobot>(R.string.hamburgerMenu)
        clickElementById<HomeRobot>(R.id.layoutUserInfo)
        return AccountRoobot()
    }
}