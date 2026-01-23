# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Authbound's** implementation of the EU Digital Identity (EUDI) Android Wallet - a secure mobile application for managing digital identity credentials. Authbound is a startup specializing in SaaS solutions around the EUDI wallet ecosystem, providing enhanced user experience and enterprise-grade features while maintaining full EUDI-ARF compliance.

### EUDI Wallet Core Features
- **Credential Management**: PID (Personal Identification Data), mDL (Mobile Driving License)
- **Authentication**: Secure identity verification across digital services
- **Document Signing**: Legally binding digital signatures
- **Proximity Sharing**: NFC/QR code credential sharing
- **Remote Presentations**: Online credential verification

The app follows EU Digital Identity Wallet Architecture Reference Framework (EUDI-ARF) standards and implements:
- OpenID4VP (draft 24) for remote presentation
- ISO18013-5 for proximity presentation  
- OpenID4VCI (draft 15) for credential issuance

## Build Commands

### Basic Development
```bash
# Build the app (default variant)
./gradlew assembleDebug

# Build specific variants
./gradlew assembleDevDebug    # Dev environment with debugging
./gradlew assembleDevRelease  # Dev environment optimized
./gradlew assembleDemoDebug   # Demo environment with debugging
./gradlew assembleDemoRelease # Demo environment optimized

# Install on device/emulator
./gradlew installDevDebug

# Clean build
./gradlew clean
```

### Testing
```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :business-logic:test
./gradlew :authentication-logic:test

# Run instrumentation tests
./gradlew connectedAndroidTest

# Generate test coverage report
./gradlew koverHtmlReport
```

### Code Quality
```bash
# Run lint checks
./gradlew lint

# Generate lint report
./gradlew lintDebug

# Run security vulnerability checks
./gradlew dependencyCheckAnalyze

# Generate baseline profiles
./gradlew generateBaselineProfile
```

## Build Environment Requirements

**CRITICAL**: Always use Android Studio's bundled JDK and Gradle wrapper. Do NOT use system Java installations.

| Tool | Version | Notes |
|------|---------|-------|
| **Java (JDK)** | Android Studio JBR | Always use Android Studio's bundled JDK - no exceptions |
| **Gradle** | 8.13 | Managed by wrapper (`./gradlew`) |
| **Android Gradle Plugin** | 8.13.0 | Defined in `gradle/libs.versions.toml` |
| **Kotlin** | 2.2.21 | Defined in `gradle/libs.versions.toml` |
| **Android SDK** | 34+ | Compile SDK 34 or higher |

### Setting Up Java for Builds

**Always use Android Studio's bundled JDK.** This is the only supported configuration.

```bash
# Find your Android Studio installation and set JAVA_HOME
# Check these common locations:
ls -d ~/Applications/Android\ Studio.app 2>/dev/null || \
ls -d /Applications/Android\ Studio.app 2>/dev/null

# Set JAVA_HOME to Android Studio's JBR (JetBrains Runtime)
# Use the FULL PATH - do not use $HOME or ~ in JAVA_HOME as it may not expand correctly
# Example for ~/Applications:
export JAVA_HOME="/Users/$(whoami)/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Or for /Applications:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Verify it works
$JAVA_HOME/bin/java -version
```

### Build Commands

```bash
# If JAVA_HOME is set correctly in your environment:
./gradlew assembleDevDebug

# Or specify JAVA_HOME inline (use full absolute path):
JAVA_HOME="/Users/lassi/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDevDebug
```

### Verifying Build Environment

```bash
# Verify using Android Studio JDK
$JAVA_HOME/bin/java -version
# Should show JetBrains Runtime (e.g., "OpenJDK Runtime Environment (build 21.0.x...)")

# Verify Gradle can run
./gradlew --version

# Test compilation of core modules
./gradlew :authentication-logic:compileDevDebugKotlin :startup-feature:compileDevDebugKotlin
```

## Architecture Overview

### Clean Architecture Structure

The project follows clean architecture with clear separation of concerns:

**Presentation Layer (Feature Modules):**
- `authentication-feature` - Login, biometric setup, wallet activation
- `dashboard-feature` - Home screen, main navigation
- `issuance-feature` - Credential issuance flows
- `proximity-feature` - NFC and QR code sharing
- `presentation-feature` - Credential presentation to verifiers
- `startup-feature` - App initialization and onboarding
- `common-feature` - Shared UI components

