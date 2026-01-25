/*
 * Copyright (c) 2025 European Commission
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

package eu.europa.ec.quickidlogic.repository

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.networklogic.api.ApiClient
import eu.europa.ec.networklogic.model.ApiResponse
import eu.europa.ec.networklogic.model.request.CreateLivenessSessionRequest
import eu.europa.ec.networklogic.model.request.CreateQuickIdSessionRequest
import eu.europa.ec.networklogic.model.request.IssueAuthboundIdRequest
import eu.europa.ec.networklogic.model.response.LivenessSessionResponse
import eu.europa.ec.networklogic.model.response.VerificationResponse
import eu.europa.ec.quickidlogic.model.MrzData
import eu.europa.ec.quickidlogic.model.PassportData
import eu.europa.ec.quickidlogic.model.QuickIdSession
import eu.europa.ec.quickidlogic.model.VerificationResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository for QuickID verification operations.
 *
 * Manages session state and provides a clean interface to the QuickID API.
 * Implements [DefaultLifecycleObserver] to automatically clear sensitive session
 * data when the app goes to background.
 */
interface QuickIdRepository : DefaultLifecycleObserver {
    /**
     * Creates a new QuickID verification session.
     *
     * @return Result containing the session data or an error
     */
    suspend fun createSession(): Result<QuickIdSession>

    /**
     * Stores MRZ data that was extracted on-device using ML Kit.
     *
     * This replaces the server-side scanMrz API call with on-device storage.
     *
     * @param mrzData The MRZ data extracted by MrzTextRecognizer
     */
    suspend fun setMrzData(mrzData: MrzData)

    /**
     * Creates an AWS Liveness session for face verification.
     *
     * @return Result containing the liveness session ID and region
     */
    suspend fun createLivenessSession(): Result<LivenessSessionResponse>

    /**
     * Submits passport NFC data and liveness results for verification.
     *
     * @param passportData The data read from the passport NFC chip
     * @param livenessSessionId The completed AWS Liveness session ID
     * @return Result containing the verification result
     */
    suspend fun submitVerification(
        passportData: PassportData,
        livenessSessionId: String
    ): Result<VerificationResult>

    /**
     * Issues an Authbound ID credential after successful verification.
     *
     * @return Result containing the credential offer URI
     */
    suspend fun issueCredential(): Result<String>

    /**
     * Gets the current session if active.
     *
     * @return The current session or null if not active/expired
     */
    fun getCurrentSession(): QuickIdSession?

    /**
     * Gets the stored MRZ data from OCR scan.
     *
     * @return The MRZ data or null if not yet scanned
     */
    fun getMrzData(): MrzData?

    /**
     * Gets the liveness session data (session ID and region) for AWS Liveness.
     *
     * @return The liveness session data or null if not created yet
     */
    fun getLivenessSessionData(): LivenessSessionResponse?

    /**
     * Clears the current session and all stored data.
     */
    fun clearSession()
}

