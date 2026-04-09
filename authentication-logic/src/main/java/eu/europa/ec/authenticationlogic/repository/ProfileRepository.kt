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

import eu.europa.ec.authenticationlogic.model.AccountDeletion
import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import eu.europa.ec.authenticationlogic.model.LegalAcceptance
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.request.CompleteProfileRequest
import eu.europa.ec.networklogic.model.request.RecordLegalAcceptanceRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

/**
 * Exception from profile API calls that preserves the HTTP status code.
 * Allows downstream error classification (e.g., 400 server error vs network failure).
 */
class ProfileApiException(
    val httpCode: Int,
    val httpMessage: String,
    val errorBody: String? = null
) : Exception("HTTP $httpCode: $httpMessage")

interface ProfileRepository {
    suspend fun completeProfile(request: CompleteProfileRequest): Result<Unit>
    suspend fun checkHandle(handle: String): Result<Boolean>
    suspend fun getMyProfile(): Result<Profile>
    suspend fun recordLegalAcceptance(request: RecordLegalAcceptanceRequest): Result<LegalAcceptanceSnapshot>
    suspend fun requestAccountDeletion(): Result<AccountDeletion>
    suspend fun cancelAccountDeletion(): Result<AccountDeletion>
}

class ProfileRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient,
    private val logController: LogController
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
                Result.failure(ProfileApiException(
                    httpCode = response.code(),
                    httpMessage = response.message(),
                    errorBody = response.errorBody()
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkHandle(handle: String): Result<Boolean> {
        return try {
            logController.d(TAG, "checkHandle: Starting handle check for '$handle'")

            val session = supabaseClient.auth.currentSessionOrNull()
            val token = session?.accessToken

            if (token == null) {
                logController.e(TAG) { "checkHandle: No auth token available. Session: ${session != null}" }
                return Result.failure(Exception("User not authenticated - no access token"))
            }

            val response = apiClient.checkHandleAvailability(handle, token)

            logController.d(TAG, "checkHandle: Response code=${response.code()}")

            if (response.isSuccessful) {
                val available = response.body()?.available ?: false
                logController.d(TAG, "checkHandle: Handle '$handle' available=$available")
                Result.success(available)
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                logController.e(TAG) { "checkHandle: API error - $errorMsg" }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            logController.e(TAG) { "checkHandle: Exception - ${e.javaClass.simpleName}: ${e.message}" }
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
                    displayName = profileResponse.displayName,
                    legalAcceptance = profileResponse.legalAcceptance?.let { legalAcceptance ->
                        LegalAcceptance(
                            requiredTermsVersion = legalAcceptance.requiredTermsVersion,
                            acceptedTermsVersion = legalAcceptance.acceptedTermsVersion,
                            acceptedTermsAt = legalAcceptance.acceptedTermsAt,
                            requiredPrivacyVersion = legalAcceptance.requiredPrivacyVersion,
                            acknowledgedPrivacyVersion = legalAcceptance.acknowledgedPrivacyVersion,
                            acknowledgedPrivacyAt = legalAcceptance.acknowledgedPrivacyAt
                        )
                    },
                    accountDeletion = profileResponse.accountDeletion?.let { accountDeletion ->
                        AccountDeletion(
                            status = accountDeletion.status,
                            requestedAt = accountDeletion.requestedAt,
                            scheduledFor = accountDeletion.scheduledFor,
                            canCancel = accountDeletion.canCancel
                        )
                    },
                )
                Result.success(profile)
            } else {
                Result.failure(ProfileApiException(
                    httpCode = response.code(),
                    httpMessage = response.message(),
                    errorBody = response.errorBody()
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordLegalAcceptance(request: RecordLegalAcceptanceRequest): Result<LegalAcceptanceSnapshot> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))
            val response = apiClient.recordLegalAcceptance(request, token)
            if (response.isSuccessful) {
                val legalAcceptance = response.body()?.legalAcceptance
                    ?: return Result.failure(Exception("Empty legal acceptance response"))
                Result.success(
                    LegalAcceptanceSnapshot(
                        requiredTermsVersion = legalAcceptance.requiredTermsVersion.orEmpty(),
                        acceptedTermsVersion = legalAcceptance.acceptedTermsVersion,
                        acceptedTermsAt = legalAcceptance.acceptedTermsAt,
                        requiredPrivacyVersion = legalAcceptance.requiredPrivacyVersion.orEmpty(),
                        acknowledgedPrivacyVersion = legalAcceptance.acknowledgedPrivacyVersion,
                        acknowledgedPrivacyAt = legalAcceptance.acknowledgedPrivacyAt
                    )
                )
            } else {
                Result.failure(ProfileApiException(
                    httpCode = response.code(),
                    httpMessage = response.message(),
                    errorBody = response.errorBody()
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun requestAccountDeletion(): Result<AccountDeletion> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))
            val response = apiClient.requestAccountDeletion(token)
            if (response.isSuccessful) {
                val accountDeletion = response.body()?.accountDeletion
                    ?: return Result.failure(Exception("Empty account deletion response"))
                Result.success(
                    AccountDeletion(
                        status = accountDeletion.status,
                        requestedAt = accountDeletion.requestedAt,
                        scheduledFor = accountDeletion.scheduledFor,
                        canCancel = accountDeletion.canCancel
                    )
                )
            } else {
                Result.failure(ProfileApiException(
                    httpCode = response.code(),
                    httpMessage = response.message(),
                    errorBody = response.errorBody()
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelAccountDeletion(): Result<AccountDeletion> {
        return try {
            val token = supabaseClient.auth.currentSessionOrNull()?.accessToken
                ?: return Result.failure(Exception("User not authenticated"))
            val response = apiClient.cancelAccountDeletion(token)
            if (response.isSuccessful) {
                val accountDeletion = response.body()?.accountDeletion
                    ?: return Result.failure(Exception("Empty account deletion response"))
                Result.success(
                    AccountDeletion(
                        status = accountDeletion.status,
                        requestedAt = accountDeletion.requestedAt,
                        scheduledFor = accountDeletion.scheduledFor,
                        canCancel = accountDeletion.canCancel
                    )
                )
            } else {
                Result.failure(ProfileApiException(
                    httpCode = response.code(),
                    httpMessage = response.message(),
                    errorBody = response.errorBody()
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
