package com.protonvpn.mocks

import me.proton.core.accountrecovery.domain.IsAccountRecoveryEnabled
import me.proton.core.domain.entity.UserId

class FakeIsAccountRecoveryEnabled(
    var enabled: Boolean
) : IsAccountRecoveryEnabled {
    override fun invoke(userId: UserId?): Boolean = enabled
}
