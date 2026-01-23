/*
 * Copyright (c) 2023 European Commission
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

package eu.europa.ec.networklogic.model

/**
 * Response wrapper providing API compatibility with Retrofit's Response<T> pattern.
 *
 * This sealed class encapsulates HTTP responses, making it easy for consumers to handle
 * success and error cases uniformly while maintaining the same API surface as Retrofit.
 */
sealed class ApiResponse<out T> {
    /**
     * Successful HTTP response with body.
     */
    data class Success<T>(
        val body: T,
        val code: Int = 200
    ) : ApiResponse<T>()

    /**
     * HTTP error response (4xx, 5xx) or network failure.
     */
    data class Error(
        val code: Int,
        val message: String,
        val errorBody: String? = null
    ) : ApiResponse<Nothing>()

    /**
     * Returns true if this response is successful (2xx status code).
     */
    val isSuccessful: Boolean
        get() = this is Success

    /**
     * Returns the response body if successful, null otherwise.
     * Matches Retrofit's Response.body() behavior.
     */
    fun body(): T? = when (this) {
        is Success -> body
        is Error -> null
    }

    /**
     * Returns the HTTP status code.
     * For network errors, returns 0.
     */
    fun code(): Int = when (this) {
        is Success -> code
        is Error -> code
    }

    /**
     * Returns a human-readable message describing the response status.
     */
    fun message(): String = when (this) {
        is Success -> "OK"
        is Error -> message
    }

    /**
     * Returns the error body if this is an error response, null otherwise.
     */
    fun errorBody(): String? = when (this) {
        is Success -> null
        is Error -> errorBody
    }
}
