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

package eu.europa.ec.businesslogic.controller.storage

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class TestPrefsControllerV2 {

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var supabaseClient: SupabaseClient

    private lateinit var closeable: AutoCloseable
    private lateinit var context: Context

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
        whenever(resourceProvider.provideContext()).thenReturn(context)
    }

    @After
    fun after() {
        closeable.close()
    }

    @Test
    fun `Given authenticated user, When string is stored, Then legacy shared preferences stay empty`() =
        runTest {
            val dataStores: MutableMap<String, FakePreferencesDataStore> = mutableMapOf()
            val controller: PrefsControllerV2 = createController(
                userId = "user-a",
                dataStores = dataStores
            )

            controller.setString("secret", "plain-value")

            val legacyValue: String? = legacyPrefs("user-a").getString("secret", null)
            assertNull(legacyValue)
            assertEquals("plain-value", controller.getString("secret", ""))
        }

    @Test
    fun `Given legacy user preferences, When value is read, Then value migrates and legacy entry is cleared`() =
        runTest {
            val dataStores: MutableMap<String, FakePreferencesDataStore> = mutableMapOf()
            legacyPrefs("user-a").edit { putString("legacy_secret", "legacy-value") }
            val controller: PrefsControllerV2 = createController(
                userId = "user-a",
                dataStores = dataStores
            )

            val actualValue: String = controller.getString("legacy_secret", "")

            assertEquals("legacy-value", actualValue)
            assertFalse(legacyPrefs("user-a").contains("legacy_secret"))
        }

    private fun createController(
        userId: String,
        dataStores: MutableMap<String, FakePreferencesDataStore>
    ): PrefsControllerV2 {
        return PrefsControllerV2Impl(
            resourceProvider = resourceProvider,
            logController = logController,
            supabaseClient = supabaseClient,
            currentUserIdProvider = { userId },
            currentUserIdSyncProvider = { userId },
            dataStoreProvider = { fileName ->
                dataStores.getOrPut(fileName) { FakePreferencesDataStore() }
            }
        )
    }

    private fun legacyPrefs(userId: String) =
        context.getSharedPreferences("authbound-wallet-user-$userId", Context.MODE_PRIVATE)
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state: MutableStateFlow<Preferences> = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updatedValue: Preferences = transform(state.value)
        state.value = updatedValue
        return updatedValue
    }
}
