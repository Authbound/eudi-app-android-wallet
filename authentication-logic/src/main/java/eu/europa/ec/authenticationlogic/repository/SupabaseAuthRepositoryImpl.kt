/*
 * Copyright (c) 2024 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */
package eu.europa.ec.authenticationlogic.repository

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import eu.europa.ec.authenticationlogic.model.EmailPasswordRequest
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.auth
import eu.europa.ec.authenticationlogic.BuildConfig

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID

class SupabaseAuthRepositoryImpl(
    private val supabaseClient: SupabaseClient
) : SupabaseAuthRepository {

    private companion object {
        private const val E2E_USER_ID = "e2e-wallet-user"
    }

    override suspend fun isUserAuthenticated(): Boolean {
        val sessionStatus = supabaseClient.auth.sessionStatus.first { it !is SessionStatus.Initializing}
        return supabaseClient.auth.currentSessionOrNull() != null || BuildConfig.E2E_MODE
    }

    override suspend fun getCurrentUser(): UserInfo? {
        return supabaseClient.auth.currentSessionOrNull()?.user
    }

    override suspend fun getCurrentUserId(): String? {
        return supabaseClient.auth.currentSessionOrNull()?.user?.id
            ?: if (BuildConfig.E2E_MODE) E2E_USER_ID else null
    }

    override suspend fun getAccessToken(): String? {
        return supabaseClient.auth.currentAccessTokenOrNull()
    }

    override fun observeAuthState(): Flow<SessionStatus> = supabaseClient.auth.sessionStatus

    override suspend fun signInWithEmailPassword(request: EmailPasswordRequest) {
        supabaseClient.auth.signInWith(Email) {
            email = request.email
            password = request.password
        }
    }

    override suspend fun signUpWithEmailPassword(request: EmailPasswordRequest) {
        supabaseClient.auth.signUpWith(Email) {
            email = request.email
            password = request.password
        }
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider, context: Context) {
        when (provider) {
            OAuthProvider.GOOGLE -> signInWithGoogle(context)
            OAuthProvider.MICROSOFT -> {
                supabaseClient.auth.signInWith(
                    provider = Azure,
                    redirectUrl = "${BuildConfig.DEEPLINK}://login-callback"
                )
            }
            OAuthProvider.META -> {
                supabaseClient.auth.signInWith(
                    provider = Facebook,
                    redirectUrl = "${BuildConfig.DEEPLINK}://login-callback"
                )
            }
        }
    }

    private suspend fun signInWithGoogle(context: Context) {
        // Generate a nonce for security (prevents token replay attacks)
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_AUTH_CLIENT_ID)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(
            context = context as Activity,
            request = request
        )

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(
            result.credential.data
        )

        supabaseClient.auth.signInWith(IDToken) {
            this.provider = Google
            this.idToken = googleIdTokenCredential.idToken
            this.nonce = rawNonce
        }
    }

    override suspend fun signOut() {
        supabaseClient.auth.signOut()
    }
} 
