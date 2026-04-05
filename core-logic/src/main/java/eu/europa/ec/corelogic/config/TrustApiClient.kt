package eu.europa.ec.corelogic.config

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Minimal Trust API client for fetching dynamic issuer configuration
 * and trust anchor certificates.
 * Uses the shared Ktor HttpClient from the network-logic module.
 */
internal class TrustApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    companion object {
        private const val TAG = "TrustApiClient"
        private const val REQUEST_TIMEOUT_MS = 3_000L
    }

    /**
     * Fetch issuer URLs from the Trust API.
     * Returns a list of issuer URLs, or null on failure or timeout.
     */
    suspend fun fetchIssuerUrls(): List<String>? = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
        try {
            val response = httpClient.get("$baseUrl/entities") {
                parameter("type", "issuer")
            }

            if (!response.status.isSuccess()) {
                Log.w(TAG, "Trust API returned ${response.status} for entities")
                return@withTimeoutOrNull null
            }

            val json = JSONObject(response.bodyAsText())
            val data = json.getJSONArray("data")

            val urls = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val entity = data.getJSONObject(i)
                val issuerUrl = entity.optString("issuerUrl", "")
                if (issuerUrl.isNotBlank()) {
                    urls.add(issuerUrl)
                }
            }

            Log.i(TAG, "Fetched ${urls.size} issuers from Trust API")
            urls
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch issuers from Trust API: ${e.message}")
            null
        }
    }

    /**
     * Fetch trusted CA certificates (PEM-encoded) from the Trust API.
     * These are used to populate the wallet's reader trust store.
     * Returns a list of PEM strings, or null on failure or timeout.
     */
    suspend fun fetchTrustedCertificates(): List<String>? = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
        try {
            val response = httpClient.get("$baseUrl/certificates")

            if (!response.status.isSuccess()) {
                Log.w(TAG, "Trust API returned ${response.status} for certificates")
                return@withTimeoutOrNull null
            }

            val json = JSONObject(response.bodyAsText())
            val data = json.getJSONArray("data")

            val certs = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val pem = data.getString(i)
                if (pem.contains("BEGIN CERTIFICATE")) {
                    certs.add(pem)
                }
            }

            Log.i(TAG, "Fetched ${certs.size} trusted certificates from Trust API")
            certs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch certificates from Trust API: ${e.message}")
            null
        }
    }
}
