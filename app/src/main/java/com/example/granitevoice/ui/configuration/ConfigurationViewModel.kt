package com.example.granitevoice.ui.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.granitevoice.data.local.preferences.GranitePreferences
import com.example.granitevoice.data.remote.client.GraniteClient
import com.example.granitevoice.domain.repository.MasterItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConfigurationUiState(
    val apiEndpoint: String = "https://192.168.0.10:8081",
    val username: String = "0",
    val password: String = "07",
    val allowSelfSignedCerts: Boolean = true,
    val isLoading: Boolean = false,
    val isFormValid: Boolean = false,
    val message: String? = null,
    val isConfigurationSuccessful: Boolean = false,
    val loadingMessage: String = "" // New field for detailed loading messages
)

@HiltViewModel
class ConfigurationViewModel @Inject constructor(
    private val preferences: GranitePreferences,
    private val graniteClient: GraniteClient,
    private val masterItemRepository: MasterItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigurationUiState())
    val uiState: StateFlow<ConfigurationUiState> = _uiState.asStateFlow()

    init {
        loadExistingConfiguration()
    }

    private fun loadExistingConfiguration() {
        android.util.Log.d("ConfigurationViewModel", "Loading existing configuration")
        val loadedState = ConfigurationUiState(
            apiEndpoint = preferences.apiEndpoint,
            username = preferences.username,
            password = preferences.password,
            allowSelfSignedCerts = preferences.allowSelfSignedCertificates,
            isConfigurationSuccessful = false // Ensure this starts as false when editing
        )

        _uiState.update { validateForm(loadedState) }
        android.util.Log.d(
            "ConfigurationViewModel",
            "Configuration loaded: isConfigurationSuccessful = ${_uiState.value.isConfigurationSuccessful}"
        )
    }

    fun updateApiEndpoint(endpoint: String) {
        _uiState.update { currentState ->
            validateForm(currentState.copy(apiEndpoint = endpoint))
        }
    }

    fun updateUsername(username: String) {
        _uiState.update { currentState ->
            validateForm(currentState.copy(username = username))
        }
    }

    fun updatePassword(password: String) {
        _uiState.update { currentState ->
            validateForm(currentState.copy(password = password))
        }
    }

    fun updateAllowSelfSignedCerts(allow: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(allowSelfSignedCerts = allow) // No need to validate for this field
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun validateForm(state: ConfigurationUiState): ConfigurationUiState {
        val isValid = state.apiEndpoint.isNotBlank() &&
                state.username.isNotBlank() &&
                state.password.isNotBlank() &&
                state.apiEndpoint.startsWith("http")

        return state.copy(isFormValid = isValid)
    }

    fun testAndSaveConfiguration() {
        val currentState = _uiState.value
        if (!currentState.isFormValid || currentState.isLoading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    message = null,
                    loadingMessage = "Testing connection..."
                )
            }

            try {
                // Test the configuration by attempting to connect
                val testResult = testConnection(
                    currentState.apiEndpoint,
                    currentState.username,
                    currentState.password,
                    currentState.allowSelfSignedCerts
                )

                if (testResult) {
                    // Save configuration if test successful
                    _uiState.update { it.copy(loadingMessage = "Saving configuration...") }

                    preferences.saveConfiguration(
                        endpoint = currentState.apiEndpoint,
                        username = currentState.username,
                        password = currentState.password,
                        allowSelfSigned = currentState.allowSelfSignedCerts
                    )

                    // Refresh client to use the saved configuration
                    graniteClient.refreshConfiguration()

                    // Now sync master items
                    _uiState.update { it.copy(loadingMessage = "Syncing master items...") }

                    try {
                        masterItemRepository.syncMasterItems()

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "✅ Configuration saved and master items synced! Redirecting...",
                                loadingMessage = ""
                            )
                        }

                        // Add a delay to let user see the success message
                        delay(2000)

                        _uiState.update {
                            it.copy(isConfigurationSuccessful = true)
                        }

                    } catch (syncException: Exception) {
                        // Configuration saved but sync failed - still proceed
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "✅ Configuration saved! ⚠️ Master item sync failed: ${syncException.message}",
                                loadingMessage = ""
                            )
                        }

                        delay(3000)

                        _uiState.update {
                            it.copy(isConfigurationSuccessful = true)
                        }
                    }

                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "❌ Connection test failed. Please check your settings and try again.",
                            loadingMessage = ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Configuration error: ${e.message}",
                        loadingMessage = ""
                    )
                }
            }
        }
    }

    private suspend fun testConnection(
        endpoint: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Validate URL format first
                val isValidUrl = endpoint.matches(Regex("^https?://.*:\\d+/?.*"))
                if (!isValidUrl) {
                    return@withContext false
                }

                // Save current preferences temporarily
                val originalEndpoint = preferences.apiEndpoint
                val originalUsername = preferences.username
                val originalPassword = preferences.password
                val originalAllowSelfSigned = preferences.allowSelfSignedCertificates

                try {
                    // Set test configuration
                    preferences.apiEndpoint = endpoint
                    preferences.username = username
                    preferences.password = password
                    preferences.allowSelfSignedCertificates = allowSelfSigned

                    // Refresh the client to use new configuration
                    graniteClient.refreshConfiguration()

                    // Try to get master items as a connection test with shorter timeout
                    val result = graniteClient.getMasterItems()

                    // If we get here, the connection worked
                    result.isSuccess

                } finally {
                    // Restore original preferences (don't save test values yet)
                    preferences.apiEndpoint = originalEndpoint
                    preferences.username = originalUsername
                    preferences.password = originalPassword
                    preferences.allowSelfSignedCertificates = originalAllowSelfSigned

                    // Refresh client back to original configuration
                    graniteClient.refreshConfiguration()
                }

            } catch (e: Exception) {
                false
            }
        }
    }
}