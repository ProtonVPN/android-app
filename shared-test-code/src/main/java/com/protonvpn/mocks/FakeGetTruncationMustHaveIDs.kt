package com.protonvpn.mocks

import com.protonvpn.android.vpn.usecases.GetTruncationMustHaveIDs

class FakeGetTruncationMustHaveIDs : GetTruncationMustHaveIDs {

    private var truncationIds = mutableSetOf<String>()

    fun set(newTruncationIds: Set<String>) {
        truncationIds = newTruncationIds.toMutableSet()
    }

    override suspend fun invoke(maxRecents: Int, maxMustHaves: Int): Set<String> = truncationIds

}
