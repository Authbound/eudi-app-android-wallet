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
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import eu.europa.ec.dashboardfeature.ui.common.resolveCredentialVisualType
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.dashboardfeature.util.TestTag
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.brandNavyDeep
import eu.europa.ec.resourceslogic.theme.values.brandNavyMedium
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
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

/**
 * Unused upstream container: WalletScreen is the live host for the Documents tab
 * (it renders [DocumentsTabContent] directly and owns the top bar).
 */
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
            CoreActions.REVOCATION_WORK_REFRESH_ACTION,
            CoreActions.RE_ISSUANCE_WORK_REFRESH_ACTION
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
            // Searching an empty wallet is meaningless — the hero replaces the search row.
            if (!state.isWalletEmpty) {
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
            } else if (state.isWalletEmpty) {
                item {
                    EmptyWalletHero(
                        onAddDocument = { onEventSend(Event.AddDocumentPressed) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SPACING_SMALL.dp)
                    )
                }
            } else if (state.showNoMatches) {
                item {
                    NoMatchesState(
                        isFilteringActive = state.isFilteringActive,
                        hasSearchText = state.searchText.isNotBlank(),
                        onClearAll = {
                            onEventSend(Event.OnSearchQueryChanged(""))
                            if (state.isFilteringActive) {
                                onEventSend(Event.OnFiltersReset)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                itemsIndexed(items = state.documentsUi) { index, (documentCategory, documents) ->
                    DocumentCategorySection(
                        modifier = Modifier.fillMaxWidth(),
                        category = documentCategory,
                        documents = documents,
                        categoryIndex = index,
                        enableEntranceAnimations = !state.hasPlayedEntranceAnimation,
                        onEventSend = onEventSend
                    )

                    // Section divider between categories
                    if (index != state.documentsUi.lastIndex) {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = SPACING_LARGE.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
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

    // Let the staggered card entrance play exactly once per process; the flag lives in
    // ViewModel state so tab switches (which dispose this composition) don't replay it.
    val hasDocuments = state.documentsUi.isNotEmpty()
    LaunchedEffect(hasDocuments) {
        if (hasDocuments && !state.hasPlayedEntranceAnimation) {
            delay(600)
            onEventSend(Event.EntranceAnimationCompleted)
        }
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
    enableEntranceAnimations: Boolean,
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
            val visualType: CredentialVisualType = resolveCredentialVisualType(
                documentIdentifier = documentItem.documentIdentifier,
                documentCategory = category,
                issuerName = documentItem.uiData.overlineText
            )

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
                           visualType == CredentialVisualType.AUTHBOUND ||
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
                    holderName = documentItem.holderName,
                    issuerName = documentItem.uiData.overlineText,
                    primaryField = null,
                    secondaryField = null,
                    status = status,
                    expiryDate = documentItem.uiData.supportingText?.removePrefix("Valid until: "),
                    hasPhoto = hasPhoto,
                    portraitBase64 = documentItem.portraitBase64
                ),
                animationDelay = ((categoryIndex * 100) + (docIndex * 50)).coerceAtMost(300),
                enableAnimations = enableEntranceAnimations,
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
        CredentialVisualType.AUTHBOUND -> "Authbound Digital ID"
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

/**
 * Premium onboarding empty state shown when the wallet holds no documents at all.
 * Mirrors the Home screen's empty hero (navy gradient, security dot grid, concentric
 * arcs) so a new user's first two screens speak the same visual language.
 */
@Composable
private fun EmptyWalletHero(
    onAddDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "empty_wallet_hero_scale"
    )
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val gradientStart = MaterialTheme.colorScheme.brandNavyDeep
    val gradientEnd = MaterialTheme.colorScheme.brandNavyMedium
    val accent = MaterialTheme.colorScheme.tertiary

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(420)) + slideInVertically(
            animationSpec = tween(380),
            initialOffsetY = { it / 5 }
        )
    ) {
        Box(
            modifier = modifier
                .height(220.dp)
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White.copy(alpha = 0.10f))
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onAddDocument()
                }
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(gradientStart, gradientEnd),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            // Security dot-grid + concentric arc motif (same recipe as the Home empty hero)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = 22.dp.toPx()
                val dotRadius = 1.1.dp.toPx()
                val dotColor = accent.copy(alpha = 0.09f)
                var xi = 0f
                while (xi <= size.width + spacing) {
                    var yi = 0f
                    while (yi <= size.height + spacing) {
                        drawCircle(color = dotColor, radius = dotRadius, center = Offset(xi, yi))
                        yi += spacing
                    }
                    xi += spacing
                }
                val arcColor = Color(0xFF60A5FA)
                listOf(88.dp.toPx(), 130.dp.toPx(), 172.dp.toPx()).forEachIndexed { index, radius ->
                    drawArc(
                        color = arcColor.copy(alpha = 0.068f - index * 0.014f),
                        startAngle = 128f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = Offset(size.width - radius, -radius * 0.52f),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Radial glow accent hugging the top-right corner (the parent clip crops
            // the offset so it reads as corner light, not a mid-card haze)
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 55.dp, y = (-55).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SPACING_LARGE.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: icon circle + section pill badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Id,
                            customTint = Color(0xFF93C5FD),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.09f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.documents_screen_empty_badge),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color(0xFF93C5FD)
                        )
                    }
                }

                // Bottom block: headline + supporting copy + primary CTA
                Column {
                    Text(
                        text = stringResource(R.string.documents_screen_empty_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 30.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = stringResource(R.string.documents_screen_empty_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(SPACING_MEDIUM.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onAddDocument()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
                        ) {
                            WrapIcon(
                                iconData = AppIcons.Add,
                                customTint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.documents_screen_empty_cta),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quiet inline state for when search/filters match nothing — distinct from an empty
 * wallet, which gets the onboarding hero instead.
 */
@Composable
private fun NoMatchesState(
    isFilteringActive: Boolean,
    hasSearchText: Boolean,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300))
    ) {
        Column(
            modifier = modifier.padding(
                horizontal = SPACING_LARGE.dp,
                vertical = 48.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Search,
                    customTint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
            VSpacer.Medium()
            Text(
                text = stringResource(R.string.documents_screen_no_results_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            VSpacer.Small()
            Text(
                text = stringResource(R.string.documents_screen_no_results_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (isFilteringActive || hasSearchText) {
                VSpacer.Small()
                TextButton(onClick = onClearAll) {
                    Text(
                        text = stringResource(R.string.documents_screen_no_results_clear_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
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
                options = buildAddDocumentOptions(
                    shouldShowAuthboundPidEntry = state.shouldShowAuthboundPidEntry
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

@Composable
private fun buildAddDocumentOptions(
    shouldShowAuthboundPidEntry: Boolean,
): List<ModalOptionUi<Event>> {
    return buildList {
        // Option cards tint their icons, so these must be single-path glyphs:
        // the AddDocumentFrom* drawables are multi-color illustrations that
        // flatten into unreadable silhouettes when tinted.
        add(
            ModalOptionUi(
                title = stringResource(R.string.documents_screen_add_document_option_list),
                leadingIcon = AppIcons.Documents,
                accentColor = Color(0xFF3B82F6),
                event = Event.BottomSheet.AddDocument.FromList,
            )
        )
        add(
            ModalOptionUi(
                title = stringResource(R.string.documents_screen_add_document_option_qr),
                leadingIcon = AppIcons.QrScanner,
                accentColor = Color(0xFF3B82F6),
                event = Event.BottomSheet.AddDocument.ScanQr,
            )
        )
        if (shouldShowAuthboundPidEntry) {
            add(
                ModalOptionUi(
                    title = stringResource(R.string.authboundpid_get_authbound_id),
                    leadingIcon = AppIcons.Verified,
                    accentColor = Color(0xFF047857),
                    event = Event.BottomSheet.AddDocument.AuthboundPid,
                )
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