class QuickIdRepositoryImpl(
    private val apiClient: ApiClient,
    private val supabaseClient: SupabaseClient,
    private val configLogic: ConfigLogic
) : QuickIdRepository, DefaultLifecycleObserver {

    private var currentSession: QuickIdSession? = null
    private var currentMrzData: MrzData? = null
    private var currentLivenessSession: LivenessSessionResponse? = null
    private val sessionMutex = Mutex()

    companion object {
        private const val TAG = "QuickIdRepository"
        private const val CALLBACK_URL = "authbound://quickid/callback"
        private const val SESSION_REASON = "Authbound ID Verification"
    }

    /**
     * Called when the app goes to background.
     * Clears sensitive session data for security.
     */
    override fun onStop(owner: LifecycleOwner) {
        clearSession()
    }

    override suspend fun createSession(): Result<QuickIdSession> {
        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        val request = CreateQuickIdSessionRequest(
            callbackUrl = CALLBACK_URL,
            reason = SESSION_REASON
        )

        return when (val response = apiClient.createQuickIdSession(request, accessToken)) {
            is ApiResponse.Success -> {
                val session = QuickIdSession(
                    sessionId = response.body.sessionId,
                    clientToken = response.body.clientToken,
                    expiresAt = Instant.parse(response.body.expiresAt)
                )
                sessionMutex.withLock {
                    currentSession = session
                }
                Result.success(session)
            }
            is ApiResponse.Error -> {
                Result.failure(Exception(response.message))
            }
        }
    }

    override suspend fun setMrzData(mrzData: MrzData) {
        sessionMutex.withLock {
            currentMrzData = mrzData
        }
    }

    override suspend fun createLivenessSession(): Result<LivenessSessionResponse> {
        val session = getCurrentSession()
            ?: return Result.failure(IllegalStateException("No active QuickID session"))

        if (session.isExpired(Instant.fromEpochMilliseconds(System.currentTimeMillis()))) {
            clearSession()
            return Result.failure(IllegalStateException("Session expired"))
        }

        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        val request = CreateLivenessSessionRequest(sessionId = session.sessionId)

        return when (val response = apiClient.createLivenessSession(request, accessToken)) {
            is ApiResponse.Success -> {
                // Update session with liveness session ID and store liveness data
                sessionMutex.withLock {
                    currentSession = session.withLivenessSession(response.body.livenessSessionId)
                    currentLivenessSession = response.body
                }
                Result.success(response.body)
            }
            is ApiResponse.Error -> {
                Result.failure(Exception(response.message))
            }
        }
    }

    override suspend fun submitVerification(
        passportData: PassportData,
        livenessSessionId: String
    ): Result<VerificationResult> {
        val session = getCurrentSession()
            ?: return Result.failure(IllegalStateException("No active QuickID session"))

        if (session.isExpired(Instant.fromEpochMilliseconds(System.currentTimeMillis()))) {
            clearSession()
            return Result.failure(IllegalStateException("Session expired"))
        }

        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        // DEV ONLY: Override expiry date for testing with expired passports
        val effectivePassportExpiry = if (configLogic.skipPassportExpiryValidation) {
            val futureExpiry = getFutureExpiryDate()
            Log.w(TAG, "DEV MODE: Overriding passport expiry from ${passportData.passportExpiry} to $futureExpiry")
            futureExpiry
        } else {
            passportData.passportExpiry
        }

        return when (val response = apiClient.verifyNfcLiveness(
            bearerToken = accessToken,
            nfcFaceImage = passportData.faceImageBytes,
            nfcMrzData = passportData.mrzRaw,
            passportExpiry = effectivePassportExpiry,
            livenessSessionId = livenessSessionId
        )) {
            is ApiResponse.Success -> {
                Result.success(mapVerificationResponse(response.body))
            }
            is ApiResponse.Error -> {
                Result.failure(Exception(response.message))
            }
        }
    }

    /**
     * Returns a future expiry date (10 years from now) for testing purposes.
     */
    private fun getFutureExpiryDate(): String {
        val futureDate = LocalDate.now().plusYears(10)
        return futureDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    override suspend fun issueCredential(): Result<String> {
        val session = getCurrentSession()
            ?: return Result.failure(IllegalStateException("No active QuickID session"))

        val accessToken = getAccessToken()
            ?: return Result.failure(IllegalStateException("User not authenticated"))

        val request = IssueAuthboundIdRequest(
            quickidSessionId = session.sessionId
        )

        return when (val response = apiClient.issueAuthboundIdCredential(request, accessToken)) {
            is ApiResponse.Success -> {
                Result.success(response.body.credentialOfferUri)
            }
            is ApiResponse.Error -> {
                Result.failure(Exception(response.message))
            }
        }
    }

    override fun getCurrentSession(): QuickIdSession? {
        return currentSession?.takeIf { !it.isExpired(Instant.fromEpochMilliseconds(System.currentTimeMillis())) }
    }

    override fun getMrzData(): MrzData? {
        return currentMrzData
    }

    override fun getLivenessSessionData(): LivenessSessionResponse? {
        return currentLivenessSession
    }

    override fun clearSession() {
        currentSession = null
        currentMrzData = null
        currentLivenessSession = null
    }

    private suspend fun getAccessToken(): String? {
        return supabaseClient.auth.currentAccessTokenOrNull()
    }

    private fun mapVerificationResponse(response: VerificationResponse): VerificationResult {
        return if (response.status == VerificationResponse.STATUS_VERIFIED) {
            VerificationResult.Verified(
                sessionId = response.sessionId,
                firstName = response.documentData?.firstName,
                lastName = response.documentData?.lastName,
                dateOfBirth = response.documentData?.dateOfBirth,
                nationality = response.documentData?.nationality,
                documentNumber = response.documentData?.documentNumber,
                expiryDate = response.documentData?.expiryDate
            )
        } else {
            VerificationResult.Rejected(
                sessionId = response.sessionId,
                errorCode = response.lastError?.code ?: "UNKNOWN",
                errorMessage = response.lastError?.message ?: "Verification failed",
                isRecoverable = response.lastError?.recoverable ?: true
            )
        }
    }
}
