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

package eu.europa.ec.issuancefeature.ui.add

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import eu.europa.ec.commonfeature.config.IssuanceFlowType
import eu.europa.ec.commonfeature.config.IssuanceUiConfig
import eu.europa.ec.corelogic.controller.IssuanceMethod
import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.util.CoreActions
import eu.europa.ec.issuancefeature.ui.add.model.AddDocumentUi
import eu.europa.ec.issuancefeature.ui.add.model.CategoryGroupUi
import eu.europa.ec.issuancefeature.ui.add.model.FeaturedCredentialUi
import eu.europa.ec.issuancefeature.util.TestTag
import eu.europa.ec.issuancefeature.util.toIcon
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.ErrorInfo
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ListItemTrailingContentDataUi
import eu.europa.ec.uilogic.component.content.BroadcastAction
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ContentTitle
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.SIZE_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.utils.VSpacer
import eu.europa.ec.uilogic.component.wrap.ButtonConfig
import eu.europa.ec.uilogic.component.wrap.ButtonType
import eu.europa.ec.uilogic.component.wrap.DocumentCategoryHeader
import eu.europa.ec.uilogic.component.wrap.WrapButton
import eu.europa.ec.uilogic.component.wrap.WrapExpandableCard
import eu.europa.ec.uilogic.component.wrap.WrapImage
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.getPendingDeepLink
import eu.europa.ec.uilogic.extension.paddingFrom
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.helper.handleDeepLinkAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun AddDocumentScreen(
    navController: NavController,
    viewModel: AddDocumentViewModel
) {
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = state.navigatableAction,
        onBack = state.onBackAction,
        contentErrorConfig = state.error,
        broadcastAction = BroadcastAction(
            intentFilters = listOf(
                CoreActions.VCI_RESUME_ACTION,
                CoreActions.VCI_DYNAMIC_PRESENTATION
            ),
            callback = {
                when (it?.action) {
                    CoreActions.VCI_RESUME_ACTION -> it.extras?.getString("uri")?.let { link ->
                        viewModel.setEvent(Event.OnResumeIssuance(link))
                    }

                    CoreActions.VCI_DYNAMIC_PRESENTATION -> it.extras?.getString("uri")
                        ?.let { link ->
                            viewModel.setEvent(Event.OnDynamicPresentation(link))
                        }
                }
            }
        )
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSend = { viewModel.setEvent(it) },
            onNavigationRequested = { navigationEffect ->
                when (navigationEffect) {
                    is Effect.Navigation.Pop -> navController.popBackStack()
                    is Effect.Navigation.SwitchScreen -> {
                        navController.navigate(navigationEffect.screenRoute) {
                            popUpTo(IssuanceScreens.AddDocument.screenRoute) {
                                inclusive = navigationEffect.inclusive
                            }
                        }
                    }

                    is Effect.Navigation.Finish -> context.finish()
                    is Effect.Navigation.OpenDeepLinkAction -> handleDeepLinkAction(
                        navController,
                        navigationEffect.deepLinkUri,
                        navigationEffect.arguments
                    )
                }
            },
            paddingValues = paddingValues,
            context = context,
        )
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_PAUSE
    ) {
        viewModel.setEvent(Event.OnPause)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.Init(context.getPendingDeepLink()))
    }
}

