package com.example.granitevoice.data.remote.client

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class GraniteClient @Inject constructor() {

    private val cookieJar: CookieJar = InMemoryCookieJar()
    private val client = createUnsafeOkHttpClient()

    @Throws(IOException::class)
    fun getMasterItems(): String {
        val request = Request.Builder()
            .url("https://192.168.0.10:8081/MasterItems/Active")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return response.body!!.string()
        }
    }

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        val sslSocketFactory = sslContext.socketFactory
        val trustManager = trustAllCerts[0] as X509TrustManager

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(AuthInterceptor(cookieJar, sslSocketFactory, trustManager))
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
}

private class AuthInterceptor(
    private val cookieJar: CookieJar,
    private val sslSocketFactory: SSLSocketFactory,
    private val trustManager: X509TrustManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()

        if (originalRequest.url.encodedPath.contains("auth")) {
            return chain.proceed(originalRequest)
        }

        val authRequestBody = "{\"UserName\":\"0\",\"Password\":\"07\"}"
            .toRequestBody("application/json".toMediaType())

        val authRequest = originalRequest.newBuilder()
            .url("https://192.168.0.10:8081/auth")
            .post(authRequestBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        val authClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()

        authClient.newCall(authRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Authentication failed: ${response.message}")
            }
        }

        return chain.proceed(originalRequest)
    }
}

class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}