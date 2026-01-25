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

package eu.europa.ec.quickidlogic.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.quickidlogic.model.PassportData
import eu.europa.ec.quickidlogic.model.VerificationResult
import eu.europa.ec.quickidlogic.repository.QuickIdRepository
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Partial states for verification operations.
 */
sealed class VerificationPartialState {
    /**
     * Verification is in progress.
     */
    data class InProgress(val message: String) : VerificationPartialState()

    /**
     * Verification completed successfully.
     */
    data class Verified(val result: VerificationResult.Verified) : VerificationPartialState()

    /**
     * Verification was rejected.
     */
    data class Rejected(val result: VerificationResult.Rejected) : VerificationPartialState()

    /**
     * Verification operation failed.
     */
    data class Failure(val errorMessage: String) : VerificationPartialState()
}

/**
 * Partial states for credential issuance operations.
 */
sealed class CredentialIssuancePartialState {
    /**
     * Credential issuance in progress.
     */
    data object InProgress : CredentialIssuancePartialState()

    /**
     * Credential offer received.
     */
    data class CredentialOfferReceived(val credentialOfferUri: String) : CredentialIssuancePartialState()

    /**
     * Credential issuance failed.
     */
    data class Failure(val errorMessage: String) : CredentialIssuancePartialState()
}

/**
 * Interactor for verification and credential issuance operations.
 *
 * Handles submitting verification data and issuing Authbound ID credentials.
 */
interface VerificationInteractor {
    /**
     * Submits passport data and liveness results for verification.
     *
     * @param passportData The data read from the passport NFC chip
     * @param livenessSessionId The completed AWS Liveness session ID
     * @return Flow emitting the verification result
     */
    fun submitVerification(
        passportData: PassportData,
        livenessSessionId: String
    ): Flow<VerificationPartialState>

    /**
     * Issues an Authbound ID credential after successful verification.
     *
     * @return Flow emitting the credential issuance result
     */
    fun issueCredential(): Flow<CredentialIssuancePartialState>

    /**
     * Stores passport data temporarily for later submission.
     */
    fun storePassportData(passportData: PassportData)

    /**
     * Retrieves stored passport data.
     */
    fun getStoredPassportData(): PassportData?

    /**
     * Clears stored passport data.
     */
    fun clearStoredPassportData()
}

class VerificationInteractorImpl(
    private val quickIdRepository: QuickIdRepository,
    private val resourceProvider: ResourceProvider
) : VerificationInteractor {

    private var storedPassportData: PassportData? = null

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    override fun submitVerification(
        passportData: PassportData,
        livenessSessionId: String
    ): Flow<VerificationPartialState> = flow {
        emit(VerificationPartialState.InProgress("Verifying your identity..."))

        quickIdRepository.submitVerification(passportData, livenessSessionId)
            .onSuccess { result ->
                when (result) {
                    is VerificationResult.Verified -> {
                        emit(VerificationPartialState.Verified(result))
                    }
                    is VerificationResult.Rejected -> {
                        emit(VerificationPartialState.Rejected(result))
                    }
                }
            }
            .onFailure { error ->
                emit(VerificationPartialState.Failure(
                    errorMessage = error.message ?: genericErrorMsg
                ))
            }
    }.safeAsync {
        VerificationPartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMsg
        )
    }

    override fun issueCredential(): Flow<CredentialIssuancePartialState> = flow {
        emit(CredentialIssuancePartialState.InProgress)

        quickIdRepository.issueCredential()
            .onSuccess { credentialOfferUri ->
                emit(CredentialIssuancePartialState.CredentialOfferReceived(credentialOfferUri))
            }
            .onFailure { error ->
                emit(CredentialIssuancePartialState.Failure(
                    errorMessage = error.message ?: genericErrorMsg
                ))
            }
    }.safeAsync {
        CredentialIssuancePartialState.Failure(
            errorMessage = it.localizedMessage ?: genericErrorMsg
        )
    }

    override fun storePassportData(passportData: PassportData) {
        storedPassportData = passportData
    }

    override fun getStoredPassportData(): PassportData? {
        return storedPassportData
    }

    override fun clearStoredPassportData() {
        storedPassportData = null
    }
}
