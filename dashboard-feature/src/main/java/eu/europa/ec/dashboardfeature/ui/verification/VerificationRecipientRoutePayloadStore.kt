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

package eu.europa.ec.dashboardfeature.ui.verification

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val ROUTE_PAYLOAD_TTL_MILLIS = 30 * 60 * 1000L
private const val MAX_ROUTE_PAYLOADS = 16

data class VerificationRecipientRoutePayload(
    val sessionId: String,
    val accessToken: String,
    val verificationUrl: String,
)

object VerificationRecipientRoutePayloadStore {

    private data class Entry(
        val payload: VerificationRecipientRoutePayload,
        val createdAtMillis: Long,
    )

    private val payloads = ConcurrentHashMap<String, Entry>()

    @Synchronized
    fun put(payload: VerificationRecipientRoutePayload): String {
        val now = System.currentTimeMillis()
        prune(now)
        if (payloads.size >= MAX_ROUTE_PAYLOADS) {
            payloads.entries.minByOrNull { it.value.createdAtMillis }?.key?.let(payloads::remove)
        }
        val key = UUID.randomUUID().toString()
        payloads[key] = Entry(payload = payload, createdAtMillis = now)
        return key
    }

    @Synchronized
    fun get(payloadKey: String?): VerificationRecipientRoutePayload? {
        val key = payloadKey?.takeIf(String::isNotBlank) ?: return null
        val now = System.currentTimeMillis()
        val entry = payloads[key] ?: return null
        if (now - entry.createdAtMillis > ROUTE_PAYLOAD_TTL_MILLIS) {
            payloads.remove(key)
            return null
        }
        return entry.payload
    }

    @Synchronized
    fun remove(payloadKey: String?) {
        payloadKey?.takeIf(String::isNotBlank)?.let(payloads::remove)
    }

    @Synchronized
    fun clear() {
        payloads.clear()
    }

    private fun prune(now: Long) {
        payloads.entries.removeIf { now - it.value.createdAtMillis > ROUTE_PAYLOAD_TTL_MILLIS }
    }
}
