/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.presentationfeature.interactor

import android.content.Context
import android.util.Log
import eu.europa.ec.authenticationlogic.controller.authentication.BiometricsAvailability
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.authenticationlogic.model.BiometricCrypto
import eu.europa.ec.commonfeature.interactor.DeviceAuthenticationInteractor
import eu.europa.ec.corelogic.controller.SendRequestedDocumentsPartialState
import eu.europa.ec.corelogic.controller.WalletCorePartialState
import eu.europa.ec.corelogic.controller.WalletCorePresentationController
import eu.europa.ec.corelogic.model.AuthenticationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.net.URI
import org.json.JSONObject

sealed class PresentationLoadingObserveResponsePartialState {
    data class UserAuthenticationRequired(
        val authenticationData: List<AuthenticationData>,
    ) : PresentationLoadingObserveResponsePartialState()

    data class Failure(val error: String) : PresentationLoadingObserveResponsePartialState()
    data object Success : PresentationLoadingObserveResponsePartialState()
    data class Redirect(val uri: URI) : PresentationLoadingObserveResponsePartialState()
    data object RequestReadyToBeSent : PresentationLoadingObserveResponsePartialState()
}

sealed class PresentationLoadingSendRequestedDocumentPartialState {
    data class Failure(val error: String) : PresentationLoadingSendRequestedDocumentPartialState()
    data object Success : PresentationLoadingSendRequestedDocumentPartialState()
}

interface PresentationLoadingInteractor {
    fun observeResponse(): Flow<PresentationLoadingObserveResponsePartialState>
    fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState
    fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    )
}

class PresentationLoadingInteractorImpl(
    private val walletCorePresentationController: WalletCorePresentationController,
    private val deviceAuthenticationInteractor: DeviceAuthenticationInteractor,
) : PresentationLoadingInteractor {

    private val TAG = "PresentationLoadingInteractor"

    override fun observeResponse(): Flow<PresentationLoadingObserveResponsePartialState> =
        walletCorePresentationController.observeSentDocumentsRequest().mapNotNull { response ->
            Log.d(TAG, "Observing response type: ${response::class.simpleName}")
            when (response) {
                is WalletCorePartialState.Failure -> {
                    val errorMsg = response.error
                    Log.e(TAG, "Failure in observeResponse: $errorMsg")
                    // Log specific error types for better debugging
                    when {
                        errorMsg.contains("InvalidClientIdScheme") -> {
                            Log.e(TAG, "Client ID Scheme Error: Check if the client_id_scheme is properly set in the request")
                        }
                        errorMsg.contains("InvalidJarJwt") -> {
                            Log.e(TAG, "JAR JWT Error: JWT parsing failed. This could be due to malformed JWT, invalid signature, or expired token")
                        }
                        errorMsg.contains("resolution") -> {
                            Log.e(TAG, "Resolution Error: Issue with resolving the request or response")
                        }
                    }
                    PresentationLoadingObserveResponsePartialState.Failure(
                        error = errorMsg
                    )
                }

                is WalletCorePartialState.Redirect -> {
                    Log.d(TAG, "Redirect received to: ${response.uri}")
                    Log.d(TAG, "Redirect URI parameters: ${response.uri.query}")
                    PresentationLoadingObserveResponsePartialState.Redirect(
                        uri = response.uri
                    )
                }

                is WalletCorePartialState.Success -> {
                    Log.d(TAG, "Success in observeResponse")
                    PresentationLoadingObserveResponsePartialState.Success
                }

                is WalletCorePartialState.UserAuthenticationRequired -> {
                    Log.d(TAG, "User authentication required with ${response.authenticationData.size} auth data items")
                    response.authenticationData.forEachIndexed { index, authData ->
                        Log.d(TAG, "Auth data [$index]: type=${authData::class.simpleName}")
                    }
                    PresentationLoadingObserveResponsePartialState.UserAuthenticationRequired(
                        response.authenticationData
                    )
                }

                is WalletCorePartialState.RequestIsReadyToBeSent -> {
                    Log.d(TAG, "Request is ready to be sent")
                    PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent
                }
            }
        }

    override fun sendRequestedDocuments(): PresentationLoadingSendRequestedDocumentPartialState {
        Log.d(TAG, "Attempting to send requested documents")
        try {
            val result = walletCorePresentationController.sendRequestedDocuments()
            Log.d(TAG, "Send documents result type: ${result::class.simpleName}")
            
            return when (result) {
                is SendRequestedDocumentsPartialState.RequestSent -> {
                    Log.d(TAG, "Documents request sent successfully")
                    PresentationLoadingSendRequestedDocumentPartialState.Success
                }
                is SendRequestedDocumentsPartialState.Failure -> {
                    val errorMsg = result.error
                    Log.e(TAG, "Failed to send documents request: $errorMsg")
                    // Additional error type logging
                    when {
                        errorMsg.contains("JWT") -> {
                            Log.e(TAG, "JWT-related error in document sending")
                        }
                        errorMsg.contains("resolution") -> {
                            Log.e(TAG, "Resolution error in document sending")
                        }
                    }
                    PresentationLoadingSendRequestedDocumentPartialState.Failure(
                        result.error
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while sending documents", e)
            return PresentationLoadingSendRequestedDocumentPartialState.Failure(
                e.message ?: "Unknown error during document sending"
            )
        }
    }

    override fun handleUserAuthentication(
        context: Context,
        crypto: BiometricCrypto,
        notifyOnAuthenticationFailure: Boolean,
        resultHandler: DeviceAuthenticationResult,
    ) {
        Log.d(TAG, "Handling user authentication with crypto type: ${crypto::class.simpleName}")
        deviceAuthenticationInteractor.getBiometricsAvailability {
            when (it) {
                is BiometricsAvailability.CanAuthenticate -> {
                    Log.d(TAG, "Biometric authentication available, proceeding with auth")
                    try {
                        deviceAuthenticationInteractor.authenticateWithBiometrics(
                            context = context,
                            crypto = crypto,
                            notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                            resultHandler = resultHandler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during biometric authentication", e)
                        resultHandler.onAuthenticationFailure()
                    }
                }

                is BiometricsAvailability.NonEnrolled -> {
                    Log.d(TAG, "Biometrics not enrolled, launching system screen")
                    deviceAuthenticationInteractor.launchBiometricSystemScreen()
                }

                is BiometricsAvailability.Failure -> {
                    Log.e(TAG, "Biometric availability check failed")
                    resultHandler.onAuthenticationFailure()
                }
            }
        }
    }
}