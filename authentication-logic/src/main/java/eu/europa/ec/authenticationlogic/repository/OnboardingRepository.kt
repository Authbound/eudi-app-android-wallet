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

import eu.europa.ec.authenticationlogic.model.OnboardingRequest
import eu.europa.ec.authenticationlogic.model.OnboardingResult

/**
 * Repository interface for atomic onboarding operations.
 * 
 * This interface defines the contract for completing user onboarding
 * in a single, atomic operation that either succeeds completely or
 * fails completely with no partial state.
 */
interface OnboardingRepository {
    
    /**
     * Completes the entire user onboarding process atomically.
     * 
     * This operation must be implemented as an atomic transaction that:
     * 1. Creates/updates the user profile
     * 2. Creates the wallet attestation
     * 3. Registers the device
     * 4. Sets up push notifications
     * 
     * If any step fails, the entire operation must be rolled back.
     * 
     * @param request Complete onboarding request
     * @return Result with onboarding details or specific error
     */
    suspend fun completeOnboarding(request: OnboardingRequest): Result<OnboardingResult>
}