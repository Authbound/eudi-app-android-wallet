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

package eu.europa.ec.uilogic.extension

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import eu.europa.ec.uilogic.container.EudiComponentActivity

fun Context.openDeepLink(deepLink: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = deepLink
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

fun Context.getPendingDeepLink(): Uri? {
    return (this as? EudiComponentActivity)?.pendingDeepLink?.let { deepLink ->
        clearPendingDeepLink()
        deepLink
    }
}

fun Context.peekPendingDeepLink(): Uri? {
    return (this as? EudiComponentActivity)?.pendingDeepLink
}

fun Context.cacheDeepLink(uri: Uri) {
    val intent = Intent().apply {
        data = uri
    }
    (this as? EudiComponentActivity)?.cacheDeepLink(intent)
}

fun Context.finish() {
    (this as? EudiComponentActivity)?.finish()
}

fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("No Activity found.")
}

private fun Context.clearPendingDeepLink() {
    (this as? EudiComponentActivity)?.pendingDeepLink = null
}

/**
 * Parses a string url and sends the Action View Intent.
 *
 * @param uri the url to parse.
 */
fun Context.openUrl(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
    }
}

internal fun createBrowserOnlyIntent(
    uri: Uri,
    browserPackages: List<String>,
    chooserTitle: String? = null
): Intent? {
    val packages = browserPackages
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (packages.isEmpty()) return null

    val browserIntents = packages.map { packageName ->
        Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            `package` = packageName
        }
    }

    if (browserIntents.size == 1) {
        return browserIntents.first()
    }

    return Intent.createChooser(browserIntents.first(), chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Intent.EXTRA_INITIAL_INTENTS, browserIntents.drop(1).toTypedArray())
    }
}

fun Context.openUrlInBrowser(uri: Uri, chooserTitle: String? = null) {
    try {
        val browserPackages = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            },
            0
        )
            .mapNotNull { it.activityInfo?.packageName }
            .filterNot { it == packageName }

        createBrowserOnlyIntent(uri, browserPackages, chooserTitle)?.let(::startActivity)
    } catch (_: Exception) {
    }
}

fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri = Uri.fromParts("package", packageName, null)
    intent.data = uri
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.openBleSettings() {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.openIntentChooser(intent: Intent, title: String? = null) {
    try {
        startActivity(Intent.createChooser(intent, title))
    } catch (_: Exception) {
    }
}
