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
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object EncryptedPreferenceDataStores {
    private const val KEYSET_PREFS_PREFIX = "authbound-datastore-keyset-"
    private const val KEYSET_KEY_PREFIX = "keyset-"
    private const val MASTER_KEY_URI = "android-keystore://authbound-datastore-master-key"

    fun create(context: Context, fileName: String): DataStore<Preferences> {
        val appContext: Context = context.applicationContext
        return DataStoreFactory.create(
            serializer = AeadSerializer(
                aead = provideAead(appContext, fileName),
                wrappedSerializer = PreferencesFileSerializer,
                associatedData = fileName.toByteArray(Charsets.UTF_8)
            ),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { appContext.preferencesDataStoreFile(fileName) }
        )
    }

    private fun provideAead(context: Context, fileName: String): Aead {
        AeadConfig.register()
        val suffix: String = hashValue(fileName)
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_KEY_PREFIX + suffix, KEYSET_PREFS_PREFIX + suffix)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(
            RegistryConfiguration.get(),
            Aead::class.java
        )
    }

    fun hashValue(value: String): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