**Domain Layer (Logic Modules):**
- `business-logic` - Core wallet business rules and use cases
- `core-logic` - Shared utilities and base classes
- `authentication-logic` - Authentication and authorization
- `wallet-activation-logic` - Wallet setup processes

**Data Layer:**
- `network-logic` - API communication
- `storage-logic` - Local data persistence
- `assembly-logic` - Credential assembly
- `analytics-logic` - Usage tracking
- `notification-logic` - Push notifications

**Infrastructure:**
- `ui-logic` - Shared UI components and design system
- `resources-logic` - Shared resources and assets
- `test-logic` - Testing utilities

### Dependency Flow
- Features depend on Logic modules
- Logic modules depend on Core logic
- No circular dependencies between modules
- Core logic has minimal external dependencies

### MVI Pattern
ViewModels use Model-View-Intent (MVI) pattern with:
- `State` - Immutable UI state
- `Event` - User interactions
- `Effect` - Side effects (navigation, dialogs)

## Key Technologies

- **Language:** Kotlin 2.2.21 with coroutines and Flow
- **Build:** Gradle 8.13, Android Gradle Plugin 8.13.0, **JDK 17 required**
- **UI:** Jetpack Compose with Material 3 (migrating from XML views)
- **DI:** Koin with annotations (@Single, @Factory, @Module)
- **Architecture:** Clean Architecture + MVI pattern
- **Security:** Android Keystore, biometric authentication, encrypted storage
- **Database:** Room with SQLCipher encryption
- **Network:** Retrofit + OkHttp with certificate pinning
- **Testing:** JUnit 5, MockK, Truth assertions, Compose Test
- **Backend:** Supabase for authentication and data management

## Security Implementation

### Authentication
- Hardware-backed biometric authentication (BIOMETRIC_STRONG)
- PIN-based authentication with secure storage
- Session management with automatic timeout

### Data Protection
- Android Keystore for cryptographic operations
- Encrypted SharedPreferences for sensitive data
- SQLCipher for database encryption
- Certificate pinning for network security

### Compliance
- GDPR compliance with user consent management
- Security audit logging for compliance
- Data minimization principles
- Hardware security module integration

## Build Variants

**Flavors:**
- `dev` - Development environment (dev.issuer.eudiw.dev)
- `demo` - Demo environment (demo.issuer.eudiw.dev)

**Build Types:**
- `debug` - Full logging enabled, debugging tools
- `release` - Optimized, no logging, obfuscated

## Configuration

### Wallet Core Configuration
Located in `core-logic/src/{flavor}/java/eu/europa/ec/corelogic/config/WalletCoreConfigImpl.kt`

Key configuration points:
- VCI_ISSUER_URL - Issuer service endpoint
- VCI_CLIENT_ID - OAuth client identifier
- Authentication requirements
- Certificate validation settings

### Local Development
For local services, update VCI_ISSUER_URL to use `10.0.2.2` (Android emulator localhost alias).

## Development Patterns

### Business Logic Architecture
- **Interactors**: Feature-level business logic in feature modules (e.g., `DashboardInteractor`, `AuthenticationInteractor`)
- **Use Cases**: Single-purpose domain operations in logic modules (e.g., `SignInWithEmailPasswordUseCase`, `IsUserAuthenticatedUseCase`)
- **Controllers**: System-level operations and hardware interaction (e.g., `BiometricAuthenticationController`, `CryptoController`)
- **Repositories**: Data access layer with Repository pattern (e.g., `SupabaseAuthRepository`, `WalletRepository`)

### Dependency Injection with Koin
```kotlin
@Module
@ComponentScan("eu.europa.ec.networklogic") 
class LogicNetworkModule

@Single
fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

@Factory
fun provideDashboardInteractor(
    resourceProvider: ResourceProvider,
    walletController: WalletCoreDocumentsController
): DashboardInteractor = DashboardInteractorImpl(resourceProvider, walletController)
```

### Code Style (Kotlin Guidelines)
- **PascalCase** for classes, **camelCase** for variables/functions
- **Declare explicit types** - avoid `any`, create necessary types
- **Short functions** with single purpose (<20 lines)
- **Use English** for all code and documentation
- **Start functions with verbs** (e.g., `authenticateUser`, `isUserLoggedIn`)
- **Complete words** instead of abbreviations (except standard ones like API, URL)
- **Follow SOLID principles** and prefer composition over inheritance

### Adding New Features
1. Create feature module in presentation layer
2. Implement corresponding logic module if needed  
3. Follow MVI pattern for ViewModels
4. Use Koin dependency injection with appropriate scopes
5. Implement proper error handling and security
6. Follow existing naming conventions (`*Interactor`, `*UseCase`, `*Controller`)

