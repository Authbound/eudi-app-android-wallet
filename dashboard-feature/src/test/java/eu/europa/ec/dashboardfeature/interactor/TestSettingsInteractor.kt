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

package eu.europa.ec.dashboardfeature.interactor

import android.net.Uri
import eu.europa.ec.authenticationlogic.controller.storage.BiometryStorageController
import eu.europa.ec.authenticationlogic.usecase.GetCachedLegalAcceptanceUseCase
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.IsUserAuthenticatedUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.businesslogic.config.AppBuildType
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsSection
import eu.europa.ec.dashboardfeature.ui.verification.VerificationRecipientRoutePayload
import eu.europa.ec.dashboardfeature.ui.verification.VerificationRecipientRoutePayloadStore
import eu.europa.ec.dashboardfeature.util.mockedChangeLogUrl
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.util.StringResourceProviderMocker.mockResourceProviderStrings
import eu.europa.ec.testfeature.util.mockedUriPath1
import eu.europa.ec.testfeature.util.mockedUriPath2
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TestSettingsInteractor {

    @Mock
    private lateinit var configLogic: ConfigLogic

    @Mock
    private lateinit var logController: LogController

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var prefKeys: PrefKeys

    @Mock
    private lateinit var prefsController: PrefsControllerV2

    @Mock
    private lateinit var biometryStorageController: BiometryStorageController

    @Mock
    private lateinit var getCachedLegalAcceptanceUseCase: GetCachedLegalAcceptanceUseCase

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    @Mock
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase

    @Mock
    private lateinit var signOutUseCase: SignOutUseCase

    @Mock
    private lateinit var isUserAuthenticatedUseCase: IsUserAuthenticatedUseCase

    @Mock
    private lateinit var getMyProfileUseCase: GetMyProfileUseCase

    private lateinit var interactor: SettingsInteractor

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)

        interactor = SettingsInteractorImpl(
            configLogic = configLogic,
            logController = logController,
            resourceProvider = resourceProvider,
            prefKeys = prefKeys,
            prefsController = prefsController,
            biometryStorageController = biometryStorageController,
            getCachedLegalAcceptanceUseCase = getCachedLegalAcceptanceUseCase,
            walletCoreDocumentsController = walletCoreDocumentsController,
            getCurrentUserUseCase = getCurrentUserUseCase,
            signOutUseCase = signOutUseCase,
            isUserAuthenticatedUseCase = isUserAuthenticatedUseCase,
            getMyProfileUseCase = getMyProfileUseCase,
        )
    }

    @After
    fun after() {
        VerificationRecipientRoutePayloadStore.clear()
        closeable.close()
    }

    //region getAppVersion
    @Test
    fun `Given an App Version, When getAppVersion is called, Then it returns the Apps Version`() {
        // Given
        val expectedAppVersion = "2024.01.1"
        whenever(configLogic.appVersion)
            .thenReturn(expectedAppVersion)

        // When
        val actualAppVersion = interactor.getAppVersion()

        // Then
        assertEquals(expectedAppVersion, actualAppVersion)
        verify(configLogic, times(1))
            .appVersion
    }
    //endregion

    @Test
    fun `Given verification recipient payloads are cached, When logout, Then payloads are cleared`() =
        runTest {
            val payloadKey = VerificationRecipientRoutePayloadStore.put(
                VerificationRecipientRoutePayload(
                    sessionId = "550e8400-e29b-41d4-a716-446655440000",
                    accessToken = "recipient-token",
                    verificationUrl = "https://app.authbound.io/verify/550e8400-e29b-41d4-a716-446655440000#token=recipient-token"
                )
            )

            interactor.logout()

            assertNull(VerificationRecipientRoutePayloadStore.get(payloadKey))
            verify(signOutUseCase, times(1)).invoke(SignOutMode.Soft)
        }

    //region getChangelogUrl
    @Test
    fun `Given a Changelog URL in configLogic, When getChangelogUrl is called, Then it returns the Changelog URL`() {
        // Given
        val expectedUrl = mockedChangeLogUrl
        whenever(configLogic.changelogUrl)
            .thenReturn(expectedUrl)

        // When
        val actualUrl = interactor.getChangelogUrl()

        // Then
        assertEquals(expectedUrl, actualUrl)
        verify(configLogic, times(1))
            .changelogUrl
    }

    @Test
    fun `Given no Changelog URL in configLogic, When getChangelogUrl is called, Then it returns null`() {
        // Given
        val expectedUrl = null
        whenever(configLogic.changelogUrl)
            .thenReturn(expectedUrl)

        // When
        val actualUrl = interactor.getChangelogUrl()

        // Then
        assertEquals(expectedUrl, actualUrl)
        verify(configLogic, times(1))
            .changelogUrl
    }
    //endregion

    //region retrieveLogFileUris
    @Test
    fun `Given a list of logs via logController, When retrieveLogFileUris is called, Then expected logs are returned`() {
        // Given
        val mockedArrayList = arrayListOf(
            Uri.parse(mockedUriPath1),
            Uri.parse(mockedUriPath2)
        )
        whenever(logController.retrieveLogFileUris()).thenReturn(mockedArrayList)

        // When
        val expectedLogFileUris = interactor.retrieveLogFileUris()

        // Then
        assertEquals(mockedArrayList, expectedLogFileUris)
    }
    //endregion

    //region getSettingsItemsUi
    @Test
    fun `Given release build, When getSettingsItemsUi is called, Then expected entries are returned`() {
        whenever(configLogic.appBuildType).thenReturn(AppBuildType.RELEASE)
        mockStringsNeededForGetSettingsItemsUi(resourcesProvider = resourceProvider)
        val settingsItems = interactor.getSettingsItemsUi(
            changelogUrl = mockedChangeLogUrl,
            isBiometricsEnabled = false
        )
        assertEquals(5, settingsItems.size)

        val accountDetailsItem = settingsItems[0]
        assertEquals(SettingsMenuItemType.ACCOUNT_DETAILS, accountDetailsItem.type)
        assertEquals(SettingsSection.ACCOUNT, accountDetailsItem.section)
        assertEquals(accountDetailsIdString, accountDetailsItem.data.itemId)
        val accountDetailsMain =
            accountDetailsItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(accountDetailsText, accountDetailsMain.text)
        val accountDetailsLeading =
            accountDetailsItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.UserIcon, accountDetailsLeading.iconData)
        val accountDetailsTrailing =
            accountDetailsItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, accountDetailsTrailing.iconData)

        val changePinItem = settingsItems[1]
        assertEquals(SettingsMenuItemType.CHANGE_PIN, changePinItem.type)
        assertEquals(SettingsSection.SECURITY, changePinItem.section)
        assertEquals(changePinIdString, changePinItem.data.itemId)
        val changePinMain = changePinItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(changePinText, changePinMain.text)
        val changePinLeading =
            changePinItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.ChangePin, changePinLeading.iconData)
        val changePinTrailing =
            changePinItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, changePinTrailing.iconData)

        val biometricItem = settingsItems[2]
        assertEquals(SettingsMenuItemType.BIOMETRIC_AUTHENTICATION, biometricItem.type)
        assertEquals(SettingsSection.SECURITY, biometricItem.section)
        assertEquals(biometricAuthenticationIdString, biometricItem.data.itemId)
        val biometricMain = biometricItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(biometricAuthenticationText, biometricMain.text)
        assertEquals(null, biometricItem.data.supportingText)
        val biometricLeading =
            biometricItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.TouchId, biometricLeading.iconData)
        val biometricTrailing =
            biometricItem.data.trailingContentData as ListItemTrailingContentDataUi.Switch
        assertEquals(false, biometricTrailing.switchData.isChecked)

        val privacyDataItem = settingsItems[3]
        assertEquals(SettingsMenuItemType.PRIVACY_AND_DATA, privacyDataItem.type)
        assertEquals(SettingsSection.SUPPORT, privacyDataItem.section)
        assertEquals(privacyDataIdString, privacyDataItem.data.itemId)
        val privacyDataMain =
            privacyDataItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(privacyDataText, privacyDataMain.text)
        val privacyDataLeading =
            privacyDataItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.Info, privacyDataLeading.iconData)
        val privacyDataTrailing =
            privacyDataItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, privacyDataTrailing.iconData)

        val retrieveLogsItem = settingsItems[4]
        assertEquals(SettingsMenuItemType.RETRIEVE_LOGS, retrieveLogsItem.type)
        assertEquals(SettingsSection.SUPPORT, retrieveLogsItem.section)
        assertEquals(retrieveLogsIdString, retrieveLogsItem.data.itemId)
        val retrieveLogsMain =
            retrieveLogsItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(retrieveLogsText, retrieveLogsMain.text)
        val retrieveLogsLeading =
            retrieveLogsItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.OpenNew, retrieveLogsLeading.iconData)
        val retrieveLogsTrailing =
            retrieveLogsItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, retrieveLogsTrailing.iconData)
    }

    @Test
    fun `Given debug build, When getSettingsItemsUi is called, Then privacy item is still included and no extra debug item is added`() {
        whenever(configLogic.appBuildType).thenReturn(AppBuildType.DEBUG)
        mockStringsNeededForGetSettingsItemsUi(resourcesProvider = resourceProvider)
        val settingsItems = interactor.getSettingsItemsUi(
            changelogUrl = null,
            isBiometricsEnabled = true
        )
        assertEquals(5, settingsItems.size)
        val biometricItem = settingsItems[2]
        val biometricTrailing =
            biometricItem.data.trailingContentData as ListItemTrailingContentDataUi.Switch
        assertEquals(true, biometricTrailing.switchData.isChecked)
        val privacyDataItem = settingsItems[3]
        assertEquals(SettingsMenuItemType.PRIVACY_AND_DATA, privacyDataItem.type)
        assertEquals(privacyDataIdString, privacyDataItem.data.itemId)
    }

    @Test
    fun `Given any build, When getSettingsItemsUi is called, Then sections appear in ACCOUNT, SECURITY, SUPPORT order`() {
        whenever(configLogic.appBuildType).thenReturn(AppBuildType.RELEASE)
        mockStringsNeededForGetSettingsItemsUi(resourcesProvider = resourceProvider)
        val settingsItems = interactor.getSettingsItemsUi(
            changelogUrl = mockedChangeLogUrl,
            isBiometricsEnabled = false
        )

        // The screen groups items by section in enum order; items must already
        // be sorted so each section forms one contiguous block.
        val sectionOrdinals = settingsItems.map { it.section.ordinal }
        assertEquals(sectionOrdinals.sorted(), sectionOrdinals)
        assertTrue(settingsItems.any { it.section == SettingsSection.ACCOUNT })
        assertTrue(settingsItems.any { it.section == SettingsSection.SECURITY })
        assertTrue(settingsItems.any { it.section == SettingsSection.SUPPORT })
    }
    //endregion

    //region Mock Calls
    private fun mockStringsNeededForGetSettingsItemsUi(resourcesProvider: ResourceProvider) {
        mockResourceProviderStrings(
            resourcesProvider,
            listOf(
                R.string.dashboard_side_menu_option_change_pin_id to changePinIdString,
                R.string.dashboard_side_menu_option_change_pin to changePinText,
                R.string.settings_biometric_authentication to biometricAuthenticationText,
                R.string.settings_privacy to privacyDataText,
                R.string.settings_screen_option_retrieve_logs_id to retrieveLogsIdString,
                R.string.settings_screen_option_retrieve_logs to retrieveLogsText,
            )
        )
    }
    //endregion

    //region Mocked objects needed for tests.
    private val accountDetailsIdString = "account_details"
    private val accountDetailsText = "Account details"
    private val changePinIdString = "changePinId"
    private val changePinText = "Change PIN"
    private val biometricAuthenticationIdString = "biometric_authentication"
    private val biometricAuthenticationText = "Biometrics"
    private val privacyDataIdString = "privacy_and_data"
    private val privacyDataText = "Privacy & Data"
    private val retrieveLogsIdString = "retrieveLogsId"
    private val retrieveLogsText = "Retrieve logs"
    //endregion
}
