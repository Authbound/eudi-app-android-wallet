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

package eu.europa.ec.corelogic.util

import android.content.Intent
import android.util.Log
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import java.net.URI

class EudiWalletListenerWrapper(
    private val onConnected: () -> Unit,
    private val onConnecting: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onError: (String) -> Unit,
    private val onQrEngagementReady: (String) -> Unit,
    private val onRequestReceived: (RequestProcessor.ProcessedRequest) -> Unit,
    private val onResponseSent: () -> Unit,
    private val onRedirect: (URI) -> Unit,
    private val intentToSend: (Intent) -> Unit
) : TransferEvent.Listener {

    companion object {
        private const val TAG = "EudiWalletListener"
    }

    override fun onTransferEvent(event: TransferEvent) {
        Log.d(TAG, "TransferEvent received: ${event::class.simpleName}")

        when (event) {
            is TransferEvent.Connected -> {
                Log.d(TAG, "Connected to verifier")
                onConnected()
            }
            is TransferEvent.Connecting -> {
                Log.d(TAG, "Connecting to verifier...")
                onConnecting()
            }
            is TransferEvent.Disconnected -> {
                Log.d(TAG, "Disconnected from verifier")
                onDisconnected()
            }
            is TransferEvent.Error -> {
                logTransferError(event.error)
                onError(event.error.message ?: "Unknown error")
            }
            is TransferEvent.QrEngagementReady -> {
                Log.d(TAG, "QR engagement ready, content length: ${event.qrCode.content.length}")
                onQrEngagementReady(event.qrCode.content)
            }
            is TransferEvent.RequestReceived -> {
                logRequestReceived(event.processedRequest)
                onRequestReceived(event.processedRequest)
            }
            is TransferEvent.ResponseSent -> {
                Log.d(TAG, "Response sent successfully")
                onResponseSent()
            }
            is TransferEvent.Redirect -> {
                Log.d(TAG, "Redirect received")
                onRedirect(event.redirectUri)
            }
            is TransferEvent.IntentToSend -> {
                Log.d(TAG, "Intent to send")
                intentToSend(event.intent)
            }
        }
    }

    private fun logTransferError(error: Throwable) {
        Log.e(TAG, "Error class: ${error::class.qualifiedName}")
    }

    private fun logRequestReceived(processedRequest: RequestProcessor.ProcessedRequest) {
        when (processedRequest) {
            is RequestProcessor.ProcessedRequest.Success -> {
                Log.d(TAG, "Request processed successfully")
                Log.d(TAG, "Number of requested documents: ${processedRequest.requestedDocuments.size}")
            }
            is RequestProcessor.ProcessedRequest.Failure -> {
                Log.e(TAG, "REQUEST PROCESSING FAILED")
                Log.e(TAG, "Failure class: ${processedRequest::class.qualifiedName}")
            }
        }
    }
}
