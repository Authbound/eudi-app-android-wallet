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

package eu.europa.ec.authenticationlogic.usecase

import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2

/**
 * V2 implementation with simplified error handling.
 * 
 * No try-catch needed since safe accessors don't throw exceptions.
 */
class IsProfileCompletedUseCaseV2Impl(
    private val prefKeys: PrefKeysV2,
    private val profileRepository: ProfileRepository
) : IsProfileCompletedUseCase {
    
    override suspend fun invoke(): Boolean {
        // Check cached flag first (fast path)
        val cached = prefKeys.isProfileCompletedSafe()
        if (cached) return true

        // Verify from backend when not cached
        val profile = profileRepository.getMyProfile().getOrNull()
        val complete = profile?.handle?.isNotBlank() == true && 
                      profile.displayName.isNotBlank()
        
        // Cache the result for next time
        if (complete) {
            prefKeys.setProfileCompleted(true)
        }
        
        return complete
    }
}
