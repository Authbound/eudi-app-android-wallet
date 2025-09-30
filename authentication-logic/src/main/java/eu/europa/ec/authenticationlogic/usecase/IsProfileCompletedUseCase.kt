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
import eu.europa.ec.businesslogic.controller.storage.PrefKeys

interface IsProfileCompletedUseCase {
    suspend operator fun invoke(): Boolean
}

class IsProfileCompletedUseCaseImpl(
    private val prefKeys: PrefKeys,
    private val profileRepository: ProfileRepository // optional; see below
) : IsProfileCompletedUseCase {
    override suspend fun invoke(): Boolean {
        // Start with the cached flag for speed/offline:
        val cached = try {
            prefKeys.getIsProfileCompletedSafe()
        } catch (_: Exception) {
            null
        }
        if (cached == true) return true

        // (Optional) If you want to be stricter, verify from backend when logged in:
        val profile = profileRepository.getMyProfile().getOrNull()
        val complete = profile?.handle?.isNotBlank() == true && profile.displayName.isNotBlank()
        if (complete) prefKeys.setProfileCompleted(true)
        return complete


    }
}
