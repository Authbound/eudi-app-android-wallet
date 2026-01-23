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
package eu.europa.ec.dashboardfeature.ui.health

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import eu.europa.ec.dashboardfeature.interactor.HealthInteractor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MaisaCallbackActivity : ComponentActivity() {

    private val interactor: HealthInteractor by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri = intent?.data ?: run {
            finish()
            return
        }
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")
        if (!error.isNullOrBlank()) {
            val message = errorDescription?.replace('+', ' ')
                ?: "Maisa authorization failed"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            Toast.makeText(this, "Missing authorization code", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            interactor.handleMaisaCallback(
                code = code,
                state = state,
                redirectUri = "authbound://maisa/callback"
            ).fold(
                onSuccess = { offerUri ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(offerUri)))
                    Toast.makeText(
                        this@MaisaCallbackActivity,
                        "Credential offer opened",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = {
                    Toast.makeText(
                        this@MaisaCallbackActivity,
                        it.localizedMessage ?: "Maisa import failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            finish()
        }
    }
}
