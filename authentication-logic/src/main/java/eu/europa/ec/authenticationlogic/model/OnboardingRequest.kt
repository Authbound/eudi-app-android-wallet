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
package eu.europa.ec.authenticationlogic.model

import eu.europa.ec.businesslogic.model.DeviceInfo

/**
 * Request model for atomic onboarding operation.
 * 
 * Contains all information needed to complete user onboarding in a single operation:
 * - Profile information (handle, display name)
 * - Device information for wallet attestation
 * - Push notification token
 */
data class OnboardingRequest(
    /**
     * User's chosen handle - must be unique across the platform
     */
    val handle: String,
    
    /**
     * User's display name
     */
    val displayName: String,
    
    /**
     * Device information for wallet attestation creation
     */
    val deviceInfo: DeviceInfo,
    
    /**
     * Push notification token for device registration
     */
    val pushToken: String
)