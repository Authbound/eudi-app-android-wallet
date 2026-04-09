/*
 * Copyright (c) 2026 European Commission
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

import eu.europa.ec.authenticationlogic.model.LegalAcceptanceSnapshot
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2

interface GetLegalAcceptanceStateUseCase {
    suspend operator fun invoke(): Result<LegalAcceptanceSnapshot>
}

class GetLegalAcceptanceStateUseCaseImpl(
    private val prefKeys: PrefKeysV2,
    private val profileRepository: ProfileRepository,
    private val logController: LogController
) : GetLegalAcceptanceStateUseCase {

    override suspend fun invoke(): Result<LegalAcceptanceSnapshot> {
        return try {
            val profile: Profile = profileRepository.getMyProfile().getOrThrow()
            val profileCompleted: Boolean =
                profile.handle?.isNotBlank() == true && profile.displayName?.isNotBlank() == true
            runCatching { prefKeys.setProfileCompleted(profileCompleted) }
            val snapshot: LegalAcceptanceSnapshot = profile.legalAcceptance?.let { legalAcceptance ->
                LegalAcceptanceSnapshot(
                    requiredTermsVersion = legalAcceptance.requiredTermsVersion.orEmpty(),
                    acceptedTermsVersion = legalAcceptance.acceptedTermsVersion,
                    acceptedTermsAt = legalAcceptance.acceptedTermsAt,
                    requiredPrivacyVersion = legalAcceptance.requiredPrivacyVersion.orEmpty(),
                    acknowledgedPrivacyVersion = legalAcceptance.acknowledgedPrivacyVersion,
                    acknowledgedPrivacyAt = legalAcceptance.acknowledgedPrivacyAt
                )
            } ?: return Result.failure(
                IllegalStateException("Legal acceptance requirements are unavailable")
            )
            if (!snapshot.isRequirementConfigured) {
                return Result.failure(
                    IllegalStateException("Legal acceptance requirements are not configured")
                )
            }
            cacheSnapshot(snapshot)
            Result.success(snapshot)
        } catch (e: Exception) {
            logController.w(TAG) { "Failed to refresh legal acceptance: ${e.message}" }
            Result.failure(e)
        }
    }

    private suspend fun cacheSnapshot(snapshot: LegalAcceptanceSnapshot) {
        prefKeys.setRequiredTermsVersion(snapshot.requiredTermsVersion)
        prefKeys.setAcceptedTermsVersion(snapshot.acceptedTermsVersion.orEmpty())
        prefKeys.setAcceptedTermsAt(snapshot.acceptedTermsAt.orEmpty())
        prefKeys.setRequiredPrivacyVersion(snapshot.requiredPrivacyVersion)
        prefKeys.setAcknowledgedPrivacyVersion(snapshot.acknowledgedPrivacyVersion.orEmpty())
        prefKeys.setAcknowledgedPrivacyAt(snapshot.acknowledgedPrivacyAt.orEmpty())
    }

    private companion object {
        const val TAG: String = "GetLegalAcceptanceState"
    }
}
