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

package eu.europa.ec.uilogic.component.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles NFC tag discovery and provides a flow of discovered tags.
 *
 * This class manages NFC foreground dispatch, enabling the app to receive
 * NFC tag events when specific screens are active. Screens that need NFC
 * should call [enableForegroundDispatch] in onResume and [disableForegroundDispatch]
 * in onPause.
 */
@Single
class NfcTagHandler {

    private val _tagFlow = MutableSharedFlow<Tag>(extraBufferCapacity = 1)

    /**
     * Flow of discovered NFC tags.
     * Screens interested in NFC should collect this flow.
     */
    val tagFlow: SharedFlow<Tag> = _tagFlow.asSharedFlow()

    /**
     * Tracks whether NFC foreground dispatch is currently requested by a screen.
     * The Activity checks this flag in onResume to decide whether to enable dispatch.
     * Uses AtomicBoolean for thread safety.
     */
    private val _isDispatchRequested = AtomicBoolean(false)
    val isDispatchRequested: Boolean
        get() = _isDispatchRequested.get()

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private var techLists: Array<Array<String>>? = null

    /**
     * Initializes the NFC handler with the given activity.
     * Should be called once in Activity.onCreate.
     */
    fun initialize(activity: Activity) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)

        // Create PendingIntent for NFC discovery
        val intent = Intent(activity, activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Filter for IsoDep tags (passport chips use ISO-DEP)
        val isoDepFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        intentFilters = arrayOf(isoDepFilter)
        techLists = arrayOf(arrayOf(IsoDep::class.java.name))
    }

    /**
     * Requests NFC foreground dispatch.
     * Called by screens that need NFC when they become visible.
     * The Activity will enable dispatch in its onResume.
     */
    fun requestDispatch(activity: Activity) {
        _isDispatchRequested.set(true)
        enableForegroundDispatch(activity)
    }

    /**
     * Releases the NFC foreground dispatch request.
     * Called by screens when they are no longer visible.
     */
    fun releaseDispatch(activity: Activity) {
        _isDispatchRequested.set(false)
        disableForegroundDispatch(activity)
    }

    /**
     * Enables NFC foreground dispatch.
     * Call this in Activity.onResume when dispatch is requested.
     */
    fun enableForegroundDispatch(activity: Activity) {
        if (isDispatchRequested) {
            nfcAdapter?.enableForegroundDispatch(
                activity,
                pendingIntent,
                intentFilters,
                techLists
            )
        }
    }

    /**
     * Disables NFC foreground dispatch.
     * Call this in Activity.onPause.
     */
    fun disableForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Handles an NFC intent.
     * Call this from Activity.onNewIntent when an NFC intent is received.
     *
     * @return true if the intent was an NFC tag intent and was handled
     */
    fun handleIntent(intent: Intent): Boolean {
        val action = intent.action
        if (action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                _tagFlow.tryEmit(tag)
                return true
            }
        }
        return false
    }

    /**
     * Checks if NFC is available on the device.
     */
    fun isNfcAvailable(): Boolean = nfcAdapter != null

    /**
     * Checks if NFC is enabled on the device.
     */
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true
}
