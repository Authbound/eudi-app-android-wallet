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

import android.content.Context
import eu.europa.ec.authenticationlogic.model.EmailPasswordRequest
import eu.europa.ec.authenticationlogic.model.OAuthProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.auth

import io.github.jan.supabase.auth.status.SessionStatus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SupabaseAuthRepositoryImpl(
    private val supabaseClient: SupabaseClient
) : SupabaseAuthRepository {

    override suspend fun isUserAuthenticated(): Boolean {
        val sessionStatus = supabaseClient.auth.sessionStatus.first()
        return sessionStatus is SessionStatus.Authenticated
    }

    override fun observeAuthState(): Flow<SessionStatus> = supabaseClient.auth.sessionStatus

    override suspend fun signInWithEmailPassword(request: EmailPasswordRequest) {
        supabaseClient.auth.signInWith(Email) {
            email = request.email
            password = request.password
        }
    }

    override suspend fun signInWithOAuth(provider: OAuthProvider, context: Context) {
        val supabaseProvider = when (provider) {
            OAuthProvider.GOOGLE -> Google
            OAuthProvider.MICROSOFT -> Azure
            OAuthProvider.META -> Facebook
        }
        supabaseClient.auth.signInWith(
            provider = supabaseProvider,
            redirectUrl = "io.supabase.YOUR_SUPABASE_PROJECT_ID://login-callback"
        )
    }

    override suspend fun signOut() {
        supabaseClient.auth.signOut()
    }
} 