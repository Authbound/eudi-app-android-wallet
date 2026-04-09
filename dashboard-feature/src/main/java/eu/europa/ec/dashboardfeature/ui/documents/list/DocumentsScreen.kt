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

package eu.europa.ec.dashboardfeature.ui.documents.list

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.dashboardfeature.model.SearchItemUi
import eu.europa.ec.dashboardfeature.ui.component.BottomNavigationItem
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.dashboardfeature.util.TestTag
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.DualSelectorButton
import eu.europa.ec.uilogic.component.DualSelectorButtonDataUi
import eu.europa.ec.uilogic.component.DualSelectorButtons
import eu.europa.ec.uilogic.component.FiltersSearchBar
import eu.europa.ec.uilogic.component.InlineSnackbar
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ModalOptionUi
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.SystemBroadcastReceiver
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_EXTRA_SMALL
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.BottomSheetWithOptionsList
import eu.europa.ec.uilogic.component.wrap.BottomSheetWithTwoBigIcons
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.CredentialStatus
import eu.europa.ec.uilogic.component.wrap.CredentialVisualType
import eu.europa.ec.uilogic.component.wrap.DocumentCategoryHeader
import eu.europa.ec.uilogic.component.wrap.VisualCredentialCard
import eu.europa.ec.uilogic.component.wrap.VisualCredentialConfig
import eu.europa.ec.uilogic.component.wrap.WrapExpandableListItem
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.component.loader.SkeletonDocumentList
import eu.europa.ec.uilogic.extension.applyTestTag
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.test.DashboardTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    navHostController: NavController,
    viewModel: DocumentsViewModel,
    notificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current
    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { context.finish() },
        contentErrorConfig = null,
        topBar = {
            TopBar(
                notificationCount = notificationCount,
                onNotificationsClick = onNotificationsClick,
                onEventSend = { viewModel.setEvent(it) },
                onDashboardEventSent = onDashboardEventSent
            )
        }
    ) { paddingValues ->
        DocumentsTabContent(
            navHostController = navHostController,
            viewModel = viewModel,
            paddingValues = paddingValues,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentsTabContent(
    navHostController: NavController,
    viewModel: DocumentsViewModel,
    paddingValues: PaddingValues,
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current
    val isBottomSheetOpen: Boolean = state.isBottomSheetOpen
    val scope: CoroutineScope = rememberCoroutineScope()
    val bottomSheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    DocumentsContent(
        state = state,
        effectFlow = viewModel.effect,
        onEventSend = { viewModel.setEvent(it) },
        onNavigationRequested = { navigationEffect ->
            handleNavigationEffect(navigationEffect, navHostController, context)
        },
        paddingValues = paddingValues,
        coroutineScope = scope,
        modalBottomSheetState = bottomSheetState
    )
    if (isBottomSheetOpen) {
        WrapModalBottomSheet(
            onDismissRequest = {
                viewModel.setEvent(
                    Event.BottomSheet.UpdateBottomSheetState(
                        isOpen = false
                    )
                )
            },
            sheetState = bottomSheetState
        ) {
            DocumentsSheetContent(
                sheetContent = state.sheetContent,
                state = state,
                onEventSent = { event ->
                    viewModel.setEvent(event)
                }
            )
        }
    }
    SystemBroadcastReceiver(
        intentFilters = listOf(
            CoreActions.REVOCATION_WORK_REFRESH_ACTION
        )
    ) {
        viewModel.setEvent(Event.GetDocuments)
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    context: Context,
) {
    when (navigationEffect) {
        is Effect.Navigation.Pop -> context.finish()
        is Effect.Navigation.SwitchScreen -> {
            navController.navigate(navigationEffect.screenRoute) {
                popUpTo(navigationEffect.popUpToScreenRoute) {
                    inclusive = navigationEffect.inclusive
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onEventSend: (Event) -> Unit,
    onDashboardEventSent: (DashboardEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SPACING_SMALL.dp,
                vertical = 4.dp
            )
    ) {
        WrapIconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            iconData = AppIcons.Menu,
            customTint = MaterialTheme.colorScheme.onSurface,
        ) {
            onDashboardEventSent(OpenSideMenuEvent)
        }
        Text(
            modifier = Modifier.align(Alignment.Center),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            text = stringResource(R.string.documents_screen_title)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WrapIconButton(
                modifier = Modifier.testTag(TestTag.DocumentsScreen.PLUS_BUTTON),
                iconData = AppIcons.Add,
                customTint = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                onEventSend(Event.AddDocumentPressed)
            }
            NotificationIconButton(
                badgeCount = notificationCount,
                onClick = onNotificationsClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentsContent(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
    coroutineScope: CoroutineScope,
    modalBottomSheetState: SheetState,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onEventSend(Event.PullToRefresh) },
        modifier = Modifier
            .applyTestTag(DashboardTestTags.Documents.ROOT)
            .fillMaxSize()
            .paddingFrom(paddingValues, bottom = false)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp),
        ) {
            item {
                val searchItemUi =
                    SearchItemUi(searchLabel = stringResource(R.string.documents_screen_search_label))
                FiltersSearchBar(
                    placeholder = searchItemUi.searchLabel,
                    onValueChange = { onEventSend(Event.OnSearchQueryChanged(it)) },
                    onFilterClick = { onEventSend(Event.FiltersPressed) },
                    onClearClick = { onEventSend(Event.OnSearchQueryChanged("")) },
                    isFilteringActive = state.isFilteringActive,
                    text = state.searchText
                )
                VSpacer.Large()
            }

            if (state.isLoading && state.documentsUi.isEmpty()) {
                // Show skeleton loading state
                item {
                    SkeletonDocumentList(
                        count = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SPACING_MEDIUM.dp)
                    )
                }
            } else if (state.showNoResultsFound) {
                item {
                    NoResults(modifier = Modifier.fillMaxWidth())
                }
            } else {
                itemsIndexed(items = state.documentsUi) { index, (documentCategory, documents) ->
                    DocumentCategorySection(
                        modifier = Modifier.fillMaxWidth(),
                        category = documentCategory,
                        documents = documents,
                        categoryIndex = index,
                        onEventSend = onEventSend
                    )

                    // Section divider between categories
                    if (index != state.documentsUi.lastIndex) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 48.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        if (state.error != null) {
            InlineSnackbar(
                error = state.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SPACING_EXTRA_SMALL.dp)
            )
        }
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        onEventSend(Event.GetDocuments)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        onEventSend(Event.OnPause)
    }

    OneTimeLaunchedEffect {
        onEventSend(Event.Init)
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)

                is Effect.CloseBottomSheet -> {
                    coroutineScope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!modalBottomSheetState.isVisible) {
                            onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
                        }
                    }
                }

                is Effect.ShowBottomSheet -> {
                    onEventSend(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                }

                is Effect.DocumentsFetched -> {
                    onEventSend(Event.TryIssuingDeferredDocuments(effect.deferredDocs))
                }

                is Effect.ResumeOnApplyFilter -> {
                    onEventSend(Event.GetDocuments)
                }
            }
        }.collect()
    }
}

/**
 * A redesigned document category section with premium visual credential cards.
 * Features Apple Wallet-style cards with gradient backgrounds and clear visual hierarchy.
 */
@Composable
private fun DocumentCategorySection(
    modifier: Modifier = Modifier,
    category: DocumentCategory,
    documents: List<DocumentUi>,
    categoryIndex: Int,
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = SPACING_MEDIUM.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        // Category header with icon - each category gets a unique, recognizable icon
        val categoryIcon = when (category) {
            DocumentCategory.Government -> AppIcons.Government
            DocumentCategory.Finance -> AppIcons.Finance
            DocumentCategory.Education -> AppIcons.Education
            DocumentCategory.Health -> AppIcons.Health
            DocumentCategory.Travel -> AppIcons.Travel
            DocumentCategory.SocialSecurity -> AppIcons.SocialSecurity
            DocumentCategory.Retail -> AppIcons.Retail
            DocumentCategory.Other -> AppIcons.Folder
        }

        DocumentCategoryHeader(
            title = stringResource(category.stringResId),
            icon = categoryIcon,
            documentCount = documents.size
        )

        documents.forEachIndexed { docIndex, documentItem: DocumentUi ->
            // Map DocumentIssuanceStateUi to CredentialStatus
            val status = when (documentItem.documentIssuanceState) {
                DocumentIssuanceStateUi.Issued -> CredentialStatus.ISSUED
                DocumentIssuanceStateUi.Pending -> CredentialStatus.PENDING
                DocumentIssuanceStateUi.Failed -> CredentialStatus.PENDING
                DocumentIssuanceStateUi.Expired -> CredentialStatus.EXPIRED
                DocumentIssuanceStateUi.Revoked -> CredentialStatus.REVOKED
            }

            // Map DocumentIdentifier to CredentialVisualType for premium styling
            val visualType = when (documentItem.documentIdentifier) {
                is DocumentIdentifier.MdocPid,
                is DocumentIdentifier.SdJwtPid -> CredentialVisualType.PID
                is DocumentIdentifier.OTHER -> {
                    // Check formatType for mDL or map by category
                    val formatType = documentItem.documentIdentifier.formatType.lowercase()
                    when {
                        formatType.contains("mdl") || formatType.contains("driving") -> CredentialVisualType.MDL
                        category == DocumentCategory.Education -> CredentialVisualType.DIPLOMA
                        category == DocumentCategory.Health -> CredentialVisualType.HEALTH
                        else -> CredentialVisualType.GENERIC
                    }
                }
            }

            // Extract title from mainContentData - prefer user-friendly display names
            val rawTitle = when (val content = documentItem.uiData.mainContentData) {
                is ListItemMainContentDataUi.Text -> content.text
                is ListItemMainContentDataUi.Image -> documentItem.uiData.itemId
            }

            // Get user-friendly display title (maps technical names to readable ones)
            val displayTitle = getDisplayTitle(
                documentIdentifier = documentItem.documentIdentifier,
                issuer = documentItem.uiData.overlineText,
                fallbackTitle = rawTitle
            )

            // Check if this document should show a photo (PID/mDL can have portrait)
            val hasPhoto = visualType == CredentialVisualType.PID ||
                           visualType == CredentialVisualType.MDL ||
                           !documentItem.portraitBase64.isNullOrBlank()

            VisualCredentialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (visualType == CredentialVisualType.PID) {
                            Modifier.applyTestTag(DashboardTestTags.Documents.PID_CARD)
                        } else {
                            Modifier
                        }
                    ),
                config = VisualCredentialConfig(
                    id = documentItem.uiData.itemId,
                    visualType = visualType,
                    title = displayTitle,
                    subtitle = getCredentialSubtitle(visualType),
                    holderName = null, // Will be populated from actual document data
                    issuerName = documentItem.uiData.overlineText,
                    primaryField = null,
                    secondaryField = null,
                    status = status,
                    expiryDate = documentItem.uiData.supportingText?.removePrefix("Valid until: "),
                    hasPhoto = hasPhoto,
                    portraitBase64 = documentItem.portraitBase64
                ),
                animationDelay = (categoryIndex * 100) + (docIndex * 50),
                enableAnimations = false,
                onClick = {
                    val onItemClickEvent = if (
                        documentItem.documentIssuanceState == DocumentIssuanceStateUi.Pending
                        || documentItem.documentIssuanceState == DocumentIssuanceStateUi.Failed
                    ) {
                        Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.DocumentSelected(
                            documentId = documentItem.uiData.itemId
                        )
                    } else {
                        Event.GoToDocumentDetails(documentItem.uiData.itemId)
                    }
                    onEventSend(onItemClickEvent)
                }
            )
        }
    }
}

