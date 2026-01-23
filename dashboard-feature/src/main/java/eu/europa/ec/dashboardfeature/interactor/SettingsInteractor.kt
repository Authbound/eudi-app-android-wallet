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
import eu.europa.ec.businesslogic.config.AppBuildType
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsItemUi
import eu.europa.ec.dashboardfeature.ui.settings.model.SettingsMenuItemType
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.wrap.SwitchDataUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.IsUserAuthenticatedUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutUseCase
import eu.europa.ec.authenticationlogic.model.Profile
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.authenticationlogic.usecase.SignOutMode
import eu.europa.ec.businesslogic.controller.storage.PrefKeys
import eu.europa.ec.businesslogic.controller.storage.PrefsControllerV2
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.toDocumentCategory
import eu.europa.ec.corelogic.model.toDocumentIdentifier
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.collect

interface SettingsInteractor {
    fun getAppVersion(): String
    fun getChangelogUrl(): String?
    fun retrieveLogFileUris(): ArrayList<Uri>
    fun getSettingsItemsUi(changelogUrl: String?): List<SettingsItemUi>
    fun getShowBatchIssuanceCounter(): Boolean
    fun toggleShowBatchIssuanceCounter()
    fun getUserEmail(): Flow<String?>
    suspend fun logout()
    suspend fun isUserAuthenticated(): Boolean
    suspend fun getCurrentUser(): UserInfo?
    suspend fun getMyProfile(): Result<Profile>
    suspend fun resetHealthData(): Result<Unit>
}

class SettingsInteractorImpl(
    private val configLogic: ConfigLogic,
    private val logController: LogController,
    private val resourceProvider: ResourceProvider,
    private val prefKeys: PrefKeys,
    private val prefsController: PrefsControllerV2,
    private val walletCoreDocumentsController: WalletCoreDocumentsController,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val isUserAuthenticatedUseCase: IsUserAuthenticatedUseCase,
    private val getMyProfileUseCase: GetMyProfileUseCase,
) : SettingsInteractor {

    override fun getAppVersion(): String = configLogic.appVersion

    override fun getChangelogUrl(): String? = configLogic.changelogUrl

    override fun retrieveLogFileUris(): ArrayList<Uri> {
        return ArrayList(logController.retrieveLogFileUris())
    }

    override fun getSettingsItemsUi(changelogUrl: String?): List<SettingsItemUi> {
        return buildList {
            add(
                SettingsItemUi(
                    type = SettingsMenuItemType.ACCOUNT_DETAILS,
                    data = ListItemDataUi(
                        itemId = "account_details",
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = "Account details"
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.UserIcon
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )

            add(
                SettingsItemUi(
                    type = SettingsMenuItemType.MY_DATA,
                    data = ListItemDataUi(
                        itemId = "my_data",
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.settings_my_data_title)
                        ),
                        supportingText = resourceProvider.getString(R.string.settings_my_data_description),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.Id
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )

            add(
                SettingsItemUi(
                    type = SettingsMenuItemType.CHANGE_PIN,
                    data = ListItemDataUi(
                        itemId = resourceProvider.getString(R.string.dashboard_side_menu_option_change_pin_id),
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.dashboard_side_menu_option_change_pin)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.ChangePin
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )

            add(
                SettingsItemUi(
                    type = SettingsMenuItemType.RETRIEVE_LOGS,
                    data = ListItemDataUi(
                        itemId = resourceProvider.getString(R.string.settings_screen_option_retrieve_logs_id),
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.settings_screen_option_retrieve_logs)
                        ),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.OpenNew
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )

            add(
                SettingsItemUi(
                    type = SettingsMenuItemType.RESET_HEALTH_DATA,
                    data = ListItemDataUi(
                        itemId = resourceProvider.getString(R.string.settings_reset_health_data_id),
                        mainContentData = ListItemMainContentDataUi.Text(
                            text = resourceProvider.getString(R.string.settings_reset_health_data_title)
                        ),
                        supportingText = resourceProvider.getString(R.string.settings_reset_health_data_description),
                        leadingContentData = ListItemLeadingContentDataUi.Icon(
                            iconData = AppIcons.Refresh
                        ),
                        trailingContentData = ListItemTrailingContentDataUi.Icon(
                            iconData = AppIcons.KeyboardArrowRight
                        )
                    )
                )
            )

            /* if (changelogUrl != null) {
                add(
                    SettingsItemUi(
                        type = SettingsMenuItemType.CHANGELOG,
                        data = ListItemDataUi(
                            itemId = resourceProvider.getString(R.string.settings_screen_option_changelog_id),
                            mainContentData = ListItemMainContentDataUi.Text(
                                text = resourceProvider.getString(R.string.settings_screen_option_changelog)
                            ),
                            leadingContentData = ListItemLeadingContentDataUi.Icon(
                                iconData = AppIcons.OpenInBrowser
                            ),
                            trailingContentData = ListItemTrailingContentDataUi.Icon(
                                iconData = AppIcons.KeyboardArrowRight
                            )
                        )
                    )
                )
            } */

            // Add delete wallet activation option for development/debugging
            if (configLogic.appBuildType == AppBuildType.DEBUG) {
                add(
                    SettingsItemUi(
                        type = SettingsMenuItemType.DELETE_WALLET_ACTIVATION,
                        data = ListItemDataUi(
                            itemId = "delete_wallet_activation",
                            mainContentData = ListItemMainContentDataUi.Text(
                                text = "Delete Wallet Activation"
                            ),
                            leadingContentData = ListItemLeadingContentDataUi.Icon(
                                iconData = AppIcons.Delete
                            ),
                            trailingContentData = ListItemTrailingContentDataUi.Icon(
                                iconData = AppIcons.KeyboardArrowRight
                            )
                        )
                    )
                )
            }
        }
    }

    override fun getShowBatchIssuanceCounter(): Boolean {
        return getCurrentShowBatchIssuanceCounter()
    }

    override fun toggleShowBatchIssuanceCounter() {
        prefKeys.setShowBatchIssuanceCounter(
            value = !getCurrentShowBatchIssuanceCounter()
        )
    }

    private fun getCurrentShowBatchIssuanceCounter(): Boolean {
        return prefKeys.getShowBatchIssuanceCounter()
    }

    override fun getUserEmail(): Flow<String?> = flow {
        val user = getCurrentUserUseCase()
        emit(user?.email)
    }

    override suspend fun logout() {
        signOutUseCase(SignOutMode.Soft)
    }

    override suspend fun isUserAuthenticated(): Boolean {
        return isUserAuthenticatedUseCase()
    }

    override suspend fun getCurrentUser(): UserInfo? {
        return getCurrentUserUseCase()
    }

    override suspend fun getMyProfile(): Result<Profile> {
        return getMyProfileUseCase()
    }

    override suspend fun resetHealthData(): Result<Unit> {
        return try {
            val categories = walletCoreDocumentsController.getAllDocumentCategories()
            val healthDocuments = walletCoreDocumentsController.getAllIssuedDocuments()
                .filter { document ->
                    document.toDocumentIdentifier().toDocumentCategory(categories) == DocumentCategory.Health
                }
            healthDocuments.forEach { document ->
                walletCoreDocumentsController.deleteDocument(document.id).collect { }
            }
            prefsController.setBool("maisa_connected", false)
            prefsController.setString("maisa_last_sync", "")
            prefsController.setString("maisa_oauth_state", "")
            prefsController.setString("maisa_subject_ref", "")
            Result.success(Unit)
        } catch (e: Exception) {
            logController.e("SettingsInteractor", e)
            Result.failure(e)
        }
    }
}
