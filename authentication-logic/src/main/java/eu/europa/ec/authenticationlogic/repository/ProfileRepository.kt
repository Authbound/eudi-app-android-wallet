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

import android.util.Log
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.CompleteProfileRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

interface ProfileRepository {
    suspend fun completeProfile(request: CompleteProfileRequest): Result<Unit>
    suspend fun checkHandle(handle: String): Result<Boolean>
    suspend fun getMyProfile(): Result<Profile>
}

class ProfileRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient
) : ProfileRepository {

    companion object {
        private const val TAG = "ProfileRepository"
    }

    override suspend fun completeProfile(request: CompleteProfileRequest): Result<Unit> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))
            
            val response = apiClient.completeProfile(request, token)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkHandle(handle: String): Result<Boolean> {
        return try {
            Log.d(TAG, "checkHandle: Starting handle check for '$handle'")

            val session = supabaseClient.auth.currentSessionOrNull()
            val token = session?.accessToken

            if (token == null) {
                Log.e(TAG, "checkHandle: No auth token available. Session: ${session != null}")
                return Result.failure(Exception("User not authenticated - no access token"))
            }

            Log.d(TAG, "checkHandle: Got auth token (${token.take(20)}...), calling API")

            val response = apiClient.checkHandleAvailability(handle, token)

            Log.d(TAG, "checkHandle: Response - isSuccessful=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val available = response.body()?.available ?: false
                Log.d(TAG, "checkHandle: Handle '$handle' available=$available")
                Result.success(available)
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e(TAG, "checkHandle: API error - $errorMsg, errorBody=${response.errorBody()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkHandle: Exception - ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getMyProfile(): Result<Profile> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))
            
            val response = apiClient.getMyProfile(token)
            if (response.isSuccessful) {
                val profileResponse = response.body()
                    ?: return Result.failure(Exception("Empty response body"))
                
                // Convert NetworkLogic ProfileResponse to AuthenticationLogic Profile
                val profile = Profile(
                    id = profileResponse.id,
                    handle = profileResponse.handle,
                    displayName = profileResponse.displayName
                )
                Result.success(profile)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 