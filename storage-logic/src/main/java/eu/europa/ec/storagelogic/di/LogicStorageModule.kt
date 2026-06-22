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

package eu.europa.ec.storagelogic.di

import android.content.Context
import androidx.room.Room
import eu.europa.ec.storagelogic.security.DatabaseEncryptionMigrator
import eu.europa.ec.storagelogic.security.DatabaseKeyProvider
import eu.europa.ec.storagelogic.dao.BookmarkDao
import eu.europa.ec.storagelogic.dao.FailedReIssuedDocumentDao
import eu.europa.ec.storagelogic.dao.RevokedDocumentDao
import eu.europa.ec.storagelogic.dao.TransactionLogDao
import eu.europa.ec.storagelogic.dao.UserDocumentMappingDao
import eu.europa.ec.storagelogic.service.DatabaseMigrations
import eu.europa.ec.storagelogic.service.DatabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("eu.europa.ec.storagelogic")
class LogicStorageModule

private const val DATABASE_NAME = "eudi.app.wallet.storage"

@Single
fun provideAppDatabase(context: Context): DatabaseService {
    System.loadLibrary("sqlcipher")
    val databaseKey: ByteArray = runBlocking(Dispatchers.IO) {
        DatabaseKeyProvider(context).getOrCreateKey()
    }
    DatabaseEncryptionMigrator.migratePlaintextDatabaseIfNeeded(
        context = context,
        databaseName = DATABASE_NAME,
        databaseKey = databaseKey
    )
    return Room.databaseBuilder(
        context,
        DatabaseService::class.java,
        DATABASE_NAME
    )
        .openHelperFactory(SupportOpenHelperFactory(databaseKey))
        .addMigrations(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3)
        .build()
}

@Single
fun provideBookmarkDao(service: DatabaseService): BookmarkDao = service.bookmarkDao()

@Single
fun provideRevokedDocumentDao(service: DatabaseService): RevokedDocumentDao =
    service.revokedDocumentDao()

@Single
fun provideTransactionLogDao(service: DatabaseService): TransactionLogDao =
    service.transactionLogDao()

@Single
fun provideUserDocumentMappingDao(service: DatabaseService): UserDocumentMappingDao =
    service.userDocumentMappingDao()

@Single
fun provideFailedReIssuedDocumentDao(service: DatabaseService): FailedReIssuedDocumentDao =
    service.failedReIssuedDocumentDao()
