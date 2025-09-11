# GraniteVoice

A modern Android voice-to-text application that integrates with Granite API and OpenAI services,
built with Jetpack Compose and following Clean Architecture principles.

## 🏗️ Project Structure

The project follows **Clean Architecture** with clear separation of concerns:

```
app/src/main/java/com/example/granitevoice/
├── data/                          # Data Layer
│   ├── local/                     # Local data sources
│   │   ├── dao/                   # Room DAOs
│   │   └── db/                    # Database configuration
│   ├── remote/                    # Remote data sources
│   │   ├── client/               # API clients (GraniteClient, OpenAIClient)
│   │   ├── model/                # Data Transfer Objects (DTOs)
│   │   └── MasterItemsGraniteSource.kt
│   └── repository/               # Repository implementations
├── domain/                       # Domain Layer (Pure Kotlin)
│   ├── model/                    # Domain entities
│   ├── repository/               # Repository interfaces
│   └── usecase/                  # Business logic
├── di/                           # Dependency Injection
├── sync/                         # Background synchronization
├── ui/                           # Presentation Layer
│   ├── main_screen/              # Main UI components
│   ├── theme/                    # Compose theming
│   └── tutorial/                 # Tutorial screens
├── AudioRecordManager.kt         # Audio recording management
└── GraniteVoiceApplication.kt    # Application class
```

## 🚀 Recent Improvements

### GraniteClient Refactoring

**Before (Issues Fixed):**

- ❌ Disabled ALL SSL certificate validation (security vulnerability)
- ❌ Hardcoded credentials and URLs
- ❌ Poor error handling with generic messages
- ❌ Authenticated on every request (inefficient)
- ❌ No timeout configurations
- ❌ No retry logic

**After (Improvements Made):**

- ✅ **Security**: Configurable SSL certificate validation with clear warnings
- ✅ **Configuration**: Externalized credentials and URLs to `GraniteClientConfig`
- ✅ **Error Handling**: Comprehensive error handling with `Result<T>` pattern
- ✅ **Authentication**: Smart auth interceptor with token caching
- ✅ **Network**: Proper timeouts and connection management
- ✅ **Resilience**: Retry logic with exponential backoff
- ✅ **Logging**: Structured logging for debugging
- ✅ **Type Safety**: Replaced string returns with `Result<T>`

### Architecture Improvements

1. **Result Pattern**: Replaced exception-based error handling with `Result<T>`
2. **Configuration Management**: Centralized configuration with `GraniteClientConfig`
3. **Dependency Updates**: Updated to latest stable versions
4. **Testing**: Added comprehensive unit tests
5. **Documentation**: Improved code documentation and README

## 🛠️ Technology Stack

### Core

- **Kotlin** - Modern, concise programming language
- **Jetpack Compose** - Declarative UI toolkit
- **Hilt** - Dependency injection
- **Coroutines** - Asynchronous programming

### Data & Networking

- **Room** - Local database with SQLite
- **OkHttp** - HTTP client for networking
- **Gson** - JSON serialization/deserialization
- **WorkManager** - Background task scheduling

### Architecture

- **Clean Architecture** - Separation of concerns
- **MVVM** - Presentation layer pattern
- **Repository Pattern** - Data access abstraction
- **Use Cases** - Business logic encapsulation

## 🔧 Configuration

### Environment Setup

The app uses configuration that can be externalized:

```kotlin
data class GraniteClientConfig(
    val baseUrl: String = "https://192.168.0.10:8081",
    val username: String = "0",
    val password: String = "07",
    val connectTimeoutSeconds: Long = 30L,
    val readTimeoutSeconds: Long = 30L,
    val writeTimeoutSeconds: Long = 30L,
    val maxRetries: Int = 3,
    val allowSelfSignedCertificates: Boolean = true, // ⚠️ DEVELOPMENT ONLY
    val enableDebugLogging: Boolean = true
)
```

### ⚠️ Self-Signed Certificate Support

**For Development Only**: The application includes support for self-signed certificates to work with
development/testing servers.

**Security Warning**:

- Self-signed certificate support is **ENABLED** by default for development
- This makes the application vulnerable to man-in-the-middle attacks
- **NEVER use this configuration in production**

**Configuration Options**:

