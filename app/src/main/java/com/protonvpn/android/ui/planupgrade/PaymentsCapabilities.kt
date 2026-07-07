/*
 * Copyright (c) 2026. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.ui.planupgrade

import dagger.Reusable
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.proton.android.payment.billing.extension.invokeOrBillingUnavailable
import me.proton.android.payment.billing.model.StoreProduct
import me.proton.android.payment.billing.usecase.AcknowledgePurchase
import me.proton.android.payment.billing.usecase.GetStoreProducts
import me.proton.android.payment.billing.usecase.PurchaseStoreProduct
import me.proton.android.payment.capability.HttpCapability
import me.proton.android.payment.capability.StoreCapability
import me.proton.android.payment.capability.model.HttpResponse
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.network.data.protonApi.GenericResponse
import me.proton.core.network.domain.ApiException
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.serializeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.Optional
import javax.inject.Inject

private const val APPLICATION_JSON_CONTENT_TYPE: String = "application/json"

@Reusable
class PaymentsHttpCapability @Inject constructor(
    private val apiProvider: ApiProvider,
    private val accountManager: AccountManager,
) : HttpCapability {

    override suspend fun get(endpoint: String): HttpResponse {
        val response = apiProvider.get<GenericGetPostRetrofitApi>(getPrimarySessionId()).invoke {
            get(endpoint)
        }

        return response.toHttpResponse()
    }

    override suspend fun post(endpoint: String, body: ByteArray?): HttpResponse {
        val requestBody = body?.toRequestBody(APPLICATION_JSON_CONTENT_TYPE.toMediaType())
        val response = apiProvider.get<GenericGetPostRetrofitApi>(getPrimarySessionId()).invoke {
            post(endpoint, requestBody)
        }

        return response.toHttpResponse()
    }

    private suspend fun getPrimarySessionId(): SessionId? {
        return accountManager.getPrimaryAccount().first()?.sessionId
    }

    private fun ApiResult<Response<JsonObject>>.toHttpResponse(): HttpResponse {
        return when (this) {
            is ApiResult.Success -> {
                HttpResponse(status = value.code(), body = value.body())
            }
            is ApiResult.Error.Http -> {
                val reconstructedBody = proton?.let {
                    GenericResponse(it.code).serializeToJsonElement().jsonObject
                }
                HttpResponse(status = httpCode, body = reconstructedBody)
            }
            is ApiResult.Error -> {
                throw ApiException(this)
            }
        }
    }
}

@Reusable
class PaymentsStoreCapability @Inject constructor(
    private val acknowledgePurchase: Optional<AcknowledgePurchase>,
    private val getStoreProducts: Optional<GetStoreProducts>,
    private val purchaseStoreProduct: Optional<PurchaseStoreProduct>,
) : StoreCapability {

    override suspend fun getProducts(ids: List<String>): Result<List<StoreProduct>> =
        getStoreProducts.invokeOrBillingUnavailable { it(ids) }

    override suspend fun purchase(
        productId: String,
        offerToken: String,
        userId: String?
    ): Result<Unit> =
        purchaseStoreProduct.invokeOrBillingUnavailable { it(productId, offerToken, userId) }


    override suspend fun acknowledge(orderId: String): Result<Unit> =
        acknowledgePurchase.invokeOrBillingUnavailable { it(orderId) }
}

interface GenericGetPostRetrofitApi : BaseRetrofitApi {

    /**
     * Makes a generic GET request.
     *
     * @param endpoint the URI to execute a request on.
     * @return a generic [Response] object of type [JsonObject].
     */
    @GET
    suspend fun get(@Url endpoint: String): Response<JsonObject>

    /**
     * Makes a generic POST request.
     *
     * @param endpoint the URI to execute a request on.
     * @param body the request body to post.
     * @return a generic [Response] object of type [JsonObject].
     */
    @POST
    suspend fun post(@Url endpoint: String, @Body body: RequestBody?): Response<JsonObject>
}
