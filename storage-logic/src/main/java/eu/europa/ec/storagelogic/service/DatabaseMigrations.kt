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

package eu.europa.ec.storagelogic.service

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create user_document_mappings table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_document_mappings` (
                    `documentId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`documentId`, `userId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_user_document_mappings_userId` ON `user_document_mappings` (`userId`)"
            )

            // 2. Migrate bookmarks: add userId column, recreate with composite PK
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bookmarks_new` (
                    `identifier` TEXT NOT NULL,
                    `userId` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`identifier`, `userId`)
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `bookmarks_new` (`identifier`, `userId`) SELECT `identifier`, '' FROM `bookmarks`")
            db.execSQL("DROP TABLE `bookmarks`")
            db.execSQL("ALTER TABLE `bookmarks_new` RENAME TO `bookmarks`")

            // 3. Migrate revokedDocuments: add userId column, recreate with composite PK
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `revokedDocuments_new` (
                    `identifier` TEXT NOT NULL,
                    `userId` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`identifier`, `userId`)
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `revokedDocuments_new` (`identifier`, `userId`) SELECT `identifier`, '' FROM `revokedDocuments`")
            db.execSQL("DROP TABLE `revokedDocuments`")
            db.execSQL("ALTER TABLE `revokedDocuments_new` RENAME TO `revokedDocuments`")

            // 4. Migrate transactionLogs: add userId column, recreate with composite PK
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transactionLogs_new` (
                    `identifier` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `userId` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`identifier`, `userId`)
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO `transactionLogs_new` (`identifier`, `value`, `userId`) SELECT `identifier`, `value`, '' FROM `transactionLogs`")
            db.execSQL("DROP TABLE `transactionLogs`")
            db.execSQL("ALTER TABLE `transactionLogs_new` RENAME TO `transactionLogs`")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `failedReIssuedDocuments` (
                    `identifier` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    PRIMARY KEY(`identifier`, `userId`)
                )
                """.trimIndent()
            )
        }
    }
}