/**
 * Get subtitle text for credential type.
 */
private fun getCredentialSubtitle(type: CredentialVisualType): String? {
    return when (type) {
        CredentialVisualType.PID -> "Personal Identification Data"
        CredentialVisualType.MDL -> "Mobile Driving License"
        CredentialVisualType.DIPLOMA -> "Education Credential"
        CredentialVisualType.HEALTH -> "Health Credential"
        CredentialVisualType.GENERIC -> null
    }
}

/**
 * Get user-friendly display title for a document.
 * Maps technical document identifiers to human-readable names.
 * Prioritizes UX over technical accuracy - users see meaningful names.
 */
private fun getDisplayTitle(
    documentIdentifier: DocumentIdentifier,
    issuer: String?,
    fallbackTitle: String
): String {
    return when (documentIdentifier) {
        is DocumentIdentifier.MdocPid,
        is DocumentIdentifier.SdJwtPid -> {
            // Check if Authbound-issued for special branding
            if (issuer?.contains("authbound", ignoreCase = true) == true ||
                issuer?.contains("Authbound", ignoreCase = false) == true) {
                "Authbound ID"
            } else {
                "National ID Card"
            }
        }
        is DocumentIdentifier.OTHER -> {
            val formatType = documentIdentifier.formatType.lowercase()
            when {
                formatType.contains("mdl") || formatType.contains("driving") -> "Driving License"
                formatType.contains("passport") -> "Passport"
                formatType.contains("diploma") || formatType.contains("degree") -> "Education Credential"
                formatType.contains("health") || formatType.contains("medical") -> "Health Credential"
                // Fallback to the provided title if we can't determine a better one
                else -> fallbackTitle
            }
        }
    }
}

