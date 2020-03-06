package com.protonvpn.android.api

import android.util.Base64
import com.protonvpn.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.commons.codec.binary.Base32
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.Record
import org.minidns.record.TXT
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class DnsOverHttpsProviderRFC8484(private val baseUrl: String) : AlternativeApiManager.DnsOverHttpsProvider {

    private val api: DnsOverHttpsRetrofitApi

    init {
        require(baseUrl.endsWith('/'))

        val converterFactory = object : Converter.Factory() {
            override fun responseBodyConverter(
                type: Type,
                annotations: Array<Annotation>,
                retrofit: Retrofit
            ): Converter<ResponseBody, *>? = Converter<ResponseBody, DnsMessage> { body ->
                body.use {
                    DnsMessage(it.bytes())
                }
            }
        }

        val httpClientBuilder = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            httpClientBuilder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }

        val okClient = httpClientBuilder.build()
        api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okClient)
                .addConverterFactory(converterFactory)
                .build()
                .create(DnsOverHttpsRetrofitApi::class.java)
    }

    override suspend fun getAlternativeBaseUrls(domain: String): List<String>? {
        val base32domain = Base32().encodeAsString(domain.toByteArray()).trim('=')
        val question = Question("d$base32domain.protonpro.xyz", Record.TYPE.TXT)
        val queryMessage = DnsMessage.builder()
                .setRecursionDesired(true)
                .setQuestion(question)
                .build()
        val queryMessageBase64 = Base64.encodeToString(
                queryMessage.toArray(),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val response = ApiResult.tryWrap {
            api.getServers(baseUrl.removeSuffix("/"), queryMessageBase64)
        }
        if (response is ApiResult.Success) {
            val answers = response.value.answerSection
            return answers
                    .mapNotNull { (it.payload as? TXT)?.text }
                    .map { "https://$it/" }
                    .takeIf { it.isNotEmpty() }
        }
        return null
    }

    companion object {
        private const val TIMEOUT_S = 10L
    }
}
