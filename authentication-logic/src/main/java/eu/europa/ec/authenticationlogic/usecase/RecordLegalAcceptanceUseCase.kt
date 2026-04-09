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
import eu.europa.ec.authenticationlogic.repository.ProfileRepository
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.storage.PrefKeysV2
import eu.europa.ec.networklogic.model.request.RecordLegalAcceptanceRequest

interface RecordLegalAcceptanceUseCase {
    suspend operator fun invoke(snapshot: LegalAcceptanceSnapshot): Result<LegalAcceptanceSnapshot>
}

class RecordLegalAcceptanceUseCaseImpl(
    private val profileRepository: ProfileRepository,
    private val prefKeys: PrefKeysV2,
    private val configLogic: ConfigLogic
) : RecordLegalAcceptanceUseCase {

    override suspend fun invoke(snapshot: LegalAcceptanceSnapshot): Result<LegalAcceptanceSnapshot> {
        if (!snapshot.isRequirementConfigured) {
            return Result.failure(IllegalStateException("Legal acceptance requirements are not configured"))
        }
        val request: RecordLegalAcceptanceRequest = RecordLegalAcceptanceRequest(
            acceptedTermsVersion = snapshot.requiredTermsVersion,
            acknowledgedPrivacyVersion = snapshot.requiredPrivacyVersion,
            appVersion = configLogic.appVersion,
            platform = PLATFORM_ANDROID
        )
        return profileRepository.recordLegalAcceptance(request).map { updatedSnapshot ->
            if (!updatedSnapshot.isRequirementConfigured || !updatedSnapshot.isAccepted) {
                throw IllegalStateException("Backend did not confirm the required legal acceptance")
            }
            cacheSnapshot(updatedSnapshot)
            updatedSnapshot
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
        const val PLATFORM_ANDROID: String = "android"
    }
}
