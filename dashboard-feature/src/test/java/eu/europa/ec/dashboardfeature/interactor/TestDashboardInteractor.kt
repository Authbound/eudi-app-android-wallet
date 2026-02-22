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

import eu.europa.ec.authenticationlogic.usecase.GetCurrentUserUseCase
import eu.europa.ec.authenticationlogic.usecase.GetMyProfileUseCase
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.corelogic.controller.WalletCoreDocumentsController
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuItemUi
import eu.europa.ec.dashboardfeature.ui.dashboard.model.SideMenuTypeUi
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.testfeature.util.StringResourceProviderMocker.mockResourceProviderStrings
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class TestDashboardInteractor {

    @Mock
    private lateinit var resourceProvider: ResourceProvider

    @Mock
    private lateinit var configLogic: ConfigLogic

    @Mock
    private lateinit var getCurrentUserUseCase: GetCurrentUserUseCase

    @Mock
    private lateinit var getMyProfileUseCase: GetMyProfileUseCase

    @Mock
    private lateinit var walletCoreDocumentsController: WalletCoreDocumentsController

    private lateinit var interactor: DashboardInteractor

    private lateinit var closeable: AutoCloseable

    @Before
    fun before() {
        closeable = MockitoAnnotations.openMocks(this)
        interactor = DashboardInteractorImpl(
            resourceProvider = resourceProvider,
            configLogic = configLogic,
            getCurrentUserUseCase = getCurrentUserUseCase,
            getMyProfileUseCase = getMyProfileUseCase,
            walletCoreDocumentsController = walletCoreDocumentsController,
        )
    }

    @After
    fun after() {
        closeable.close()
    }

    //region getSideMenuOptions
    @Test
    fun `When getSideMenuOptions is called, Then it returns items with correct data`() {
        mockStringsNeededForGetSideMenuOptions(resourceProvider)
        val sideMenuItems = interactor.getSideMenuOptions()
        assertEquals(7, sideMenuItems.size)
        val quickActionsHeaderItem = sideMenuItems[0] as SideMenuItemUi.Header
        assertEquals(quickActionsHeaderText, quickActionsHeaderItem.title)
        val authenticateItem = sideMenuItems[1] as SideMenuItemUi.ActionItem
        assertEquals(SideMenuTypeUi.AUTHENTICATE, authenticateItem.type)
        assertEquals("authenticate", authenticateItem.data.itemId)
        val authenticateMain = authenticateItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(authenticateText, authenticateMain.text)
        val authenticateLeading =
            authenticateItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.TouchId, authenticateLeading.iconData)
        val authenticateTrailing =
            authenticateItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, authenticateTrailing.iconData)
        val addDocumentItem = sideMenuItems[2] as SideMenuItemUi.ActionItem
        assertEquals(SideMenuTypeUi.ADD_DOCUMENT, addDocumentItem.type)
        assertEquals("add_document", addDocumentItem.data.itemId)
        val addDocumentMain = addDocumentItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(addDocumentText, addDocumentMain.text)
        val addDocumentLeading =
            addDocumentItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.Id, addDocumentLeading.iconData)
        val addDocumentTrailing =
            addDocumentItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, addDocumentTrailing.iconData)
        val verifyItem = sideMenuItems[3] as SideMenuItemUi.ActionItem
        assertEquals(SideMenuTypeUi.VERIFY, verifyItem.type)
        assertEquals("verify", verifyItem.data.itemId)
        val verifyMain = verifyItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(verifyText, verifyMain.text)
        val verifyLeading = verifyItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.Verified, verifyLeading.iconData)
        val verifyTrailing = verifyItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, verifyTrailing.iconData)
        val signItem = sideMenuItems[4] as SideMenuItemUi.ActionItem
        assertEquals(SideMenuTypeUi.SIGN, signItem.type)
        assertEquals("sign_document", signItem.data.itemId)
        assertEquals(false, signItem.isEnabled)
        val signMain = signItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(signText, signMain.text)
        val signLeading = signItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.Sign, signLeading.iconData)
        val signTrailing =
            signItem.data.trailingContentData as ListItemTrailingContentDataUi.TextWithIcon
        assertEquals(comingSoonText, signTrailing.text)
        assertEquals(AppIcons.KeyboardArrowRight, signTrailing.iconData)
        val profileHeaderItem = sideMenuItems[5] as SideMenuItemUi.Header
        assertEquals(profileHeaderText, profileHeaderItem.title)
        val profileItem = sideMenuItems[6] as SideMenuItemUi.ActionItem
        assertEquals(SideMenuTypeUi.PROFILE, profileItem.type)
        assertEquals(profileIdString, profileItem.data.itemId)
        val profileMain = profileItem.data.mainContentData as ListItemMainContentDataUi.Text
        assertEquals(profileText, profileMain.text)
        val profileLeading = profileItem.data.leadingContentData as ListItemLeadingContentDataUi.Icon
        assertEquals(AppIcons.UserIcon, profileLeading.iconData)
        val profileTrailing = profileItem.data.trailingContentData as ListItemTrailingContentDataUi.Icon
        assertEquals(AppIcons.KeyboardArrowRight, profileTrailing.iconData)
        verify(resourceProvider, times(1)).getString(R.string.home_screen_quick_actions)
        verify(resourceProvider, times(1)).getString(R.string.home_screen_authenticate)
        verify(resourceProvider, times(1))
            .getString(R.string.dashboard_quick_action_add_credential)
        verify(resourceProvider, times(1)).getString(R.string.verification_quick_action_title)
        verify(resourceProvider, times(1)).getString(R.string.home_screen_sign)
        verify(resourceProvider, times(1)).getString(R.string.coming_soon_badge)
        verify(resourceProvider, times(2)).getString(R.string.dashboard_side_menu_option_profile)
        verify(resourceProvider, times(1)).getString(R.string.dashboard_side_menu_option_profile_id)
    }
    //endregion

    //region Mock Calls
    private fun mockStringsNeededForGetSideMenuOptions(resourcesProvider: ResourceProvider) {
        mockResourceProviderStrings(
            resourcesProvider,
            listOf(
                R.string.home_screen_quick_actions to quickActionsHeaderText,
                R.string.home_screen_authenticate to authenticateText,
                R.string.dashboard_quick_action_add_credential to addDocumentText,
                R.string.verification_quick_action_title to verifyText,
                R.string.home_screen_sign to signText,
                R.string.coming_soon_badge to comingSoonText,
                R.string.dashboard_side_menu_option_profile to profileText,
                R.string.dashboard_side_menu_option_profile_id to profileIdString,
            )
        )
    }
    //endregion

    //region Mocked objects needed for tests.
    private val quickActionsHeaderText = "Quick Actions"
    private val authenticateText = "Authenticate"
    private val addDocumentText = "Add document"
    private val verifyText = "Verify"
    private val signText = "Sign"
    private val comingSoonText = "Coming soon"
    private val profileHeaderText = "Profile"
    private val profileIdString = "profileId"
    private val profileText = "Profile"
    //endregion
}