```kotlin
// For development (current default)
allowSelfSignedCertificates = true

// For production (recommended)
allowSelfSignedCertificates = false
```

**Production Checklist**:

- [ ] Set `allowSelfSignedCertificates = false`
- [ ] Implement proper certificate pinning
- [ ] Use certificates from trusted Certificate Authorities
- [ ] Test SSL/TLS configuration with security tools

### OpenAI API Key

Add your OpenAI API key to `local.properties`:

```properties
OPENAI_API_KEY="your_openai_api_key_here"
```

## 🧪 Testing

The project includes comprehensive testing:

- **Unit Tests**: Core business logic and data transformations
- **Integration Tests**: Database and network layer testing
- **UI Tests**: Compose UI component testing

### Running Tests

```bash
# Unit tests
./gradlew test

# Android instrumented tests
./gradlew connectedAndroidTest

# All tests
./gradlew check
```

## 📱 Features

- **Voice Recording**: High-quality audio recording with proper permissions
- **Speech-to-Text**: OpenAI Whisper integration for transcription
- **Data Sync**: Background synchronization with Granite API
- **Offline Support**: Local data storage with Room database
- **Modern UI**: Material Design 3 with Jetpack Compose

## 🔒 Security Considerations

### Current Implementation

- **Development**: Self-signed certificates supported (with warnings)
- **Credentials**: Stored in configuration (needs improvement)
- **Logging**: Debug information logged (disable in production)

### ⚠️ Security Warnings

The current configuration includes several **development-only** features that **MUST** be changed
for production:

1. **Self-Signed Certificates**: Currently enabled - disable for production
2. **Debug Logging**: Currently enabled - may leak sensitive information
3. **Hardcoded Credentials**: Should be moved to secure storage

### Recommended Production Changes

**Critical (Security)**:

- [ ] Set `allowSelfSignedCertificates = false`
- [ ] Implement certificate pinning
- [ ] Move credentials to Android Keystore
- [ ] Disable debug logging (`enableDebugLogging = false`)
- [ ] Add network security config
- [ ] Use encrypted SharedPreferences for sensitive data

**Important (Reliability)**:

- [ ] Add network connectivity monitoring
- [ ] Implement proper offline-first architecture
- [ ] Add user authentication layer
- [ ] Create configuration management system

## 📋 TODO / Future Improvements

### High Priority (Security & Production)

- [ ] **CRITICAL**: Disable self-signed certificate support for production
- [ ] Implement proper SSL certificate pinning
- [ ] Move credentials to secure storage (Android Keystore)
- [ ] Add network connectivity monitoring
- [ ] Implement offline-first architecture
- [ ] Add proper error reporting/analytics

### Medium Priority
- [ ] Add user authentication and profiles
- [ ] Implement voice command recognition
- [ ] Add export functionality for transcriptions
- [ ] Create widget for quick recording
- [ ] Add configuration UI for server settings

### Low Priority
- [ ] Add multiple language support
- [ ] Implement custom wake word detection
- [ ] Add cloud backup functionality
- [ ] Create admin panel for configuration

## 🚦 Performance Considerations

- **Memory**: Proper lifecycle management for audio components
- **Network**: Connection pooling and request batching
- **Database**: Indexed queries and pagination
- **Background**: WorkManager for efficient background sync

## 🛡️ Development vs Production

### Development Configuration (Current)

```kotlin
GraniteClientConfig(
    allowSelfSignedCertificates = true,  // ⚠️  UNSAFE - Development only
    enableDebugLogging = true,           // ⚠️  Information disclosure
    // ... other settings
)
```

### Production Configuration (Recommended)

```kotlin
GraniteClientConfig(
    allowSelfSignedCertificates = false, // ✅ Secure
    enableDebugLogging = false,          // ✅ No information leakage
    // ... other settings from secure storage
)
```

## 🤝 Contributing

1. Follow the established architecture patterns
2. Write tests for new features
3. Use conventional commit messages
4. Ensure code formatting with ktlint
5. Update documentation for significant changes
6. **Never commit with `allowSelfSignedCertificates = true` for production builds**

## 📄 License

[Add your license information here]

---

**Built with ❤️ using modern Android development practices**

⚠️ **Remember**: Always review security configurations before production deployment!