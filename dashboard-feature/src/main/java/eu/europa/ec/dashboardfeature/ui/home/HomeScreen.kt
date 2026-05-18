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

package eu.europa.ec.dashboardfeature.ui.home

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ripple

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

import eu.europa.ec.corelogic.model.DocumentCategory
import eu.europa.ec.corelogic.model.DocumentIdentifier
import eu.europa.ec.dashboardfeature.ui.documents.detail.model.DocumentIssuanceStateUi
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.dashboardfeature.ui.home.model.HeroCredentialUi
import eu.europa.ec.dashboardfeature.ui.common.resolveCredentialVisualType
import eu.europa.ec.dashboardfeature.ui.component.NotificationIconButton
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.component.wrap.CompactCredentialCard
import eu.europa.ec.uilogic.component.wrap.CompactCredentialConfig
import eu.europa.ec.uilogic.component.wrap.CredentialStatus
import eu.europa.ec.uilogic.component.wrap.CredentialVisualType
import eu.europa.ec.uilogic.component.wrap.DocumentCategoryHeader
import eu.europa.ec.uilogic.component.wrap.VisualCredentialCard

import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.theme.values.warning
import eu.europa.ec.uilogic.component.AppIconAndText
import eu.europa.ec.uilogic.component.AppIconAndTextData
import eu.europa.ec.uilogic.component.AppIcons
import eu.europa.ec.uilogic.component.IconDataUi
import eu.europa.ec.uilogic.component.ListItemDataUi
import eu.europa.ec.uilogic.component.ListItemLeadingContentDataUi
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.ModalOptionUi
import eu.europa.ec.uilogic.component.SectionTitle
import eu.europa.ec.uilogic.component.content.ContentScreen
import eu.europa.ec.uilogic.component.content.ScreenNavigateAction
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.HSpacer
import eu.europa.ec.uilogic.component.utils.LifecycleEffect
import eu.europa.ec.uilogic.component.utils.OneTimeLaunchedEffect
import eu.europa.ec.uilogic.component.utils.SPACING_LARGE
import eu.europa.ec.uilogic.component.utils.SPACING_MEDIUM
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.ActionCardConfig
import eu.europa.ec.uilogic.component.wrap.BottomSheetTextDataUi
import eu.europa.ec.uilogic.component.wrap.BottomSheetWithTwoBigIcons
import eu.europa.ec.uilogic.component.wrap.DialogBottomSheet
import eu.europa.ec.uilogic.component.wrap.GenericBottomSheet
import eu.europa.ec.uilogic.component.wrap.QuickActionCard
import eu.europa.ec.uilogic.component.wrap.QuickActionConfig
import eu.europa.ec.uilogic.component.wrap.WrapActionCard
import eu.europa.ec.uilogic.component.wrap.WrapIcon
import eu.europa.ec.uilogic.component.wrap.WrapIconButton
import eu.europa.ec.uilogic.component.wrap.WrapListItem
import eu.europa.ec.uilogic.component.wrap.WrapModalBottomSheet
import eu.europa.ec.uilogic.extension.finish
import eu.europa.ec.uilogic.extension.openAppSettings
import eu.europa.ec.uilogic.extension.openBleSettings
import eu.europa.ec.uilogic.extension.paddingFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

typealias DashboardEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event
typealias OpenSideMenuEvent = eu.europa.ec.dashboardfeature.ui.dashboard.Event.SideMenu.Open

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navHostController: NavController,
    viewModel: HomeViewModel,
    bottomNavHostController: NavController,
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onDashboardEventSent: (DashboardEvent) -> Unit
) {
    val context = LocalContext.current
    val state: State by viewModel.viewState.collectAsStateWithLifecycle()
    val isBottomSheetOpen = state.isBottomSheetOpen
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ContentScreen(
        isLoading = state.isLoading,
        navigatableAction = ScreenNavigateAction.NONE,
        onBack = { context.finish() },
        topBar = {
            TopBar(
                notificationCount = notificationCount,
                onNotificationsClick = onNotificationsClick,
                onEventSent = onDashboardEventSent
            )
        }
    ) { paddingValues ->
        Content(
            state = state,
            effectFlow = viewModel.effect,
            onEventSent = { event ->
                viewModel.setEvent(event)
            },
            onNavigationRequested = {
                handleNavigationEffect(it, navHostController, bottomNavHostController, context)
            },
            coroutineScope = scope,
            modalBottomSheetState = bottomSheetState,
            paddingValues = paddingValues
        )
    }

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
            HomeScreenSheetContent(
                sheetContent = state.sheetContent,
                shouldShowAuthboundPidEntry = state.shouldShowAuthboundPidEntry,
                onEventSent = { event -> viewModel.setEvent(event) },
            )
        }
    }

    OneTimeLaunchedEffect {
        viewModel.setEvent(Event.Init)
    }

    LifecycleEffect(
        lifecycleOwner = LocalLifecycleOwner.current,
        lifecycleEvent = Lifecycle.Event.ON_RESUME
    ) {
        viewModel.setEvent(Event.GetCredentials)
    }
}

