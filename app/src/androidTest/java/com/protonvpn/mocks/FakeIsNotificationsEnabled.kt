package com.protonvpn.mocks

import me.proton.core.domain.entity.UserId
import me.proton.core.notification.domain.usecase.IsNotificationsEnabled

class FakeIsNotificationsEnabled(
    var enabled: Boolean
) : IsNotificationsEnabled {
    override fun invoke(userId: UserId?): Boolean = enabled
}
