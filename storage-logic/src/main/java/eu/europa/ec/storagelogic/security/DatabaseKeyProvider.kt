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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.europa.ec.businesslogic.controller.storage.EncryptedPreferenceDataStores
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase

class DatabaseKeyProvider(
    private val dataStore: DataStore<Preferences>,
    private val keyGenerator: () -> ByteArray = { generateDatabaseKey() }
) {
    constructor(context: Context) : this(
        EncryptedPreferenceDataStores.create(
            context = context,
            fileName = DATABASE_KEY_FILE
        )
    )

    suspend fun getOrCreateKey(): ByteArray {
        return KEY_MUTEX.withLock {
            val storedKey: ByteArray? = dataStore.data.first()[DATABASE_KEY]?.let(::decodeKey)
            if (storedKey != null) {
                return@withLock storedKey
            }
            val generatedKey: ByteArray = keyGenerator()
            require(generatedKey.size >= MIN_KEY_SIZE_BYTES) { "Database key is too short" }
            dataStore.edit { preferences ->
                preferences[DATABASE_KEY] = encodeKey(generatedKey)
            }
            generatedKey
        }
    }

    private fun encodeKey(value: ByteArray): String {
        return Base64.getEncoder().encodeToString(value)
    }

    private fun decodeKey(value: String): ByteArray {
        return Base64.getDecoder().decode(value)
    }

    private companion object {
        const val DATABASE_KEY_FILE = "authbound-wallet-db-key.preferences_pb"
        const val DATABASE_KEY_NAME = "database_key"
        const val MIN_KEY_SIZE_BYTES = 32
        val DATABASE_KEY: Preferences.Key<String> = stringPreferencesKey(DATABASE_KEY_NAME)
        val KEY_MUTEX: Mutex = Mutex()
    }
}

private fun generateDatabaseKey(): ByteArray {
    return ByteArray(32).also { bytes ->
        SecureRandom().nextBytes(bytes)
    }
}

internal object DatabaseEncryptionMigrator {
    private val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun migratePlaintextDatabaseIfNeeded(
        context: Context,
        databaseName: String,
        databaseKey: ByteArray
    ) {
        val databaseFile: File = context.getDatabasePath(databaseName)
        if (!isPlaintextSqlite(databaseFile)) return
        val encryptedFile = File(databaseFile.parentFile, "$databaseName.encrypted")
        val backupFile = File(databaseFile.parentFile, "$databaseName.plaintext-backup")
        deleteIfExists(encryptedFile)
        deleteSidecars(encryptedFile)
        exportEncryptedDatabase(
            plaintextFile = databaseFile,
            encryptedFile = encryptedFile,
            databaseKey = databaseKey
        )
        replaceDatabase(
            databaseFile = databaseFile,
            encryptedFile = encryptedFile,
            backupFile = backupFile
        )
    }

    internal fun isPlaintextSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { input ->
            if (input.read(header) != SQLITE_HEADER.size) return false
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    private fun exportEncryptedDatabase(
        plaintextFile: File,
        encryptedFile: File,
        databaseKey: ByteArray
    ) {
        SqlCipherDatabase.openDatabase(
            plaintextFile.absolutePath,
            ByteArray(0),
            null,
            SqlCipherDatabase.OPEN_READWRITE,
            null
        ).use { database ->
            database.rawExecSQL("PRAGMA wal_checkpoint(FULL)")
            database.rawExecSQL("ATTACH DATABASE ? AS encrypted KEY ?", encryptedFile.absolutePath, databaseKey)
            database.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            database.rawExecSQL("PRAGMA encrypted.user_version = ${database.version}")
            database.rawExecSQL("DETACH DATABASE encrypted")
        }
    }

    private fun replaceDatabase(
        databaseFile: File,
        encryptedFile: File,
        backupFile: File
    ) {
        deleteIfExists(backupFile)
        require(databaseFile.renameTo(backupFile)) { "Failed to prepare database encryption backup" }
        try {
            require(encryptedFile.renameTo(databaseFile)) { "Failed to install encrypted database" }
            deleteSidecars(databaseFile)
            deleteIfExists(backupFile)
        } catch (error: Throwable) {
            if (!databaseFile.exists()) {
                backupFile.renameTo(databaseFile)
            }
            throw error
        }
    }

    private fun deleteSidecars(databaseFile: File) {
        listOf("-journal", "-shm", "-wal").forEach { suffix ->
            deleteIfExists(File(databaseFile.absolutePath + suffix))
        }
    }

    private fun deleteIfExists(file: File) {
        require(!file.exists() || file.delete()) { "Failed to delete ${file.name}" }
    }
}
