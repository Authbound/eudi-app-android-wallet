# Supabase B2C Auth & DB Implementation Plan

This document outlines the plan for integrating Supabase as the backend for B2C authentication and database services in the EUDI Wallet Android application.

### **Progress Tracker**
- **Plan Definition**: :white_check_mark:
- **Phase 1: Project Setup & Supabase Configuration**: :white_large_square:
- **Phase 2: Authentication Logic (`authentication-logic`)**: :white_large_square:
- **Phase 3: Authentication UI (`authentication-feature`)**: :white_large_square:
- **Phase 4: Integration & Navigation**: :white_large_square:

---

## 1. Introduction

- **What prompted us to take action?** The application requires a robust B2C user authentication system and a scalable backend database to support its features, all while fulfilling EUDI RAF requirements.
- **What issue was identified?** The base application lacks an integrated authentication and data persistence mechanism suitable for a B2C context.
- **Why was this solution chosen?** Supabase was selected for its integrated Backend-as-a-Service (BaaS) offering. It provides a seamless solution for both authentication (email/password, social logins) and a full-fledged Postgres database. The `supabase-kt` Kotlin SDK allows for direct, type-safe interaction, significantly simplifying the development process and reducing the need for a separate backend service for these core features.
- **What outcome is anticipated?** A secure and streamlined login/signup flow for B2C users. The user's authentication state will be managed consistently, and user data will be persisted directly in the Supabase database, accessible throughout the application.

---

## 2. Architecture & Strategy

- **Architectural Patterns**: We will continue to follow the established **Clean Architecture**, **MVI (Model-View-Intent)**, and **Repository** patterns. This ensures our Supabase integration is clean, decoupled, and testable.
- **Module Strategy**: The separation of concerns between `authentication-feature` (UI) and `authentication-logic` (business logic) remains the best approach.
- **Authentication Flow (Email/Password)**:
    1.  The user enters their email and password into the UI.
    2.  The ViewModel, via a Use Case, calls the `SupabaseAuthRepository`.
    3.  The repository uses the `supabase-kt` SDK to call `auth.signUpWith(...)` or `auth.signInWith(...)`.
    4.  The SDK handles all network communication with the Supabase backend.
    5.  The SDK also manages session persistence, automatically handling JWT storage and token refresh. This simplifies development and enhances security.

---

## 3. Module Breakdown

- **`authentication-feature` (New Module)**
    - **Responsibility**: All UI for the signup/login flow.
    - **Key Components**:
        - `AuthenticationActivity`: Hosts fragments. Handles deep links for OAuth callbacks if we add social logins later.
        - `LoginFragment`: UI for email/password sign-in.
        - `SignUpFragment`: UI for new user registration.
        - `AuthenticationViewModel`: MVI ViewModel to manage UI state (e.g., input validation, loading indicators), handle user events, and trigger use cases.

- **`authentication-logic` (Existing Module)**
    - **Responsibility**: Core logic for interacting with Supabase.
    - **Key Components**:
        - `SupabaseClient` (DI): A singleton instance of the Supabase client will be provided via dependency injection (Koin).
        - `SupabaseAuthRepository`: A repository that wraps the `supabase-kt` SDK calls (e.g., `client.auth.signUp`, `client.auth.signIn`, `client.auth.session`). This abstracts the SDK from the rest of the app.
        - **Use Cases**: `SignUpUseCase`, `LoginUseCase`, `IsUserAuthenticatedUseCase`, `LogoutUseCase`, `GetCurrentUserUseCase`.

- **`business-logic` & Other Features**
    - **Responsibility**: To interact with the Supabase database for feature-specific data.
    - **Changes**: We can now create new repositories (e.g., `DocumentsRepository`, `UserProfileRepository`) that use the `Postgrest` component of the injected `SupabaseClient` to perform CRUD operations on our database tables, protected by Row Level Security.

- **`startup-feature` (Existing Module)**
    - **Responsibility**: Determine the app's initial screen.
    - **Changes**: The `SplashViewModel` will use `IsUserAuthenticatedUseCase` to check the Supabase session state and navigate to `MainActivity` (if logged in) or `AuthenticationActivity` (if logged out).

---

## 4. Step-by-Step Implementation

### Phase 1: Project Setup & Supabase Configuration
- :white_large_square: **Create `authentication-feature` Module**: Set up the new Android Library module.
- :white_large_square: **Configure Supabase Project**:
    - In the Supabase dashboard, create a new project.
    - Obtain the **Project URL** and **anon key**.
- :white_large_square: **Securely Store Credentials**: Add the Supabase URL and anon key to the project's `local.properties` file and expose them to the app via `build.gradle.kts`'s `buildConfigField`. This keeps credentials out of version control.
- :white_large_square: **Update Dependencies**: Add the `supabase-kt` BOM and necessary modules (`gotrue-kt` for auth, `postgrest-kt` for database) to the project's `build.gradle.kts` files.

### Phase 2: Authentication Logic (`authentication-logic`)
- :white_large_square: **Provide SupabaseClient**: Using Koin (the project's existing DI framework), create a module that provides a singleton `SupabaseClient`, initialized with the credentials from `BuildConfig`.
- :white_large_square: **Implement `SupabaseAuthRepository`**: Create the repository and inject the `SupabaseClient`. Implement methods for `signIn`, `signUp`, `signOut`, and `getCurrentSession` that call the corresponding `client.auth` methods.
- :white_large_square: **Implement Use Cases**: Build the use cases that will be called from the ViewModel, orchestrating calls to the new repository.

### Phase 3: Authentication UI (`authentication-feature`)
- :white_large_square: **Create `AuthenticationActivity`, `LoginFragment`, `SignUpFragment`**: Build the necessary XML layouts for user input (email, password) and actions (buttons).
- :white_large_square: **Implement `AuthenticationViewModel`**: Wire up the UI to the use cases. Use MVI to manage state (loading, errors, success) and handle navigation upon successful authentication.

### Phase 4: Integration & Data Interaction
- :white_large_square: **Update `startup-feature`**: Integrate `IsUserAuthenticatedUseCase` into the splash screen to manage the initial user journey.
- :white_large_square: **Implement Data Repositories**: As an example, create a `UserProfileRepository` that fetches user-specific data from a `profiles` table in Supabase, demonstrating how to use the database client (`postgrest`).
- :white_large_square: **Implement Logout**: Add a logout flow that calls the `LogoutUseCase` and navigates the user back to the `LoginFragment`. 