@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSend: (Event) -> Unit,
    onNavigationRequested: (Effect.Navigation) -> Unit,
    paddingValues: PaddingValues,
    context: Context
) {
    val layoutDirection = LocalLayoutDirection.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        MainContent(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .paddingFrom(paddingValues, bottom = false),
            state = state,
            onEventSend = onEventSend,
            context = context,
        )

        if (state.showFooterScanner) {
            VSpacer.ExtraSmall()
            Footer(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = SIZE_LARGE.dp, topEnd = SIZE_LARGE.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(
                        top = SPACING_MEDIUM.dp,
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection),
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                onEventSend = onEventSend,
            )
        }
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)
            }
        }.collect()
    }
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    state: State,
    onEventSend: (Event) -> Unit,
    context: Context,
) {
    val noOptions = state.featuredCredentials.isEmpty() && state.categoryGroups.isEmpty()
            && state.isInitialised && !state.isLoading

    Column(
        modifier = modifier
    ) {
        ContentTitle(
            modifier = Modifier.fillMaxWidth(),
            title = state.title,
            subtitle = state.subtitle,
            subtitleTestTag = TestTag.AddDocumentScreen.SUBTITLE,
        )

        if (noOptions) {
            ErrorInfo(
                modifier = Modifier.fillMaxSize(),
                informativeText = stringResource(R.string.issuance_add_document_no_options)
            )
        } else {
            VSpacer.Medium()

            val listState = remember { LazyListState() }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Featured credentials section
                if (state.featuredCredentials.isNotEmpty()) {
                    item(key = "featured-header") {
                        Text(
                            text = stringResource(R.string.issuance_add_document_featured_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = SPACING_MEDIUM.dp)
                        )
                    }

                    val rows = state.featuredCredentials.chunked(2)
                    rows.forEachIndexed { rowIndex, rowItems ->
                        item(key = "featured-row-$rowIndex") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
                            ) {
                                rowItems.forEachIndexed { colIndex, featured ->
                                    val tileIndex = rowIndex * 2 + colIndex
                                    FeaturedCredentialTile(
                                        modifier = Modifier.weight(1f),
                                        featured = featured,
                                        animationDelay = tileIndex * 50,
                                        onClick = {
                                            onEventSend(
                                                Event.IssueDocument(
                                                    issuanceMethod = IssuanceMethod.OPENID4VCI,
                                                    issuerId = featured.credentialIssuerId,
                                                    configId = featured.configurationId,
                                                    context = context
                                                )
                                            )
                                        }
                                    )
                                }
                                // Fill empty space for odd last row
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            if (rowIndex < rows.lastIndex) {
                                Spacer(modifier = Modifier.height(SPACING_MEDIUM.dp))
                            }
                        }
                    }
                }

                // Browse all credentials section
                if (state.categoryGroups.isNotEmpty()) {
                    item(key = "browse-header") {
                        Spacer(modifier = Modifier.height(SPACING_LARGE.dp))
                        Text(
                            text = stringResource(R.string.issuance_add_document_browse_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = SPACING_SMALL.dp)
                        )
                    }

                    state.categoryGroups.forEachIndexed { index, group ->
                        item(key = "category-${group.category.id}") {
                            CategorySection(
                                group = group,
                                onToggle = {
                                    onEventSend(Event.ToggleCategoryExpansion(group.category.id))
                                },
                                onCredentialClick = { configId, issuerId ->
                                    onEventSend(
                                        Event.IssueDocument(
                                            issuanceMethod = IssuanceMethod.OPENID4VCI,
                                            issuerId = issuerId,
                                            configId = configId,
                                            context = context
                                        )
                                    )
                                }
                            )
                            if (index < state.categoryGroups.lastIndex) {
                                Spacer(modifier = Modifier.height(SPACING_SMALL.dp))
                            }
                        }
                    }
                }

                item(key = "footer-spacer") {
                    Spacer(
                        modifier = Modifier
                            .padding(bottom = SPACING_MEDIUM.dp)
                            .then(
                                if (!state.showFooterScanner) Modifier.navigationBarsPadding()
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedCredentialTile(
    modifier: Modifier = Modifier,
    featured: FeaturedCredentialUi,
    animationDelay: Int = 0,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        ) {
            Column(
                modifier = Modifier.padding(SPACING_MEDIUM.dp),
            ) {
                // Category icon in circular background
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        WrapImage(
                            iconData = featured.categoryIcon,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SPACING_SMALL.dp))

                // Credential name
                Text(
                    text = featured.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Description
                Text(
                    text = featured.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(SPACING_SMALL.dp))

                // Add indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WrapImage(
                        iconData = AppIcons.Add,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.generic_add),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    group: CategoryGroupUi,
    onToggle: () -> Unit,
    onCredentialClick: (configId: String, issuerId: String) -> Unit,
) {
    WrapExpandableCard(
        isExpanded = group.isExpanded,
        onExpandedChange = onToggle,
        cardCollapsedContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SPACING_MEDIUM.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocumentCategoryHeader(
                    title = stringResource(group.category.stringResId),
                    icon = group.categoryIcon,
                    documentCount = group.credentials.size,
                    modifier = Modifier.weight(1f),
                )
                WrapImage(
                    iconData = if (group.isExpanded) {
                        AppIcons.KeyboardArrowUp
                    } else {
                        AppIcons.KeyboardArrowDown
                    },
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        },
        cardExpandedContent = {
            Column(
                modifier = Modifier.padding(
                    start = SPACING_MEDIUM.dp,
                    end = SPACING_MEDIUM.dp,
                    bottom = SPACING_MEDIUM.dp,
                )
            ) {
                group.credentials.forEachIndexed { index, credential ->
                    WrapListItem(
                        modifier = Modifier.fillMaxWidth(),
                        item = credential.itemData,
                        mainContentVerticalPadding = SPACING_MEDIUM.dp,
                        mainContentTextStyle = MaterialTheme.typography.bodyLarge,
                        onItemClick = { clicked ->
                            onCredentialClick(
                                clicked.itemId,
                                credential.credentialIssuerId
                            )
                        }
                    )
                    if (index < group.credentials.lastIndex) {
                        Spacer(Modifier.height(SPACING_SMALL.dp))
                    }
                }
            }
        }
    )
}

@Composable
private fun Footer(
    modifier: Modifier = Modifier,
    onEventSend: (Event) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.issuance_add_document_scan_qr_footer_text),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )

        WrapImage(
            iconData = AppIcons.AddDocumentFromQr
        )

        WrapButton(
            modifier = Modifier.fillMaxWidth(),
            buttonConfig = ButtonConfig(
                type = ButtonType.SECONDARY,
                buttonColors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                onClick = {
                    onEventSend(Event.GoToQrScan)
                }
            )
        ) {
            Text(text = stringResource(R.string.issuance_add_document_scan_qr_footer_button_text))
        }
    }
}

@ThemeModePreviews
@Composable
private fun IssuanceAddDocumentScreenPreview() {
    PreviewTheme {
        Content(
            state = State(
                issuanceConfig = IssuanceUiConfig(
                    flowType = IssuanceFlowType.NoDocument
                ),
                showFooterScanner = true,
                navigatableAction = ScreenNavigateAction.NONE,
                title = stringResource(R.string.issuance_add_document_title),
                subtitle = stringResource(R.string.issuance_add_document_subtitle),
                isInitialised = true,
                featuredCredentials = listOf(
                    FeaturedCredentialUi(
                        credentialIssuerId = "issuer1",
                        configurationId = "pid",
                        name = "National ID",
                        description = "Your core digital identity",
                        category = DocumentCategory.Government,
                        categoryIcon = AppIcons.Government,
                    ),
                    FeaturedCredentialUi(
                        credentialIssuerId = "issuer1",
                        configurationId = "mdl",
                        name = "Driving License",
                        description = "Digital driving license",
                        category = DocumentCategory.Travel,
                        categoryIcon = AppIcons.Travel,
                    ),
                ),
                categoryGroups = listOf(
                    CategoryGroupUi(
                        category = DocumentCategory.Health,
                        categoryIcon = DocumentCategory.Health.toIcon(),
                        credentials = listOf(
                            AddDocumentUi(
                                credentialIssuerId = "issuer1",
                                configurationId = "ehic",
                                category = DocumentCategory.Health,
                                itemData = ListItemDataUi(
                                    itemId = "ehic",
                                    mainContentData = ListItemMainContentDataUi.Text(text = "EHIC"),
                                    trailingContentData = ListItemTrailingContentDataUi.Icon(
                                        iconData = AppIcons.Add
                                    )
                                )
                            )
                        ),
                    ),
                ),
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(all = SPACING_LARGE.dp),
            context = LocalContext.current
        )
    }
}

@ThemeModePreviews
@Composable
private fun DashboardAddDocumentScreenPreview() {
    PreviewTheme {
        Content(
            state = State(
                issuanceConfig = IssuanceUiConfig(
                    flowType = IssuanceFlowType.ExtraDocument(
                        formatType = null
                    )
                ),
                showFooterScanner = false,
                navigatableAction = ScreenNavigateAction.BACKABLE,
                title = stringResource(R.string.issuance_add_document_title),
                subtitle = stringResource(R.string.issuance_add_document_subtitle),
                isInitialised = true,
                featuredCredentials = listOf(
                    FeaturedCredentialUi(
                        credentialIssuerId = "issuer1",
                        configurationId = "pid",
                        name = "National ID",
                        description = "Your core digital identity",
                        category = DocumentCategory.Government,
                        categoryIcon = AppIcons.Government,
                    ),
                    FeaturedCredentialUi(
                        credentialIssuerId = "issuer1",
                        configurationId = "mdl",
                        name = "Driving License",
                        description = "Digital driving license",
                        category = DocumentCategory.Travel,
                        categoryIcon = AppIcons.Travel,
                    ),
                    FeaturedCredentialUi(
                        credentialIssuerId = "issuer1",
                        configurationId = "ehic",
                        name = "EHIC",
                        description = "European health insurance",
                        category = DocumentCategory.Health,
                        categoryIcon = AppIcons.Health,
                    ),
                ),
            ),
            effectFlow = Channel<Effect>().receiveAsFlow(),
            onEventSend = {},
            onNavigationRequested = {},
            paddingValues = PaddingValues(all = SPACING_LARGE.dp),
            context = LocalContext.current
        )
    }
}
