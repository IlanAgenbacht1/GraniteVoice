package com.example.granitevoice.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GranitePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GranitePreferences"
        private const val PREFS_NAME = "granite_config"

        private const val KEY_API_ENDPOINT = "api_endpoint"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_ALLOW_SELF_SIGNED = "allow_self_signed_certs"

        private const val DEFAULT_ENDPOINT = "https://192.168.0.10:8081"
        private const val DEFAULT_USERNAME = "0"
        private const val DEFAULT_PASSWORD = "07"
    }

    // For now, use regular SharedPreferences with proper error handling
    // TODO: Implement EncryptedSharedPreferences when the device/API supports it reliably
    private val sharedPreferences: SharedPreferences by lazy {
        createSecureSharedPreferences()
    }

    /**
     * Creates secure SharedPreferences with proper error handling
     * Currently uses regular SharedPreferences - can be upgraded to encrypted when needed
     */
    private fun createSecureSharedPreferences(): SharedPreferences {
        return try {
            Log.d(TAG, "Creating SharedPreferences for configuration storage")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                Log.d(TAG, "Successfully created SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SharedPreferences", e)
            throw RuntimeException("Cannot create preferences storage", e)
        }
    }

    var apiEndpoint: String
        get() = try {
            sharedPreferences.getString(KEY_API_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read API endpoint, using default", e)
            DEFAULT_ENDPOINT
        }
        set(value) {
            try {
                sharedPreferences.edit().putString(KEY_API_ENDPOINT, value.trim()).apply()
                Log.d(TAG, "API endpoint saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save API endpoint", e)
            }
        }

    var username: String
        get() = try {
            sharedPreferences.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read username, using default", e)
            DEFAULT_USERNAME
        }
        set(value) {
            try {
                sharedPreferences.edit().putString(KEY_USERNAME, value.trim()).apply()
                Log.d(TAG, "Username saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save username", e)
            }
        }

    var password: String
        get() = try {
            sharedPreferences.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read password, using default", e)
            DEFAULT_PASSWORD
        }
        set(value) {
            try {
                sharedPreferences.edit().putString(KEY_PASSWORD, value).apply()
                Log.d(TAG, "Password saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save password", e)
            }
        }

    var isConfigured: Boolean
        get() = try {
            sharedPreferences.getBoolean(KEY_IS_CONFIGURED, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read configuration status", e)
            false
        }
        set(value) {
            try {
                sharedPreferences.edit().putBoolean(KEY_IS_CONFIGURED, value).apply()
                Log.d(TAG, "Configuration status saved: $value")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save configuration status", e)
            }
        }

    var allowSelfSignedCertificates: Boolean
        get() = try {
            sharedPreferences.getBoolean(KEY_ALLOW_SELF_SIGNED, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read self-signed certificate setting, using default (true)", e)
            true // Default to true for development
        }
        set(value) {
            try {
                sharedPreferences.edit().putBoolean(KEY_ALLOW_SELF_SIGNED, value).apply()
                Log.d(TAG, "Self-signed certificate setting saved: $value")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save self-signed certificate setting", e)
            }
        }

    fun saveConfiguration(
        endpoint: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean = true
    ) {
        try {
            sharedPreferences.edit().apply {
                putString(KEY_API_ENDPOINT, endpoint.trim())
                putString(KEY_USERNAME, username.trim())
                putString(KEY_PASSWORD, password)
                putBoolean(KEY_ALLOW_SELF_SIGNED, allowSelfSigned)
                putBoolean(KEY_IS_CONFIGURED, true)
                apply()
            }
            Log.i(
                TAG,
                "Configuration saved successfully: endpoint=${endpoint.trim()}, username=${username.trim()}, allowSelfSigned=$allowSelfSigned"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save configuration", e)
            throw RuntimeException("Failed to save configuration", e)
        }
    }

    fun clearConfiguration() {
        try {
            sharedPreferences.edit().clear().apply()
            Log.i(TAG, "Configuration cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear configuration", e)
            throw RuntimeException("Failed to clear configuration", e)
        }
    }

    /**
     * Get configuration for GraniteClient
     */
    fun getClientConfig(): Triple<String, String, String> {
        return Triple(apiEndpoint, username, password)
    }

    /**
     * Validate that the current configuration is complete and valid
     */
    fun isValidConfiguration(): Boolean {
        return try {
            val endpoint = apiEndpoint
            val user = username
            val pass = password

            endpoint.isNotBlank() &&
                    endpoint.startsWith("http") &&
                    user.isNotBlank() &&
                    pass.isNotBlank() &&
                    isConfigured
        } catch (e: Exception) {
            Log.e(TAG, "Error validating configuration", e)
            false
        }
    }

    /**
     * Get a summary of the current configuration for logging/debugging
     */
    fun getConfigurationSummary(): String {
        return try {
            "GranitePreferences[endpoint=$apiEndpoint, username=$username, configured=$isConfigured, allowSelfSigned=$allowSelfSignedCertificates]"
        } catch (e: Exception) {
            "GranitePreferences[error reading configuration: ${e.message}]"
        }
    }
}