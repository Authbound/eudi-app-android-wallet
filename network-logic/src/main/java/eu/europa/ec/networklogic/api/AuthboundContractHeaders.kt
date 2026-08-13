package eu.europa.ec.networklogic.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

const val AUTHBOUND_API_VERSION_HEADER = "Authbound-Api-Version"
const val AUTHBOUND_CONTRACT_REVISION_HEADER = "Authbound-Contract-Revision"
const val AUTHBOUND_PUBLIC_API_VERSION = "v1"
const val AUTHBOUND_PUBLIC_CONTRACT_REVISION = "v1.2026-07-06.1"
const val AUTHBOUND_MOBILE_API_VERSION = "v1"
const val AUTHBOUND_MOBILE_CONTRACT_REVISION = "v1.2026-07-10.1"

fun HttpRequestBuilder.authboundMobileContractHeaders() {
    header(AUTHBOUND_API_VERSION_HEADER, AUTHBOUND_MOBILE_API_VERSION)
    header(AUTHBOUND_CONTRACT_REVISION_HEADER, AUTHBOUND_MOBILE_CONTRACT_REVISION)
}

fun HttpRequestBuilder.authboundPublicContractHeaders() {
    header(AUTHBOUND_API_VERSION_HEADER, AUTHBOUND_PUBLIC_API_VERSION)
    header(AUTHBOUND_CONTRACT_REVISION_HEADER, AUTHBOUND_PUBLIC_CONTRACT_REVISION)
}
