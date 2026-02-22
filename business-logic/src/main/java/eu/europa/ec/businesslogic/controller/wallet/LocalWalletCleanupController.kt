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
package eu.europa.ec.businesslogic.controller.wallet

/**
 * Responsible for cleaning up local wallet data (EUDI SDK documents, Room database records).
 * Interface in business-logic for dependency inversion; implementation in core-logic.
 */
interface LocalWalletCleanupController {
    /**
     * Performs best-effort cleanup of all local wallet data:
     * - Deletes all EUDI SDK documents (PID, mDL, etc.)
     * - Clears bookmarks, transaction logs, and revoked document records
     *
     * @return list of step descriptions that failed (empty = full success)
     */
    suspend fun cleanupLocalWalletData(): List<String>
}