@Composable
private fun TopBar(
    notificationCount: Int,
    onNotificationsClick: () -> Unit,
    onEventSent: (DashboardEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SPACING_SMALL.dp,
                vertical = 4.dp
            )
    ) {
        // home menu icon
        WrapIconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            iconData = AppIcons.Menu,
            customTint = MaterialTheme.colorScheme.onSurface,
        ) {
            onEventSent(OpenSideMenuEvent)
        }
        // wallet logo
        AppIconAndText(
            modifier = Modifier.align(Alignment.Center),
            appIconAndTextData = AppIconAndTextData(),
        )
        NotificationIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            badgeCount = notificationCount,
            onClick = onNotificationsClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: State,
    effectFlow: Flow<Effect>,
    onEventSent: ((event: Event) -> Unit),
    onNavigationRequested: (navigationEffect: Effect.Navigation) -> Unit,
    coroutineScope: CoroutineScope,
    modalBottomSheetState: SheetState,
    paddingValues: PaddingValues
) {
    // Scrollable content layout
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E3A5F).copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingFrom(paddingValues, bottom = false)
                .verticalScroll(scrollState)
                .padding(vertical = SPACING_MEDIUM.dp),
            verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            // Hero Credential Section at the top
            HeroCredentialSection(
                heroCredentials = state.heroCredentials,
                isLoading = state.isLoadingHeroCredential,
                onCredentialClick = {
                    onEventSent(Event.HeroCredentialPressed)
                },
                onAddCredentialClick = {
                    onEventSent(Event.AddCredentialPressed)
                }
            )

            if (state.shouldShowAuthboundPidHomePrompt) {
                AuthboundIdHomePrompt(
                    onGetAuthboundIdClick = {
                        onEventSent(Event.GetAuthboundIdPressed)
                    },
                    onNotNowClick = {
                        onEventSent(Event.AuthboundPidPromoNotNowPressed)
                    }
                )
            }

            // Quick Actions section below hero
            QuickActionsSection(
                quickActions = state.quickActions,
                onQuickActionClick = { actionId ->
                    onEventSent(Event.QuickActionPressed(actionId))
                }
            )

            // Guide carousel section
            EudiWalletGuide()

            // Credentials section with document list
            CredentialsSection(
                isLoading = state.isLoadingCredentials,
                credentials = state.credentials,
                showEmptyMessage = state.showEmptyCredentialsMessage,
                onCredentialClick = { documentId ->
                    onEventSent(Event.CredentialPressed(documentId))
                },
                onViewAllClick = {
                    onEventSent(Event.ViewAllCredentialsPressed)
                },
                onAddCredentialClick = {
                    onEventSent(Event.AddCredentialPressed)
                }
            )

            // Bottom spacer for navigation bar clearance
            Spacer(modifier = Modifier.height(90.dp))
        }
    }

    if (state.bleAvailability == BleAvailability.NO_PERMISSION) {
        RequiredPermissionsAsk(state, onEventSent)
    }

    LaunchedEffect(Unit) {
        effectFlow.onEach { effect ->
            when (effect) {
                is Effect.Navigation -> onNavigationRequested(effect)

                is Effect.CloseBottomSheet -> {
                    coroutineScope.launch {
                        if (effect.hasNextBottomSheet.not()) {
                            modalBottomSheetState.hide()
                        } else {
                            modalBottomSheetState.hide().also {
                                modalBottomSheetState.show()
                                onEventSent(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                            }
                        }
                    }.invokeOnCompletion {
                        if (!modalBottomSheetState.isVisible) {
                            onEventSent(Event.BottomSheet.UpdateBottomSheetState(isOpen = false))
                        }
                    }
                }

                is Effect.ShowBottomSheet -> {
                    onEventSent(Event.BottomSheet.UpdateBottomSheetState(isOpen = true))
                }
            }
        }.collect()
    }
}


/**
 * "Annatar Forge" inspired Hero Guide.
 * Large visual card with vertical navigation rail.
 */
