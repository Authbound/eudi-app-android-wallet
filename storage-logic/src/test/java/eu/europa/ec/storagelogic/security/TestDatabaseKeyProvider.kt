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

package eu.europa.ec.storagelogic.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDatabaseKeyProvider {

    @Test
    fun `Given no stored key, When key is requested twice, Then generated key is persisted`() =
        runTest {
            var generationCount: Int = 0
            val expectedKey: ByteArray = ByteArray(32) { index -> index.toByte() }
            val provider = DatabaseKeyProvider(
                dataStore = FakePreferencesDataStore(),
                keyGenerator = {
                    generationCount += 1
                    expectedKey
                }
            )

            val firstKey: ByteArray = provider.getOrCreateKey()
            val secondKey: ByteArray = provider.getOrCreateKey()

            assertArrayEquals(expectedKey, firstKey)
            assertArrayEquals(expectedKey, secondKey)
            assertEquals(1, generationCount)
        }

    @Test
    fun `Given concurrent first requests, When key is requested, Then one generated key is shared`(): Unit =
        runTest {
            val requestCount: Int = 8
            val dataStore: BlockingFirstReadDataStore =
                BlockingFirstReadDataStore(expectedReaders = requestCount)
            val generationCount: AtomicInteger = AtomicInteger(0)
            val keyRequests: List<Deferred<ByteArray>> = (1..requestCount).map {
                async(Dispatchers.Default) {
                    val provider: DatabaseKeyProvider = DatabaseKeyProvider(
                        dataStore = dataStore,
                        keyGenerator = {
                            val generation: Int = generationCount.incrementAndGet()
                            ByteArray(32) { generation.toByte() }
                        }
                    )
                    provider.getOrCreateKey()
                }
            }
            dataStore.awaitFirstReads()
            dataStore.releaseFirstReads()
            val keys: List<ByteArray> = keyRequests.awaitAll()
            keys.forEach { key ->
                assertArrayEquals(keys.first(), key)
            }
            assertEquals(1, generationCount.get())
        }

    @Test
    fun `Given database files, When plaintext header is checked, Then only SQLite files are detected`() {
        val plaintextFile: File = File.createTempFile("plaintext", ".db")
        val encryptedFile: File = File.createTempFile("encrypted", ".db")
        try {
            plaintextFile.writeBytes("SQLite format 3\u0000content".toByteArray(Charsets.US_ASCII))
            encryptedFile.writeBytes(ByteArray(32) { index -> index.toByte() })
            assertTrue(DatabaseEncryptionMigrator.isPlaintextSqlite(plaintextFile))
            assertFalse(DatabaseEncryptionMigrator.isPlaintextSqlite(encryptedFile))
        } finally {
            plaintextFile.delete()
            encryptedFile.delete()
        }
    }
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

private class BlockingFirstReadDataStore(
    expectedReaders: Int
) : DataStore<Preferences> {
    private val readersReady: CountDownLatch = CountDownLatch(expectedReaders)
    private val releaseReaders: CountDownLatch = CountDownLatch(1)
    private var state: Preferences = emptyPreferences()

    override val data: Flow<Preferences> = flow {
        val snapshot: Preferences = state
        readersReady.countDown()
        releaseReaders.await(1, TimeUnit.SECONDS)
        emit(snapshot)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updatedValue: Preferences = transform(state)
        state = updatedValue
        return updatedValue
    }

    fun awaitFirstReads(): Unit {
        readersReady.await(200, TimeUnit.MILLISECONDS)
    }

    fun releaseFirstReads(): Unit {
        releaseReaders.countDown()
    }
}