@Composable
private fun NoResults(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        WrapListItem(
            item = ListItemDataUi(
                itemId = stringResource(R.string.documents_screen_search_no_results_id),
                mainContentData = ListItemMainContentDataUi.Text(text = stringResource(R.string.documents_screen_search_no_results)),
            ),
            onItemClick = null,
            modifier = Modifier.fillMaxWidth(),
            mainContentVerticalPadding = SPACING_MEDIUM.dp,
        )
    }
}

@Composable
private fun DocumentsSheetContent(
    sheetContent: DocumentsBottomSheetContent,
    state: State,
    onEventSent: (event: Event) -> Unit,
) {
    when (sheetContent) {
        is DocumentsBottomSheetContent.Filters -> {
            GenericBottomSheet(
                titleContent = {
                    Text(
                        text = stringResource(R.string.documents_screen_filters_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                bodyContent = {
                    val expandStateList by remember {
                        mutableStateOf(state.filtersUi.map { false }.toMutableStateList())
                    }

                    var buttonsRowHeight by remember { mutableIntStateOf(0) }

                    Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = with(LocalDensity.current) { buttonsRowHeight.toDp() }),
                            verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
                        ) {
                            DualSelectorButtons(state.sortOrder) {
                                onEventSent(Event.OnSortingOrderChanged(it))
                            }
                            state.filtersUi.forEachIndexed { index, filter ->
                                if (filter.nestedItems.isNotEmpty()) {
                                    WrapExpandableListItem(
                                        header = filter.header,
                                        data = filter.nestedItems,
                                        isExpanded = expandStateList[index],
                                        onExpandedChange = {
                                            expandStateList[index] = !expandStateList[index]
                                        },
                                        onItemClick = {
                                            val id = it.itemId
                                            val groupId = filter.header.itemId
                                            onEventSent(Event.OnFilterSelectionChanged(id, groupId))
                                        },
                                        addDivider = false,
                                        collapsedMainContentVerticalPadding = SPACING_MEDIUM.dp,
                                        expandedMainContentVerticalPadding = SPACING_MEDIUM.dp,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .onGloballyPositioned { coordinates ->
                                    buttonsRowHeight = coordinates.size.height
                                }
                                .padding(top = SPACING_LARGE.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WrapButton(
                                modifier = Modifier.weight(1f),
                                buttonConfig = ButtonConfig(
                                    type = ButtonType.SECONDARY,
                                    onClick = {
                                        onEventSent(Event.OnFiltersReset)
                                    }
                                )
                            ) {
                                Text(text = stringResource(R.string.documents_screen_filters_reset))
                            }
                            HSpacer.Small()
                            WrapButton(
                                modifier = Modifier.weight(1f),
                                buttonConfig = ButtonConfig(
                                    type = ButtonType.PRIMARY,
                                    onClick = {
                                        onEventSent(Event.OnFiltersApply)
                                    }
                                )
                            ) {
                                Text(text = stringResource(R.string.documents_screen_filters_apply))
                            }
                        }
                    }
                }
            )
        }

        is DocumentsBottomSheetContent.AddDocument -> {
            BottomSheetWithTwoBigIcons(
                textData = BottomSheetTextDataUi(
                    title = stringResource(R.string.documents_screen_add_document_title),
                    message = stringResource(R.string.documents_screen_add_document_description)
                ),
                options = listOf(
                    ModalOptionUi(
                        title = stringResource(R.string.documents_screen_add_document_option_list),
                        leadingIcon = AppIcons.AddDocumentFromList,
                        event = Event.BottomSheet.AddDocument.FromList,
                    ),
                    ModalOptionUi(
                        title = stringResource(R.string.documents_screen_add_document_option_qr),
                        leadingIcon = AppIcons.AddDocumentFromQr,
                        event = Event.BottomSheet.AddDocument.ScanQr,
                    )
                ),
                onEventSent = onEventSent,
                hostTab = BottomNavigationItem.Wallet.route.lowercase(),
            )
        }

        is DocumentsBottomSheetContent.DeferredDocumentPressed -> {
            DialogBottomSheet(
                textData = BottomSheetTextDataUi(
                    title = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_document_pressed_title
                    ),
                    message = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_document_pressed_subtitle
                    ),
                    positiveButtonText = stringResource(id = R.string.dashboard_bottom_sheet_deferred_document_pressed_primary_button_text),
                    negativeButtonText = stringResource(id = R.string.dashboard_bottom_sheet_deferred_document_pressed_secondary_button_text),
                ),
                onPositiveClick = {
                    onEventSent(
                        Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.PrimaryButtonPressed(
                            documentId = sheetContent.documentId
                        )
                    )
                },
                onNegativeClick = {
                    onEventSent(
                        Event.BottomSheet.DeferredDocument.DeferredNotReadyYet.SecondaryButtonPressed(
                            documentId = sheetContent.documentId
                        )
                    )
                }
            )
        }

        is DocumentsBottomSheetContent.DeferredDocumentsReady -> {
            BottomSheetWithOptionsList(
                textData = BottomSheetTextDataUi(
                    title = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_documents_ready_title
                    ),
                    message = stringResource(
                        id = R.string.dashboard_bottom_sheet_deferred_documents_ready_subtitle
                    ),
                ),
                options = sheetContent.options,
                onEventSent = onEventSent,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun DocumentsScreenPreview() {
    PreviewTheme {
        val scope = rememberCoroutineScope()
        val bottomSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.NONE,
            onBack = { },
            topBar = {
                TopBar(
                    notificationCount = 0,
                    onNotificationsClick = {},
                    onEventSend = {},
                    onDashboardEventSent = {}
                )

            },
        ) { paddingValues ->
            val issuerName = "Issuer name"
            val validUntil = "Valid Until"
            val documentsList = listOf(
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id1",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 1"),
                        overlineText = issuerName,
                        supportingText = validUntil,
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.MdocPid,
                    documentCategory = DocumentCategory.Government
                ),
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id2",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 2"),
                        overlineText = issuerName,
                        supportingText = validUntil,
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.MdocPid,
                    documentCategory = DocumentCategory.Government
                ),
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id3",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 3"),
                        overlineText = issuerName,
                        supportingText = validUntil,
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.OTHER(formatType = ""),
                    documentCategory = DocumentCategory.Finance
                ),
                DocumentUi(
                    documentIssuanceState = DocumentIssuanceStateUi.Issued,
                    uiData = ListItemDataUi(
                        itemId = "id4",
                        mainContentData = ListItemMainContentDataUi.Text(text = "Document 4"),
                        overlineText = issuerName,
                        supportingText = validUntil,
                        leadingContentData = null,
                        trailingContentData = null
                    ),
                    documentIdentifier = DocumentIdentifier.OTHER(formatType = ""),
                    documentCategory = DocumentCategory.Other
                ),
            )
            DocumentsContent(
                state = State(
                    isLoading = false,
                    isFilteringActive = false,
                    sortOrder = DualSelectorButtonDataUi(
                        first = "first",
                        second = "second",
                        selectedButton = DualSelectorButton.FIRST,
                    ),
                    documentsUi = documentsList.groupBy { it.documentCategory }.toList(),
                ),
                effectFlow = Channel<Effect>().receiveAsFlow(),
                onEventSend = {},
                onNavigationRequested = {},
                paddingValues = paddingValues,
                coroutineScope = scope,
                modalBottomSheetState = bottomSheetState,
            )
        }
    }
}