@Composable
private fun EudiWalletGuide() {
    var selectedIndex by remember { mutableStateOf(0) }
    var delayDuration by remember { mutableStateOf(5000L) }

    // Define topics with Authbound brand gradient colors
    // Navy spectrum: #0A1A36 (deepest) → #1E3A5F (medium) → #2A4A6F (lighter)
    val topics = remember {
        listOf(
            GuideTopic(
                id = "01",
                tabTitle = "Control",
                title = "Selective\nSharing",
                description = "Share only what is needed. You are in control.",
                icon = AppIcons.Visibility,
                gradientStart = Color(0xFF0A1A36),  // Deep navy
                gradientEnd = Color(0xFF1E3A5F),    // Medium navy
                accentColor = Color(0xFF3B82F6)     // Blue accent
            ),
            GuideTopic(
                id = "02",
                tabTitle = "Privacy",
                title = "Private\nby Design",
                description = "Your data stays on your device. Encrypted & Secure.",
                icon = AppIcons.TouchId,
                gradientStart = Color(0xFF1E3A5F),  // Medium navy
                gradientEnd = Color(0xFF2A4A6F),    // Lighter navy
                accentColor = Color(0xFF2A8A9A)     // Teal accent
            ),
            GuideTopic(
                id = "03",
                tabTitle = "Access",
                title = "EU-Wide\nAccess",
                description = "Accepted everywhere in the EU. All in one place.",
                icon = AppIcons.Certified,
                gradientStart = Color(0xFF0F2847),  // Deep navy variant
                gradientEnd = Color(0xFF1A3D5C),    // Medium variant
                accentColor = Color(0xFF3B82F6)     // Blue accent
            )
        )
    }

    // Auto-advance logic
    LaunchedEffect(selectedIndex) {
        delay(delayDuration)
        if (delayDuration > 5000L) {
            delayDuration = 5000L
        }
        selectedIndex = (selectedIndex + 1) % topics.size
    }

    val currentTopic = topics[selectedIndex]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SPACING_SMALL.dp)
    ) {
        // Section Header with gradient text
        GradientSectionTitle(
            text = "Guide",
            modifier = Modifier.padding(horizontal = SPACING_SMALL.dp, vertical = 8.dp)
        )

        // Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp) // Smaller hero card
                .padding(horizontal = SPACING_SMALL.dp),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background & Content Transition with gradient
                Crossfade(targetState = currentTopic, label = "hero_bg") { topic ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(topic.gradientStart, topic.gradientEnd),
                                    start = Offset(0f, 0f),
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                )
                            )
                    ) {
                        // Decorative circles (top-right, brand element)
                        GuideDecorativeCircles(
                            modifier = Modifier.align(Alignment.TopEnd),
                            color = topic.accentColor
                        )

                        // Background Decoration (Giant Icon)
                        WrapIcon(
                            iconData = topic.icon,
                            customTint = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier
                                .size(350.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 100.dp, y = -80.dp)
                                .rotate(-15f)
                        )

                        // Main Content
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .padding(end = 60.dp), // Space for nav rail
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Pill Tag
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = topic.tabTitle.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            // Big Title
                            Text(
                                text = topic.title,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 32.sp
                                ),
                                color = Color.White,
                                lineHeight = 36.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Description
                            Text(
                                text = topic.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Vertical Navigation Rail (Right Side)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    topics.forEachIndexed { index, topic ->
                        NavCircle(
                            text = topic.id,
                            isSelected = index == selectedIndex,
                            onClick = { 
                                selectedIndex = index
                                delayDuration = 10000L // Increase delay on manual interaction
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavCircle(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val height = if (isSelected) 60.dp else 40.dp
    val width = 40.dp
    val fontSize = if (isSelected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(20.dp)) // Capsule shape
            .background(Color.White.copy(alpha = if (isSelected) 1f else 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = fontSize.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.Black else Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Data class for Guide carousel topics with premium gradient styling.
 * Supports brand-aligned gradients with accent colors for visual coherence.
 */
private data class GuideTopic(
    val id: String,
    val tabTitle: String,
    val title: String,
    val description: String,
    val icon: IconDataUi,
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentColor: Color
) {
    // Legacy constructor for backward compatibility
    constructor(
        id: String,
        tabTitle: String,
        title: String,
        description: String,
        icon: IconDataUi,
        color: Color
    ) : this(
        id = id,
        tabTitle = tabTitle,
        title = title,
        description = description,
        icon = icon,
        gradientStart = color,
        gradientEnd = color.copy(alpha = 0.9f),
        accentColor = Color(0xFF3B82F6)
    )
}

/**
 * Decorative circular pattern for Guide carousel card backgrounds.
 * Creates subtle brand-aligned visual interest with varying sizes and opacities.
 */
@Composable
private fun GuideDecorativeCircles(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(
        modifier = modifier
            .size(140.dp)
            .padding(16.dp)
    ) {
        // Large circle (outline)
        drawCircle(
            color = color.copy(alpha = 0.08f),
            radius = 50.dp.toPx(),
            center = Offset(size.width * 0.7f, size.height * 0.25f),
            style = Stroke(width = 2.dp.toPx())
        )
        // Medium circle (filled)
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = 22.dp.toPx(),
            center = Offset(size.width * 0.35f, size.height * 0.5f)
        )
        // Small circle (filled)
        drawCircle(
            color = color.copy(alpha = 0.10f),
            radius = 10.dp.toPx(),
            center = Offset(size.width * 0.85f, size.height * 0.7f)
        )
    }
}

/**
 * Section title with premium gradient text effect.
 * Creates brand-aligned visual emphasis for section headers.
 */
@Composable
private fun GradientSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun handleNavigationEffect(
    navigationEffect: Effect.Navigation,
    navController: NavController,
    bottomController: NavController,
    context: Context
) {
    try {
        when (navigationEffect) {
            is Effect.Navigation.SwitchScreen -> {
                navController.navigate(navigationEffect.screenRoute) {
                    popUpTo(navigationEffect.popUpToScreenRoute) {
                        inclusive = navigationEffect.inclusive
                    }
                }
            }

            is Effect.Navigation.SwitchTab -> {
                val target = navigationEffect.tabRoute
                val targetRoute = target.substringBefore("?")
                val routeExists = bottomController.graph.findNode(target) != null ||
                    bottomController.graph.findNode(targetRoute) != null ||
                    bottomController.graph.findNode("${targetRoute}?tab={tab}") != null
                if (!routeExists) {
                    android.util.Log.e("HomeScreen", "Route '${target}' not found in bottom navigation graph")
                    return
                }
                bottomController.navigate(target) {
                    popUpTo(bottomController.graph.findStartDestination().id){
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            is Effect.Navigation.OnAppSettings -> context.openAppSettings()
            is Effect.Navigation.OnSystemSettings -> context.openBleSettings()
        }
    } catch (e: Exception) {
        // Log the error for debugging
        android.util.Log.e("HomeScreen", "Navigation error: ${e.message}", e)
        // Don't crash the app, just log the error
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenSheetContent(
    sheetContent: HomeScreenBottomSheetContent,
    shouldShowAuthboundPidEntry: Boolean,
    onEventSent: (event: Event) -> Unit,
) {
    when (sheetContent) {
        is HomeScreenBottomSheetContent.Authenticate -> {
            BottomSheetWithTwoBigIcons(
                textData = BottomSheetTextDataUi(
                    title = stringResource(R.string.home_screen_authenticate),
                    message = stringResource(R.string.home_screen_authenticate_description)
                ),
                options = listOf(
                    ModalOptionUi(
                        title = stringResource(R.string.home_screen_authenticate_option_in_person),
                        leadingIcon = AppIcons.PresentDocumentInPerson,
                        accentColor = Color(0xFF3B82F6),
                        event = Event.BottomSheet.Authenticate.OpenAuthenticateInPerson,
                    ),
                    ModalOptionUi(
                        title = stringResource(R.string.home_screen_add_document_option_online),
                        leadingIcon = AppIcons.PresentDocumentOnline,
                        accentColor = Color(0xFF7C3AED),
                        event = Event.BottomSheet.Authenticate.OpenAuthenticateOnLine,
                    )
                ),
                onEventSent = { event ->
                    onEventSent(event)
                }
            )
        }

        is HomeScreenBottomSheetContent.AddDocument -> {
            BottomSheetWithTwoBigIcons(
                textData = BottomSheetTextDataUi(
                    title = stringResource(R.string.documents_screen_add_document_title),
                    message = stringResource(R.string.documents_screen_add_document_description)
                ),
                options = buildAddDocumentOptions(
                    shouldShowAuthboundPidEntry = shouldShowAuthboundPidEntry
                ),
                onEventSent = onEventSent
            )
        }

        is HomeScreenBottomSheetContent.Verification -> {
            BottomSheetWithTwoBigIcons(
                textData = BottomSheetTextDataUi(
                    title = stringResource(R.string.verification_bottom_sheet_title),
                    message = stringResource(R.string.verification_bottom_sheet_description)
                ),
                options = listOf(
                    ModalOptionUi(
                        title = stringResource(R.string.verification_bottom_sheet_template_option),
                        leadingIcon = AppIcons.WalletActivated,
                        accentColor = Color(0xFF059669),
                        event = Event.BottomSheet.Verification.UseTemplate,
                    ),
                    ModalOptionUi(
                        title = stringResource(R.string.verification_bottom_sheet_custom_option),
                        leadingIcon = AppIcons.Edit,
                        accentColor = Color(0xFF34D399),
                        event = Event.BottomSheet.Verification.CreateCustom,
                    )
                ),
                onEventSent = onEventSent
            )}

        /**
         * Bottom sheet for Sign Document click event,
         * will be implemented in the future
         */
        is HomeScreenBottomSheetContent.Sign -> {
            BottomSheetWithTwoBigIcons(
                textData = BottomSheetTextDataUi(
                    title = stringResource(R.string.home_screen_sign_document),
                    message = stringResource(R.string.home_screen_sign_document_description)
                ),
                options = listOf(
                    ModalOptionUi(
                        title = stringResource(R.string.home_screen_sign_document_option_from_device),
                        leadingIcon = AppIcons.SignDocumentFromDevice,
                        accentColor = Color(0xFF7C3AED),
                        event = Event.BottomSheet.SignDocument.OpenFromDevice,
                    ),
                    ModalOptionUi(
                        title = stringResource(R.string.home_screen_sign_document_option_scan_qr),
                        leadingIcon = AppIcons.SignDocumentFromQr,
                        accentColor = Color(0xFFA78BFA),
                        event = Event.BottomSheet.SignDocument.OpenScanQR,
                    )
                ),
                onEventSent = { event ->
                    onEventSent(event)
                }
            )
        }

        is HomeScreenBottomSheetContent.LearnMoreAboutAuthenticate -> {
            GenericBottomSheet(
                titleContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Info,
                            customTint = MaterialTheme.colorScheme.primary
                        )
                        HSpacer.Small()
                        Text(
                            text = stringResource(R.string.home_screen_authenticate),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        )
                    }
                },
                bodyContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
                        Text(
                            stringResource(R.string.home_screen_sign_learn_more_inner_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            stringResource(R.string.home_screen_sign_learn_more_description),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            )
        }

        is HomeScreenBottomSheetContent.LearnMoreAboutSignDocument -> {
            GenericBottomSheet(
                titleContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Info,
                            customTint = MaterialTheme.colorScheme.primary
                        )
                        HSpacer.Small()
                        Text(
                            stringResource(R.string.home_screen_sign),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                },
                bodyContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)) {
                        Text(
                            stringResource(R.string.home_screen_authenticate_learn_more_inner_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            stringResource(R.string.home_screen_authenticate_learn_more_description),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            )
        }

        is HomeScreenBottomSheetContent.Bluetooth -> {
            DialogBottomSheet(
                textData = BottomSheetTextDataUi(
                    title = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_title),
                    message = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_subtitle),
                    positiveButtonText = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_primary_button_text),
                    negativeButtonText = stringResource(id = R.string.dashboard_bottom_sheet_bluetooth_secondary_button_text),
                ),
                onPositiveClick = {
                    onEventSent(
                        Event.BottomSheet.Bluetooth.PrimaryButtonPressed(
                            sheetContent.availability
                        )
                    )
                },
                onNegativeClick = { onEventSent(Event.BottomSheet.Bluetooth.SecondaryButtonPressed) }
            )
        }
    }
}

@Composable
private fun buildAddDocumentOptions(
    shouldShowAuthboundPidEntry: Boolean,
): List<ModalOptionUi<Event>> {
    return buildList {
        add(
            ModalOptionUi(
                title = stringResource(R.string.documents_screen_add_document_option_list),
                leadingIcon = AppIcons.AddDocumentFromList,
                accentColor = Color(0xFFD97706),
                event = Event.BottomSheet.AddDocument.FromList,
            )
        )
        add(
            ModalOptionUi(
                title = stringResource(R.string.documents_screen_add_document_option_qr),
                leadingIcon = AppIcons.AddDocumentFromQr,
                accentColor = Color(0xFFF59E0B),
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RequiredPermissionsAsk(
    state: State,
    onEventSend: (Event) -> Unit
) {
    val permissions: MutableList<String> = mutableListOf()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && state.isBleCentralClientModeEnabled) {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    when {
        permissionsState.allPermissionsGranted -> onEventSend(Event.StartProximityFlow)
        !permissionsState.allPermissionsGranted && permissionsState.shouldShowRationale -> {
            onEventSend(Event.OnShowPermissionsRational)
        }

        else -> {
            onEventSend(Event.OnPermissionStateChanged(BleAvailability.UNKNOWN))
            LaunchedEffect(Unit) {
                permissionsState.launchMultiplePermissionRequest()
            }
        }
    }
}

/**
 * Quick Actions grid layout section
 */
@Composable
private fun QuickActionsSection(
    quickActions: List<QuickActionConfig>,
    onQuickActionClick: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_LARGE.dp)
    ) {
        // Section title
        /* Text(
            text = stringResource(R.string.home_screen_quick_actions),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SPACING_SMALL.dp)
        ) */

        // First row - first two actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SPACING_SMALL.dp),
            horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
        ) {
            quickActions.take(2).forEachIndexed { index, action ->
                QuickActionCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp),
                    config = action,
                    animationDelay = index * 70,
                    onClick = { onQuickActionClick(action.id) }
                )
            }
        }

        // Second row - next two actions (if available)
        if (quickActions.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SPACING_SMALL.dp),
                horizontalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
            ) {
                quickActions.drop(2).take(2).forEachIndexed { index, action ->
                    if (action.id == "sign") {
                        Box(modifier = Modifier.weight(1f)) {
                            QuickActionCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                config = action,
                                animationDelay = (index + 2) * 70,
                                onClick = {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.feature_coming_soon),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = stringResource(R.string.coming_soon_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        QuickActionCard(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp),
                            config = action,
                            animationDelay = (index + 2) * 70,
                            onClick = { onQuickActionClick(action.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthboundIdHomePrompt(
    onGetAuthboundIdClick: () -> Unit,
    onNotNowClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF022C22),
                        Color(0xFF064E3B),
                        Color(0xFF065F46)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        // Diagonal stripe texture (distinct from the arc/orb pattern on hero card)
        Canvas(modifier = Modifier.matchParentSize()) {
            val stripeColor = Color(0xFF34D399).copy(alpha = 0.055f)
            val stripeWidth = 1.dp.toPx()
            val stripeSpacing = 18.dp.toPx()
            var offset = -size.height
            while (offset < size.width + size.height) {
                drawLine(
                    color = stripeColor,
                    start = Offset(offset, 0f),
                    end = Offset(offset + size.height, size.height),
                    strokeWidth = stripeWidth
                )
                offset += stripeSpacing
            }
            // Emerald glow in top-right
            drawCircle(
                color = Color(0xFF34D399).copy(alpha = 0.10f),
                radius = 68.dp.toPx(),
                center = Offset(size.width * 0.92f, -size.height * 0.15f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Shield/verified badge — emerald glow treatment
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF34D399).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                WrapIcon(
                    iconData = AppIcons.Verified,
                    customTint = Color(0xFF6EE7B7),
                    modifier = Modifier.size(27.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = stringResource(R.string.authboundpid_home_prompt_title),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.authboundpid_home_prompt_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Emerald CTA pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF059669))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(color = Color.White.copy(alpha = 0.20f))
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onGetAuthboundIdClick()
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.authboundpid_home_prompt_action),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                TextButton(
                    onClick = onNotNowClick,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.authboundpid_not_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.42f)
                    )
                }
            }
        }
    }
}

/**
 * Hero Credential Section - displays the primary credential (PID or mDL) at the top
 */
@Composable
private fun HeroCredentialSection(
    heroCredentials: List<HeroCredentialUi>,
    isLoading: Boolean,
    onCredentialClick: () -> Unit,
    onAddCredentialClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp)
    ) {
        when {
            isLoading -> {
                // Loading state - show placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            heroCredentials.isNotEmpty() -> {
                val listState = rememberLazyListState()
                val selectedPage by remember(listState, heroCredentials.size) {
                    derivedStateOf {
                        val lastPageIndex: Int = heroCredentials.lastIndex.coerceAtLeast(0)
                        val visibleItemIndex: Int? = listState.layoutInfo.visibleItemsInfo
                            .maxByOrNull { itemInfo ->
                                val itemStart: Int = itemInfo.offset
                                val itemEnd: Int = itemInfo.offset + itemInfo.size
                                val visibleStart: Int = max(itemStart, listState.layoutInfo.viewportStartOffset)
                                val visibleEnd: Int = min(itemEnd, listState.layoutInfo.viewportEndOffset)
                                visibleEnd - visibleStart
                            }?.index
                        (visibleItemIndex ?: listState.firstVisibleItemIndex).coerceIn(0, lastPageIndex)
                    }
                }
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val itemWidth = maxWidth
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
                    ) {
                        items(
                            items = heroCredentials,
                            key = { heroCredential -> heroCredential.documentId }
                        ) { heroCredential ->
                            VisualCredentialCard(
                                config = heroCredential.toVisualConfig(),
                                modifier = Modifier
                                    .width(itemWidth)
                                    .height(200.dp),
                                showAuthboundBadge = heroCredential.isAuthboundIssued(),
                                onClick = onCredentialClick
                            )
                        }
                    }
                }
                if (heroCredentials.size > 1) {
                    HeroCredentialPageIndicator(
                        pageCount = heroCredentials.size,
                        selectedPage = selectedPage
                    )
                }

                // Tap to share hint
                Text(
                    text = stringResource(R.string.home_hero_tap_to_share),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                EmptyHeroCard(onAddCredentialClick = onAddCredentialClick)
            }
        }
    }
}

@Composable
private fun HeroCredentialPageIndicator(
    pageCount: Int,
    selectedPage: Int,
) {
    val pageIndicatorContentDescription: String = stringResource(
        R.string.authboundpid_hero_page_indicator,
        selectedPage + 1,
        pageCount
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SPACING_SMALL.dp)
            .semantics {
                contentDescription = pageIndicatorContentDescription
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            val isSelected = page == selectedPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
            )
        }
    }
}

private fun HeroCredentialUi.isAuthboundIssued(): Boolean {
    return issuerName?.contains("authbound", ignoreCase = true) == true
}

@Composable
private fun EmptyHeroCard(
    onAddCredentialClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(420)) + slideInVertically(
            animationSpec = tween(380),
            initialOffsetY = { it / 5 }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White.copy(alpha = 0.10f))
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onAddCredentialClick()
                }
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0C1D3E),
                            Color(0xFF112B52),
                            Color(0xFF1A3A62)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            // Security dot-grid + concentric arc motif (ID card inspired)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = 22.dp.toPx()
                val dotR = 1.1.dp.toPx()
                val dotColor = Color(0xFF3B82F6).copy(alpha = 0.09f)
                var xi = 0f
                while (xi <= size.width + spacing) {
                    var yi = 0f
                    while (yi <= size.height + spacing) {
                        drawCircle(color = dotColor, radius = dotR, center = Offset(xi, yi))
                        yi += spacing
                    }
                    xi += spacing
                }
                // Concentric arcs top-right
                val arcColor = Color(0xFF60A5FA)
                listOf(88.dp.toPx(), 130.dp.toPx(), 172.dp.toPx()).forEachIndexed { i, r ->
                    drawArc(
                        color = arcColor.copy(alpha = 0.068f - i * 0.014f),
                        startAngle = 128f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = Offset(size.width - r, -r * 0.52f),
                        size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Radial glow accent (top-right)
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Content: icon/badge top — title/arrow bottom
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: icon circle + "DIGITAL WALLET" pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WrapIcon(
                            iconData = AppIcons.Id,
                            customTint = Color(0xFF93C5FD),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.09f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = stringResource(R.string.home_hero_digital_wallet_label),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Bottom row: headline + arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_hero_empty_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                                lineHeight = 30.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = stringResource(R.string.home_hero_empty_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f),
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB).copy(alpha = 0.38f)),
                        contentAlignment = Alignment.Center
                    ) {
                        WrapIcon(
                            iconData = AppIcons.KeyboardArrowRight,
                            customTint = Color(0xFF93C5FD),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Credentials section to display user's digital documents
 * @deprecated Replaced by HeroCredentialSection - kept for backwards compatibility
 */
@Composable
private fun CredentialsSection(
    isLoading: Boolean,
    credentials: List<Pair<DocumentCategory, List<DocumentUi>>>,
    showEmptyMessage: Boolean,
    onCredentialClick: (DocumentId) -> Unit,
    onViewAllClick: () -> Unit,
    onAddCredentialClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SPACING_SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(SPACING_MEDIUM.dp)
    ) {
        // Section header with title and View All button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientSectionTitle(
                text = stringResource(R.string.dashboard_home_screen_credential_section)
            )

            TextButton(onClick = onViewAllClick) {
                Text(
                    text = stringResource(R.string.dashboard_home_screen_view_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        // Premium empty state
        else if (showEmptyMessage) {
            CredentialsEmptyState(
                onAddCredentialClick = onAddCredentialClick
            )
        }
        // Display credentials list
        else {
            credentials.forEach { (category, documents) ->
                if (documents.isNotEmpty()) {
                    CredentialCategory(
                        category = category,
                        documents = documents,
                        onCredentialClick = onCredentialClick
                    )
                }
            }
        }
    }
}

/**
 * Minimalist invitation empty state for credentials section.
 * Deliberately understated design to contrast with bold hero card.
 * Uses line art illustration and subtle depth instead of gradients.
 * Creates visual hierarchy: Hero (bold) > Quick Actions (medium) > Empty States (minimal)
 */
@Composable
private fun CredentialsEmptyState(
    onAddCredentialClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale"
    )

    // Entrance animation - staggered fade
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }

    // Colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + slideInVertically(
            animationSpec = tween(400, easing = androidx.compose.animation.core.EaseOutCubic),
            initialOffsetY = { it / 6 }
        )
    ) {
        // Card with inset shadow effect (appears recessed, not raised)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(24.dp))
                .background(surfaceColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = primaryColor.copy(alpha = 0.08f))
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onAddCredentialClick()
                }
        ) {
            // Subtle inset border - creates depth without elevation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                outlineColor.copy(alpha = 0.15f),
                                outlineColor.copy(alpha = 0.05f)
                            )
                        )
                    )
            ) {
                // Inner content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(surfaceColor)
                ) {
                    // Decorative corner accent (top-right) - subtle geometric element
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(100.dp)
                    ) {
                        // Minimal line art - two intersecting arcs
                        drawArc(
                            color = outlineColor.copy(alpha = 0.12f),
                            startAngle = 90f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.3f, -size.height * 0.5f),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawArc(
                            color = primaryColor.copy(alpha = 0.08f),
                            startAngle = 90f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(size.width * 0.5f, -size.height * 0.3f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.8f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Line art illustration - stacked cards outline
                        Box(
                            modifier = Modifier.size(72.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Back card (offset)
                            Box(
                                modifier = Modifier
                                    .size(48.dp, 32.dp)
                                    .offset(x = 6.dp, y = (-4).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(outlineColor.copy(alpha = 0.08f))
                            )
                            // Front card with dashed outline
                            Canvas(
                                modifier = Modifier
                                    .size(52.dp, 36.dp)
                                    .offset(x = (-2).dp, y = 4.dp)
                            ) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    addRoundRect(
                                        androidx.compose.ui.geometry.RoundRect(
                                            0f, 0f, size.width, size.height,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                        )
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = primaryColor.copy(alpha = 0.5f),
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                            floatArrayOf(8.dp.toPx(), 6.dp.toPx())
                                        )
                                    )
                                )
                            }
                            // Plus icon in center
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .offset(x = (-2).dp, y = 4.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                WrapIcon(
                                    iconData = AppIcons.Add,
                                    customTint = primaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Title - understated
                        Text(
                            text = stringResource(R.string.dashboard_home_screen_no_credentials_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            ),
                            color = onSurfaceColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description - muted
                        Text(
                            text = stringResource(R.string.dashboard_home_screen_no_credentials),
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Text link CTA - minimal, no button chrome
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, color = primaryColor)
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onAddCredentialClick()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_quick_action_add_credential),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = primaryColor
                            )
                            WrapIcon(
                                iconData = AppIcons.KeyboardArrowRight,
                                customTint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Display a category of credentials
 */
@Composable
private fun CredentialCategory(
    category: DocumentCategory,
    documents: List<DocumentUi>,
    onCredentialClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Category header with icon badge
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

        // Compact credential cards
        documents.forEachIndexed { index, document ->
            val status = when (document.documentIssuanceState) {
                DocumentIssuanceStateUi.Issued -> CredentialStatus.ISSUED
                DocumentIssuanceStateUi.Pending -> CredentialStatus.PENDING
                DocumentIssuanceStateUi.Failed -> CredentialStatus.PENDING
                DocumentIssuanceStateUi.Expired -> CredentialStatus.EXPIRED
                DocumentIssuanceStateUi.Revoked -> CredentialStatus.REVOKED
            }

            val visualType: CredentialVisualType = resolveCredentialVisualType(
                documentIdentifier = document.documentIdentifier,
                documentCategory = category,
                issuerName = document.uiData.overlineText
            )

            val title = when (val content = document.uiData.mainContentData) {
                is ListItemMainContentDataUi.Text -> content.text
                is ListItemMainContentDataUi.Image -> document.uiData.itemId
            }

            CompactCredentialCard(
                config = CompactCredentialConfig(
                    id = document.uiData.itemId,
                    visualType = visualType,
                    title = title,
                    issuerName = document.uiData.overlineText,
                    status = status,
                    expiryDate = document.uiData.supportingText
                        ?.removePrefix("Valid until: "),
                    hasPhoto = !document.portraitBase64.isNullOrBlank(),
                    portraitBase64 = document.portraitBase64
                ),
                animationDelay = index * 60,
                onClick = {
                    onCredentialClick(document.uiData.itemId)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemeModePreviews
@Composable
private fun HomeScreenContentPreview() {
    PreviewTheme {
        ContentScreen(
            isLoading = false,
            navigatableAction = ScreenNavigateAction.NONE,
            onBack = { },
            topBar = {
                TopBar(
                    notificationCount = 0,
                    onNotificationsClick = {},
                    onEventSent = {}
                )
            }
        ) { paddingValues ->
            // Create example credentials for preview
            val exampleCredentials = listOf(
                DocumentCategory.Government to listOf(
                    DocumentUi(
                        documentIssuanceState = DocumentIssuanceStateUi.Issued,
                        uiData = ListItemDataUi(
                            itemId = "id1",
                            mainContentData = ListItemMainContentDataUi.Text(text = "National ID Card"),
                            overlineText = "Government Authority",
                            supportingText = "Valid until: 12/12/2025",
                            leadingContentData = ListItemLeadingContentDataUi.Icon(
                                iconData = AppIcons.Documents
                            )
                        ),
                        documentIdentifier = DocumentIdentifier.MdocPid,
                        documentCategory = DocumentCategory.Government
                    ),
                    DocumentUi(
                        documentIssuanceState = DocumentIssuanceStateUi.Issued,
                        uiData = ListItemDataUi(
                            itemId = "id2",
                            mainContentData = ListItemMainContentDataUi.Text(text = "Driver's License"),
                            overlineText = "National Transport Authority",
                            supportingText = "Valid until: 10/04/2026",
                            leadingContentData = ListItemLeadingContentDataUi.Icon(
                                iconData = AppIcons.Documents
                            )
                        ),
                        documentIdentifier = DocumentIdentifier.MdocPid,
                        documentCategory = DocumentCategory.Government
                    )
                )
            )

            Content(
                state = State(
                    isBottomSheetOpen = false,
                    welcomeUserMessage = "Welcome back, Alex",
                    authenticateCardConfig = ActionCardConfig(
                        title = stringResource(R.string.home_screen_authentication_card_title),
                        icon = AppIcons.WalletActivated,
                        primaryButtonText = stringResource(R.string.home_screen_authenticate),
                        secondaryButtonText = stringResource(R.string.home_screen_learn_more),
                    ),
                    signCardConfig = ActionCardConfig(
                        title = stringResource(R.string.home_screen_sign_card_title),
                        icon = AppIcons.Contract,
                        primaryButtonText = stringResource(R.string.home_screen_sign),
                        secondaryButtonText = stringResource(R.string.home_screen_learn_more),
                    ),
                    credentials = exampleCredentials,
                    isLoadingCredentials = false,
                    showEmptyCredentialsMessage = false
                ),
                effectFlow = Channel<Effect>().receiveAsFlow(),
                onNavigationRequested = {},
                coroutineScope = rememberCoroutineScope(),
                modalBottomSheetState = rememberModalBottomSheetState(),
                onEventSent = {},
                paddingValues = paddingValues,
            )
        }
    }
}
