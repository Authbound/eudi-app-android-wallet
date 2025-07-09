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
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))
            
            val response = apiClient.checkHandleAvailability(handle, token)
            if (response.isSuccessful) {
                Result.success(response.body()?.available ?: false)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
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