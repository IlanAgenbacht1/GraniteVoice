package com.example.granitevoice.data.remote.client

import android.content.Context
import android.util.Log
import com.example.granitevoice.data.local.preferences.GranitePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class GraniteClientConfig(
    val baseUrl: String = "https://192.168.0.10:8081",
    val username: String = "0",
    val password: String = "07",
    val connectTimeoutSeconds: Long = 30L,
    val readTimeoutSeconds: Long = 30L,
    val writeTimeoutSeconds: Long = 30L,
    val maxRetries: Int = 3,
    val allowSelfSignedCertificates: Boolean = true, // TODO: Set to false for production
    val enableDebugLogging: Boolean = true
) {
    companion object {
        fun fromEnvironment(): GraniteClientConfig {
            // In a real app, these would come from BuildConfig, SharedPreferences, 
            // or a remote configuration service
            return GraniteClientConfig()
        }
    }
}

@Singleton
class GraniteClient @Inject constructor(
    private val preferences: GranitePreferences
) {

    private val cookieJar: CookieJar = InMemoryCookieJar()
    private var client: OkHttpClient? = null
    private var isAuthenticated = false

    companion object {
        private const val TAG = "GraniteClient"
    }

    init {
        val config = getCurrentConfig()
        if (config.allowSelfSignedCertificates) {
            Log.w(
                TAG,
                "⚠️  WARNING: Self-signed certificates are ENABLED. This should NEVER be used in production!"
            )
            Log.w(
                TAG,
                "⚠️  This configuration poses a security risk and should only be used for development/testing"
            )
        }
    }

    private fun getOrCreateClient(): OkHttpClient {
        return client ?: createHttpClient().also { client = it }
    }

    private fun getCurrentConfig(): GraniteClientConfig {
        return GraniteClientConfig(
            baseUrl = preferences.apiEndpoint,
            username = preferences.username,
            password = preferences.password,
            allowSelfSignedCertificates = preferences.allowSelfSignedCertificates
        )
    }

    @Throws(IOException::class)
    suspend fun getMasterItems(): Result<String> {
        return executeWithRetry {
            withContext(Dispatchers.IO) {
                val config = getCurrentConfig()
                val request = Request.Builder()
                    .url("${config.baseUrl}/MasterItems/Active")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()

                getOrCreateClient().newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            response.body?.string() ?: throw IOException("Empty response body")
                        }

                        response.code == 401 -> {
                            isAuthenticated = false
                            throw IOException("Authentication required")
                        }

                        else -> {
                            throw IOException("Request failed: ${response.code} - ${response.message}")
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    suspend fun getStockDetailByMasterItemId(masterItemId: Int): Result<String> {
        return executeWithRetry {
            withContext(Dispatchers.IO) {
                val config = getCurrentConfig()
                val request = Request.Builder()
                    .url("${config.baseUrl}/StockDetail/MasterItem/$masterItemId")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()

                getOrCreateClient().newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            response.body?.string() ?: throw IOException("Empty response body")
                        }

                        response.code == 401 -> {
                            isAuthenticated = false
                            throw IOException("Authentication required")
                        }

                        response.code == 404 -> {
                            throw IOException("Stock detail not found for MasterItemID: $masterItemId")
                        }

                        else -> {
                            throw IOException("Stock detail request failed: ${response.code} - ${response.message}")
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    suspend fun getStockDetailByCode(code: String): Result<String> {
        return executeWithRetry {
            withContext(Dispatchers.IO) {
                val config = getCurrentConfig()
                val request = Request.Builder()
                    .url("${config.baseUrl}/StockDetail?Code=$code")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()

                getOrCreateClient().newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            response.body?.string() ?: throw IOException("Empty response body")
                        }

                        response.code == 401 -> {
                            isAuthenticated = false
                            throw IOException("Authentication required")
                        }

                        response.code == 404 -> {
                            throw IOException("Stock detail not found for Code: $code")
                        }

                        else -> {
                            throw IOException("Stock detail request failed: ${response.code} - ${response.message}")
                        }
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    suspend fun getStockDetail(site: String? = null, location: String? = null): Result<String> {
        return executeWithRetry {
            withContext(Dispatchers.IO) {
                val config = getCurrentConfig()
                val urlBuilder = StringBuilder("${config.baseUrl}/StockDetail")
                val params = mutableListOf<String>()

                site?.let { params.add("Site=$it") }

                if (params.isNotEmpty()) {
                    urlBuilder.append("?").append(params.joinToString("&"))
                }

                val url = if (location != null) {
                    "${config.baseUrl}/StockDetail/Location/$location"
                } else {
                    urlBuilder.toString()
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .build()

                getOrCreateClient().newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            response.body?.string() ?: throw IOException("Empty response body")
                        }

                        response.code == 401 -> {
                            isAuthenticated = false
                            throw IOException("Authentication required")
                        }

                        response.code == 404 -> {
                            throw IOException("Stock detail not found for specified parameters")
                        }

                        else -> {
                            throw IOException("Stock detail request failed: ${response.code} - ${response.message}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun executeWithRetry(operation: suspend () -> String): Result<String> {
        val config = getCurrentConfig()
        repeat(config.maxRetries) { attempt ->
            try {
                val result = operation()
                return Result.success(result)
            } catch (e: IOException) {
                if (config.enableDebugLogging) {
                    Log.w(TAG, "Request attempt ${attempt + 1} failed: ${e.message}")
                }
                if (attempt == config.maxRetries - 1) {
                    Log.e(TAG, "All retry attempts failed", e)
                    return Result.failure(e)
                }
                // Wait before retry (exponential backoff)
                kotlinx.coroutines.delay(1000L * (attempt + 1))
            }
        }
        return Result.failure(IOException("Max retries exceeded"))
    }

    private fun createHttpClient(): OkHttpClient {
        val config = getCurrentConfig()
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(AuthInterceptor())
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.writeTimeoutSeconds, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))

        // WARNING: This should NEVER be enabled in production!
        if (config.allowSelfSignedCertificates) {
            Log.w(TAG, "Configuring client to accept self-signed certificates - DEVELOPMENT ONLY!")
            configureSelfSignedCertificates(builder)
        }

        return builder.build()
    }

    /**
     * WARNING: This method disables SSL certificate validation.
     * This is EXTREMELY DANGEROUS and should NEVER be used in production.
     * It makes the application vulnerable to man-in-the-middle attacks.
     *
     * This is only acceptable for development/testing with self-signed certificates.
     */
    private fun configureSelfSignedCertificates(builder: OkHttpClient.Builder) {
        try {
            // Create a trust manager that accepts all certificates
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                    // Accept all client certificates - UNSAFE FOR PRODUCTION
                }

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {
                    // Accept all server certificates - UNSAFE FOR PRODUCTION
                    if (getCurrentConfig().enableDebugLogging && chain != null && chain.isNotEmpty()) {
                        Log.d(TAG, "Server certificate: ${chain[0].subjectDN}")
                        Log.d(TAG, "Certificate issuer: ${chain[0].issuerDN}")
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ ->
                if (getCurrentConfig().enableDebugLogging) {
                    Log.d(TAG, "Accepting all hostnames - UNSAFE FOR PRODUCTION")
                }
                true
            }

            Log.w(TAG, "SSL certificate validation has been DISABLED for self-signed certificates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure self-signed certificate support", e)
            throw RuntimeException("Failed to configure SSL for self-signed certificates", e)
        }
    }

    private inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // Skip auth for auth endpoint
            if (originalRequest.url.encodedPath.contains("auth")) {
                return chain.proceed(originalRequest)
            }

            // Try the request first
            val response = chain.proceed(originalRequest)

            // If unauthorized, authenticate and retry
            if (response.code == 401 || !isAuthenticated) {
                response.close()

                try {
                    authenticate()
                    isAuthenticated = true
                    // Retry the original request
                    return chain.proceed(originalRequest)
                } catch (e: IOException) {
                    Log.e(TAG, "Authentication failed", e)
                    throw e
                }
            }

            return response
        }

        @Throws(IOException::class)
        private fun authenticate() {
            val config = getCurrentConfig()
            val authRequestBody = buildString {
                append("{")
                append("\"UserName\":\"${config.username}\",")
                append("\"Password\":\"${config.password}\"")
                append("}")
            }.toRequestBody("application/json".toMediaType())

            val authRequest = Request.Builder()
                .url("${config.baseUrl}/auth")
                .post(authRequestBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()

            // Note: This runs in the OkHttp interceptor thread, which is already a background thread
            // so we don't need withContext(Dispatchers.IO) here
            getOrCreateClient().newCall(authRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Authentication failed: ${response.code} - ${response.message}")
                }
                if (config.enableDebugLogging) {
                    Log.d(TAG, "Authentication successful")
                }
            }
        }
    }

    /**
     * Refresh the client configuration - call this when preferences change
     */
    fun refreshConfiguration() {
        client = null // This will force recreation with new config
        isAuthenticated = false // Reset auth state
        val config = getCurrentConfig()

        if (config.enableDebugLogging) {
            Log.d(TAG, "Configuration refreshed: ${config.baseUrl}")
        }

        if (config.allowSelfSignedCertificates) {
            Log.w(TAG, "⚠️  Self-signed certificates are enabled after configuration refresh")
        }
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