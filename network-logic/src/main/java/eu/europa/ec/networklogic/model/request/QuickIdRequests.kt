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

package eu.europa.ec.networklogic.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to create a new QuickID verification session.
 */
@Serializable
data class CreateQuickIdSessionRequest(
    @SerialName("callback_url") val callbackUrl: String,
    @SerialName("error_url") val errorUrl: String? = null,
    @SerialName("reason") val reason: String? = null
)

/**
 * Request to create an AWS Liveness session for face verification.
 */
@Serializable
data class CreateLivenessSessionRequest(
    @SerialName("session_id") val sessionId: String
)

/**
 * Request to issue an Authbound ID credential after successful verification.
 */
@Serializable
data class IssueAuthboundIdRequest(
    @SerialName("quickid_session_id") val quickidSessionId: String,
    @SerialName("wallet_scheme") val walletScheme: String = "haip"
)
