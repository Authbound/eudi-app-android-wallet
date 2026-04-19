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

package eu.europa.ec.networklogic.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WalletSecurityIncidentRequest(
    @SerialName("eventType")
    val eventType: String,
    @SerialName("wuaId")
    val wuaId: String? = null,
    @SerialName("clientStateVersion")
    val clientStateVersion: Int = 2,
    @SerialName("details")
    val details: JsonObject? = null,
    @SerialName("detectedAt")
    val detectedAt: String? = null
)

@Serializable
data class WalletRecoveryPrepareRequest(
    @SerialName("reason")
    val reason: String = "security_incident",
    @SerialName("evidence")
    val evidence: List<String>,
    @SerialName("clientDetectedAt")
    val clientDetectedAt: String? = null
)
