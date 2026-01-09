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

package eu.europa.ec.commonfeature.model

/**
 * Enum representing the different PIN flows in the application.
 *
 * - CREATE: First-time PIN setup (enter → re-enter for confirmation)
 * - UPDATE: Change existing PIN (validate current → enter new → re-enter new)
 * - VERIFY: Verify existing PIN during startup (single entry, no re-enter)
 */
enum class PinFlow {
    CREATE,
    UPDATE,
    VERIFY
}