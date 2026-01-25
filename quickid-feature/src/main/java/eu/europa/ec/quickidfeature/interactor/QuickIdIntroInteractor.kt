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

package eu.europa.ec.quickidfeature.interactor

import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionInteractor
import eu.europa.ec.quickidlogic.interactor.QuickIdSessionPartialState
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Partial states for the QuickID intro screen.
 */
sealed class QuickIdIntroPartialState {
    /**
     * Initial state showing info and requirements.
     */
    data class Ready(
        val title: String,
        val description: String,
        val requirements: List<String>
    ) : QuickIdIntroPartialState()

    /**
     * Session is being created.
     */
    data object CreatingSession : QuickIdIntroPartialState()

    /**
     * Session created successfully, ready to proceed.
     */
    data class SessionCreated(val sessionId: String) : QuickIdIntroPartialState()

    /**
     * Session creation failed.
     */
    data class Failure(val errorMessage: String) : QuickIdIntroPartialState()
}

/**
 * Interactor for the QuickID intro screen.
 *
 * Provides intro content and initiates the QuickID session.
 */
interface QuickIdIntroInteractor {
    /**
     * Gets the intro content to display.
     */
    fun getIntroContent(): Flow<QuickIdIntroPartialState>

    /**
     * Starts the QuickID verification by creating a session.
     */
    fun startVerification(): Flow<QuickIdIntroPartialState>
}

class QuickIdIntroInteractorImpl(
    private val quickIdSessionInteractor: QuickIdSessionInteractor,
    private val resourceProvider: ResourceProvider
) : QuickIdIntroInteractor {

    private val genericErrorMsg: String
        get() = resourceProvider.genericErrorMessage()

    override fun getIntroContent(): Flow<QuickIdIntroPartialState> = flow {
        emit(QuickIdIntroPartialState.Ready(
            title = "Get your Authbound ID",
            description = "Verify your identity using your passport's NFC chip and a quick face scan. This creates a secure digital credential you can use across services.",
            requirements = listOf(
                "A passport with an NFC chip (biometric passport)",
                "Your passport's document number, date of birth, and expiry date",
                "A well-lit area for the face verification",
                "NFC enabled on your device"
            )
        ))
    }

    override fun startVerification(): Flow<QuickIdIntroPartialState> = flow {
        emit(QuickIdIntroPartialState.CreatingSession)

        quickIdSessionInteractor.createSession().collect { state ->
            when (state) {
                is QuickIdSessionPartialState.SessionCreated -> {
                    emit(QuickIdIntroPartialState.SessionCreated(state.sessionId))
                }
                is QuickIdSessionPartialState.Failure -> {
                    emit(QuickIdIntroPartialState.Failure(state.errorMessage))
                }
                else -> {
                    // Ignore other states
                }
            }
        }
    }.safeAsync {
        QuickIdIntroPartialState.Failure(it.localizedMessage ?: genericErrorMsg)
    }
}
