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
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo

import kotlinx.coroutines.flow.Flow

interface SupabaseAuthRepository {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun getCurrentUser(): UserInfo?
    fun observeAuthState(): Flow<SessionStatus>
    suspend fun signInWithEmailPassword(request: EmailPasswordRequest)
    suspend fun signUpWithEmailPassword(request: EmailPasswordRequest)
    suspend fun signInWithOAuth(provider: OAuthProvider, context: Context)
    suspend fun signOut()
}