### Security Requirements
- **Zero Trust Architecture** - Never trust, always verify
- **Hardware Security**: Use Android Keystore for all cryptographic operations
- **Biometric Authentication**: BIOMETRIC_STRONG for critical operations
- **Data Encryption**: All sensitive data encrypted at rest and in transit
- **Audit Logging**: Comprehensive security event logging for compliance
- **GDPR Compliance**: Data minimization, user consent, data subject rights
- **Certificate Pinning**: Network security for API communications

## Testing Strategy

### Unit Testing (JUnit 5 + MockK + Truth)
```kotlin
class DashboardInteractorTest {
    @MockK
    private lateinit var resourceProvider: ResourceProvider
    
    @Test
    fun `when getQuickActions succeeds, should return success state`() = runTest {
        // Given (Arrange)
        every { resourceProvider.getString(any()) } returns "Test Action"
        
        // When (Act)
        val flow = interactor.getQuickActions()
        
        // Then (Assert)
        flow.test {
            val state = awaitItem()
            assertThat(state).isInstanceOf(DashboardQuickActionsPartialState.Success::class.java)
        }
    }
}
```

### Integration Tests
- Test module interactions with fake implementations
- Test security flows end-to-end
- Use test doubles for external dependencies

### UI Testing (Compose Test + Espresso)
- Test Compose UI with ComposeTestRule
- Test accessibility with semantics
- Test user interactions and navigation

### Security Tests
- Validate biometric authentication flows
- Test encryption/decryption operations
- Verify certificate pinning behavior
- Test audit logging functionality

## Common Issues

### Build Issues
- **Java version mismatch**: If build fails with cryptic errors (e.g., just a number like "25"), check your Java version. See [Build Environment Requirements](#build-environment-requirements) above.
- Ensure Android SDK 34+ is installed
- Check that `local.properties` contains correct SDK path
- Clean build if experiencing caching issues (`./gradlew clean`)
- If using Android Studio's bundled JDK, set `JAVA_HOME` to Android Studio's JBR path

### Security Issues
- Biometric authentication requires BIOMETRIC_STRONG capability
- Certificate pinning failures indicate network configuration issues
- Keystore errors may require device security validation

### Performance
- Use baseline profiles for optimization
- Monitor memory usage in credential operations
- Implement proper lifecycle management for ViewModels

## UI/UX Guidelines

### Material 3 + Compose
- Use **Material 3** design system for all UI components
- **Jetpack Compose** with proper accessibility support
- **Responsive grid layouts** - 2 columns on phones, 3+ on tablets
- **Smooth animations** with staggered delays for lists
- **Haptic feedback** for user interactions

### Accessibility Standards
- **WCAG 2.1 AA** compliance required
- **48dp minimum touch targets**
- **4.5:1 color contrast ratio**
- **Screen reader support** with proper semantics
- **High contrast mode** support

### Animation Patterns
```kotlin
// Staggered list animations
AnimatedVisibility(
    visible = true,
    enter = fadeIn(tween(300, delayMillis = index * 50)) + 
            slideInVertically(tween(300, delayMillis = index * 50))
)
```

## Important Context

### Backend Integration
⚠️ **Important**: The backend is in a separate repository. Always ask the developer for:
- API endpoint details and contracts
- Authentication flow requirements  
- Data models and request/response formats
- Error handling expectations

### EUDI Compliance
- Reference [EUDI-ARF Documentation](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework)
- Follow PID and mDL Rulebook requirements
- Ensure cross-border credential recognition
- Maintain security and privacy standards

### Current Focus Areas
1. **Supabase Authentication** - User auth implementation
2. **Wallet Unit Attestation** - Core credential management  
3. **Proximity Features** - NFC/QR credential sharing
4. **Security Hardening** - Enhanced biometric/crypto implementations

## Additional Resources

- [Build Guide](wiki/how_to_build.md) - Detailed build instructions
- [Configuration Guide](wiki/configuration.md) - Configuration options  
- [Security Standards](.cursor/rules/security-compliance.mdc) - Security requirements
- [Architecture Guidelines](.cursor/rules/project-architecture.mdc) - Detailed architecture patterns
- [Android Guidelines](.cursor/rules/android-development-guidelines.mdc) - Kotlin coding standards
- [Technical Stack](.cursor/rules/android-technical-stack.mdc) - Technology choices and patterns