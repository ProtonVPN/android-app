package com.protonvpn.android.ui.planupgrade.usecase

import com.protonvpn.android.auth.usecase.CurrentUser
import dagger.Reusable
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject

@OptIn(ExperimentalProtonFeatureFlag::class)
@Reusable
class OneClickPaymentsEnabled @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager
) {
    suspend operator fun invoke() =
        featureFlagManager.getValue(currentUser.user()?.userId, FeatureId(ONE_CLICK_PAYMENT_FLAG))

    companion object {
        const val ONE_CLICK_PAYMENT_FLAG = "OneClickGIAP"
    }
}