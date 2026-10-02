package ai.openclaw.app.ui

import ai.openclaw.app.BuildConfig
import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.GatewayChannelsSummary
import ai.openclaw.app.GatewayConnectionDisplay
import ai.openclaw.app.GatewayConnectionProblem
import ai.openclaw.app.GatewayDreamingSummary
import ai.openclaw.app.GatewayNodeApprovalState
import ai.openclaw.app.GatewayNodesDevicesSummary
import ai.openclaw.app.GatewaySkillSummary
import ai.openclaw.app.GatewaySkillWorkshopSummary
import ai.openclaw.app.GatewaySummaryState
import ai.openclaw.app.HomeDestination
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.currentAppLanguage
import ai.openclaw.app.firstGraphemeOrNull
import ai.openclaw.app.gateway.GatewayMethod
import ai.openclaw.app.i18n.NativeText
import ai.openclaw.app.i18n.joinedNativeText
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.i18n.nativeText
import ai.openclaw.app.i18n.resolveNativeTextResource
import ai.openclaw.app.i18n.verbatimText
import ai.openclaw.app.systemagent.SystemAgentChatAccess
import ai.openclaw.app.ui.design.AgentAvatarSource
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawDesignTheme
import ai.openclaw.app.ui.design.ClawEmptyState
import ai.openclaw.app.ui.design.ClawListItem
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSeparatedColumn
import ai.openclaw.app.ui.design.ClawStatus
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import ai.openclaw.app.ui.design.OpenClawMascot
import ai.openclaw.app.ui.design.PartnerAvatar
import ai.openclaw.app.ui.design.agentAvatarSource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.DisplayFeature
import kotlinx.coroutines.launch
import java.util.Locale

internal enum class Tab {
  Overview,
  Chat,
  Sessions,
  Settings,
  ProvidersModels,
  Files,
  Dashboard,
}

private val shellContentInsets: WindowInsets
  @Composable get() = WindowInsets.safeDrawing

private val overviewMetricTileMinHeight = 96.dp
private val overviewTalkPanelMinHeight = 72.dp
private val overviewListRowMinHeight = 54.dp
private const val overviewRecentSessionLimit = 50
private const val overviewRecentSessionVisibleLimit = 3

/** Main post-onboarding shell that owns top-level Android navigation state. */
@Composable
fun ShellScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier,
  features: List<DisplayFeature> = emptyList(),
) {
  val appearanceThemeMode by viewModel.appearanceThemeMode.collectAsState()
  val appearanceThemeFamily by viewModel.appearanceThemeFamily.collectAsState()
  val appearanceAccentArgb by viewModel.appearanceAccentArgb.collectAsState()
  val gatewayAccentArgb by viewModel.gatewayAccentArgb.collectAsState()
  val shellDark = appearanceThemeMode.isDark(systemDark = isSystemInDarkTheme())
  OpenClawSystemBarAppearance(lightAppearance = !shellDark)
  ClawDesignTheme(dark = shellDark, family = appearanceThemeFamily, accentArgb = appearanceAccentArgb ?: gatewayAccentArgb) {
    val nav = rememberSaveable(saver = ShellNavigation.Saver) { ShellNavigation() }
    val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var commandOpen by rememberSaveable { mutableStateOf(false) }
    var conversationScreenWasActive by rememberSaveable { mutableStateOf(false) }
    val pendingTrust by viewModel.pendingGatewayTrust.collectAsState()
    FoldAwareContent(
      features = features,
      modifier = modifier.background(ClawTheme.colors.canvas),
      bookPanesEnabled = !commandOpen && pendingTrust == null,
      tabletopEnabled = nav.activeTab == Tab.Chat && !commandOpen && pendingTrust == null,
    ) { foldBounds ->
      val bookPanes = foldBounds.book
      val permanentSidebar = bookPanes != null
      // Mode changes discard modal operations and their drag state, not the two content slots.
      val sidebarDrawerState = key(permanentSidebar) { rememberDrawerState(initialValue = DrawerValue.Closed) }
      var sidebarRowDragging by remember(sidebarDrawerState) { mutableStateOf(false) }
      val drawerScope = key(sidebarDrawerState) { rememberCoroutineScope() }
      val openSidebar: () -> Unit = {
        if (!permanentSidebar) drawerScope.launch { sidebarDrawerState.open() }
      }
      val closeSidebar: () -> Unit = {
        if (!permanentSidebar) drawerScope.launch { sidebarDrawerState.close() }
      }
      val requestedHomeDestination by viewModel.requestedHomeDestination.collectAsState()
      val runtimeInitialized by viewModel.runtimeInitialized.collectAsState()
      val gatewayAgents by viewModel.gatewayAgents.collectAsState()
      val gatewayDefaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
      val chatSessionOwnerAgentId by viewModel.chatSessionOwnerAgentId.collectAsState()
      val chatSessions by viewModel.chatSessions.collectAsState()
      val chatSessionKey by viewModel.chatSessionKey.collectAsState()
      val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()

      LaunchedEffect(requestedHomeDestination) {
        val destination = requestedHomeDestination ?: return@LaunchedEffect
        // HomeDestination is a one-shot command from launch intents and settings
        // actions; consume it after translating to local shell state.
        nav.selectTab(
          when (destination) {
            HomeDestination.Connect -> Tab.Overview
            HomeDestination.Chat -> Tab.Chat
            HomeDestination.Voice -> Tab.Chat
            HomeDestination.Settings -> Tab.Settings
          },
        )
        // Screenshot scenes can target a settings detail route alongside the tab.
        viewModel.requestedSettingsRoute.value?.let { route ->
          nav.openSettingsRoute(route)
          viewModel.clearRequestedSettingsRoute()
        }
        closeSidebar()
        viewModel.clearRequestedHomeDestination()
      }

      LaunchedEffect(nav.activeTab, runtimeInitialized) {
        val conversationScreenActive = nav.activeTab == Tab.Chat
        if (conversationScreenActive || conversationScreenWasActive || runtimeInitialized) {
          viewModel.setVoiceScreenActive(conversationScreenActive)
        }
        conversationScreenWasActive = conversationScreenActive
      }

      BackHandler(
        enabled =
          (
            permanentSidebar ||
              (
                sidebarDrawerState.currentValue == DrawerValue.Closed &&
                  sidebarDrawerState.targetValue == DrawerValue.Closed
              )
          ) &&
            nav.activeTab != Tab.Overview,
      ) {
        nav.back()
      }

      BackHandler(enabled = commandOpen) {
        commandOpen = false
      }

      LaunchedEffect(commandOpen, pendingTrust, sidebarDrawerState) {
        if (commandOpen || pendingTrust != null) closeSidebar()
      }

      val activeSidebarDestination =
        when {
          nav.activeTab == Tab.Settings && nav.settingsRoute != SettingsRoute.Skills -> SidebarDestination.Settings
          nav.activeTab == Tab.Overview -> SidebarDestination.Work
          nav.activeTab == Tab.Chat -> SidebarDestination.Home
          nav.activeTab == Tab.Settings && nav.settingsRoute == SettingsRoute.Skills -> SidebarDestination.Skills
          nav.activeTab == Tab.Sessions -> SidebarDestination.Threads
          else -> null
        }
      val selectSidebarDestination: (SidebarDestination) -> Unit = { destination ->
        when (destination) {
          SidebarDestination.Settings -> nav.openSettingsRoute(SettingsRoute.Home)
          SidebarDestination.Work -> nav.selectTab(Tab.Overview)
          SidebarDestination.Home -> nav.selectTab(Tab.Chat)
          SidebarDestination.Skills -> nav.openSettingsRoute(SettingsRoute.Skills)
          SidebarDestination.Threads -> nav.selectTab(Tab.Sessions)
        }
        closeSidebar()
      }

      AdaptiveShellScaffold(
        nav = nav,
        hasBookPanes = bookPanes != null,
        modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas),
      ) { isCompact ->
        // Compact (スマホ) ではボトムバーがナビゲーションを担うため、
        // サイドバーのハンバーガーボタンとスワイプジェスチャーを無効化する
        val showSidebarButton = !permanentSidebar && !isCompact
        val sidebarGesturesEnabled = !sidebarRowDragging && !isCompact

        SidebarNavigationShell(
          drawerState = sidebarDrawerState,
          bookPanes = bookPanes,
          sidebarBand = foldBounds.sidebarBand,
          gesturesEnabled = sidebarGesturesEnabled,
          drawerContent = {
            OpenClawSidebar(
              viewModel = viewModel,
              rowHostBand = foldBounds.sidebarBand,
              agents = gatewayAgents,
              selectedAgentId = chatSessionOwnerAgentId ?: gatewayDefaultAgentId,
              sessions = chatSessions,
              activeSessionKey = chatSessionKey,
              activeDestination = activeSidebarDestination,
              connection = gatewayConnectionDisplay,
              visible =
                !commandOpen && pendingTrust == null &&
                  (permanentSidebar || sidebarDrawerState.isOpen || sidebarDrawerState.targetValue == DrawerValue.Open),
              showCloseButton = !permanentSidebar,
              onClose = closeSidebar,
              onDragActiveChange = { sidebarRowDragging = it },
              onNewSession = {
                viewModel.startNewChat(worktree = false)
                nav.selectTab(Tab.Chat)
                closeSidebar()
              },
              onSelectAgent = { agentId ->
                viewModel.selectChatAgent(agentId)
                nav.selectTab(Tab.Chat)
                closeSidebar()
              },
              onSelectSession = { session ->
                viewModel.switchChatSession(session.key, session.ownerAgentId)
                nav.selectTab(Tab.Chat)
                closeSidebar()
              },
              onSelectCatalogSession = { session ->
                viewModel.continueSessionCatalogEntry(session) { continued ->
                  if (continued) {
                    nav.selectTab(Tab.Chat)
                    closeSidebar()
                  }
                }
              },
              onCreateCatalogSession = { catalogId ->
                nav.selectTab(Tab.Chat)
                closeSidebar()
                viewModel.createSessionCatalogEntry(catalogId)
              },
              onSelectDestination = selectSidebarDestination,
            )
          },
        ) {
          when (nav.activeTab) {
            Tab.Overview -> {
              OverviewScreen(
                viewModel = viewModel,
                showSidebarButton = showSidebarButton,
                onOpenSidebar = openSidebar,
                onSelectTab = nav::selectTab,
                onOpenSettingsRoute = nav::openSettingsRoute,
                onOpenCommand = { commandOpen = true },
              )
            }

            Tab.Chat -> {
              UnifiedChatShellScreen(
                viewModel = viewModel,
                tabletopPanes = foldBounds.tabletop,
                features = features,
                showSidebarButton = showSidebarButton,
                onOpenSidebar = openSidebar,
                onOpenDashboard = nav::openSessionDashboard,
                onOpenGatewaySettings = { nav.openSettingsRoute(SettingsRoute.Gateway) },
                onOpenProvidersModels = { nav.openDetailTab(Tab.ProvidersModels) },
              )
            }

            Tab.ProvidersModels -> {
              ProvidersModelsScreen(
                viewModel = viewModel,
                onBack = nav::back,
              )
            }

            Tab.Sessions -> {
              SessionsScreen(
                viewModel = viewModel,
                showSidebarButton = showSidebarButton,
                onOpenSidebar = openSidebar,
                onOpenChat = { nav.selectTab(Tab.Chat) },
              )
            }

            Tab.Files -> {
              WorkspaceFilesScreen(
                viewModel = viewModel,
                onBack = nav::back,
              )
            }

            Tab.Dashboard -> {
              SessionDashboardScreen(
                viewModel = viewModel,
                sessionKey = nav.dashboardSessionKey,
                onBack = nav::back,
              )
            }

            Tab.Settings -> {
              SettingsShellScreen(
                viewModel = viewModel,
                route = nav.settingsRoute,
                showSidebarButton = showSidebarButton,
                onOpenSidebar = openSidebar,
                onRouteChange = nav::openSettingsRouteFromHome,
                onBack = nav::back,
                onOpenCommand = { commandOpen = true },
                settingsListState = settingsListState,
              )
            }
          }
        }

        if (commandOpen) {
          CommandPalette(
            viewModel = viewModel,
            onDismiss = { commandOpen = false },
            onOpen = { action ->
              when (action) {
                CommandAction.Chat, CommandAction.Voice -> {
                  nav.selectTab(Tab.Chat)
                }

                CommandAction.Sessions -> {
                  nav.openDetailTab(Tab.Sessions)
                }

                is CommandAction.Settings -> {
                  when {
                    // Keep the provider command on its standalone screen; entering
                    // Settings detail would also start the Home summary refreshes.
                    action.route == SettingsRoute.ProvidersModels -> nav.openDetailTab(Tab.ProvidersModels)

                    action.route != SettingsRoute.Home && nav.activeTab == Tab.Settings && nav.settingsRoute == SettingsRoute.Home -> nav.openSettingsRouteFromHome(action.route)

                    else -> nav.openSettingsRoute(action.route)
                  }
                }
              }
              commandOpen = false
            },
            onOpenSession = { sessionKey, ownerAgentId ->
              viewModel.switchChatSession(sessionKey, ownerAgentId)
              nav.selectTab(Tab.Chat)
              commandOpen = false
            },
          )
        }

        pendingTrust?.let { prompt ->
          // Gateway certificate trust is modal across the shell so navigation
          // cannot hide a changed TLS identity prompt.
          GatewayTrustDialog(
            prompt = prompt,
            confirmLabel = stringResource(R.string.trust_and_continue),
            cancelLabel = stringResource(R.string.cancel),
            onAccept = viewModel::acceptGatewayTrustPrompt,
            onUseSystemTrust = viewModel::useSystemGatewayTrustPrompt,
            onDecline = viewModel::declineGatewayTrustPrompt,
          )
        }
      }
    }
  }
}

@Composable
private fun OverviewScreen(
  viewModel: MainViewModel,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onSelectTab: (Tab) -> Unit,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
  onOpenCommand: () -> Unit,
) {
  val sessions by viewModel.chatSessions.collectAsState()
  val pendingRunCount by viewModel.pendingRunCount.collectAsState()
  val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()
  val isConnected = gatewayConnectionDisplay.isConnected
  val models by viewModel.providerModelCatalog.collectAsState()
  val providers by viewModel.modelAuthProviders.collectAsState()
  val approvalInbox by viewModel.execApprovalInbox.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val cronStatus by viewModel.cronStatus.collectAsState()
  val nodesDevicesSummary by viewModel.nodesDevicesSummary.collectAsState()
  val channelsState by viewModel.channelsState.collectAsState()
  val channelsSummary = channelsState.summary
  val agents by viewModel.gatewayAgents.collectAsState()
  val defaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
  val providerRows = providerRows(providers = providers, models = models)
  val readyProviderCount = providerRows.count { it.ready }
  val unknownProviderCount = providerRows.count { it.availability == ProviderAvailability.Unknown }
  val pendingApprovalsCount = approvalInbox.approvals.size + pendingToolCalls.size
  val attentionRows =
    homeAttentionRows(
      isConnected = isConnected,
      pendingApprovals = pendingApprovalsCount,
      channelsSummary = channelsSummary,
      nodesDevicesSummary = nodesDevicesSummary,
      readyProviderCount = readyProviderCount,
      unknownProviderCount = unknownProviderCount,
    )
  val secondaryAttentionRows =
    if (nodesDevicesSummary.hasNodeCapabilityApprovalPending()) {
      attentionRows.filterNot { it.settingsRoute == SettingsRoute.NodesDevices }
    } else {
      attentionRows
    }
  val isLocalLlm by viewModel.isOnDeviceLlmActive.collectAsState()
  val headerState = overviewHeaderState(isConnected = isConnected, hasAttention = attentionRows.isNotEmpty(), isLocalLlm = isLocalLlm)
  val headerRoute = overviewHeaderRoute(attentionRows)
  val activeAgentName = overviewAgentName(agents = agents, defaultAgentId = defaultAgentId)
  val activeAgentBadge = overviewAgentBadgeText(agents = agents, defaultAgentId = defaultAgentId)
  val activeAgentAvatar = overviewAgentAvatar(agents = agents, defaultAgentId = defaultAgentId)
  val overviewSessions = overviewRecentSessions(sessions)
  val overviewSessionCount = overviewSessions.size
  val candidateRecentRows =
    remember(overviewSessions, channelsSummary) {
      overviewRecentSessionRows(sessions = overviewSessions, channelsSummary = channelsSummary)
    }
  var recentRows by remember { mutableStateOf<List<RecentSessionListItem>>(emptyList()) }
  val visibleRecentRows = recentRows.ifEmpty { candidateRecentRows }
  val metricCards =
    overviewMetricCardSpecs(
      isConnected = isConnected,
      hasAttention = attentionRows.isNotEmpty(),
      nodesDevicesSummary = nodesDevicesSummary,
      pendingApprovals = pendingApprovalsCount,
      sessionCount = overviewSessionCount,
    )

  LaunchedEffect(candidateRecentRows) {
    recentRows =
      stableOverviewRecentRows(
        previousRows = recentRows,
        candidateRows = candidateRecentRows,
      )
  }

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshChatSessions(limit = 20)
      viewModel.refreshAgents()
      viewModel.refreshModelCatalog()
      viewModel.refreshProviderModels()
      viewModel.refreshCronJobs()
      viewModel.refreshNodesDevices()
      viewModel.refreshChannels()
      viewModel.refreshExecApprovals()
    }
  }

  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp),
    contentWindowInsets = shellContentInsets,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
        item {
          OverviewHeader(
            status = headerState,
            showSidebarButton = showSidebarButton,
            onOpenSidebar = onOpenSidebar,
            onOpenStatus = { onOpenSettingsRoute(headerRoute) },
            onOpenCommand = onOpenCommand,
          )
        }

        item {
          Text(
            text = nativeString("Overview"),
            style = ClawTheme.type.display.copy(fontSize = 24.sp, lineHeight = 28.sp),
            color = ClawTheme.colors.text,
          )
        }

        item {
          PcHealthCard(viewModel = viewModel, onOpen = { onOpenSettingsRoute(SettingsRoute.PcHealth) })
        }

        item {
          QuickPartnerChatPanel(
            viewModel = viewModel,
            onOpenPartnerChat = { partnerId ->
              viewModel.selectPartnerForChat(partnerId)
              onOpenSettingsRoute(SettingsRoute.PartnerChat)
            },
            onOpenPartnerManagement = {
              onOpenSettingsRoute(SettingsRoute.PartnerManagement)
            },
          )
        }

        item {
          OverviewModelEngineControlPanel(
            viewModel = viewModel,
            onOpenChat = { onSelectTab(Tab.Chat) },
          )
        }

        item {
          OverviewPrimaryPanel(
            agentName = activeAgentName,
            agentBadge = activeAgentBadge,
            agentAvatarSource = activeAgentAvatar,
            statusText = gatewaySummary(gatewayConnectionDisplay),
            isConnected = gatewayConnectionDisplay.isConnected,
            pendingRunCount = pendingRunCount,
            sessionCount = overviewSessionCount,
            cronJobCount = cronStatus.jobs,
            onOpenChat = { onSelectTab(Tab.Chat) },
            onOpenVoice = { onSelectTab(Tab.Chat) },
            onOpenAgent = { onOpenSettingsRoute(SettingsRoute.Agents) },
            onOpenGateway = { onOpenSettingsRoute(SettingsRoute.Gateway) },
          )
        }

        item {
          OverviewMetricGrid(
            cards = metricCards,
            onOpen = { card ->
              val route = card.settingsRoute
              if (route == null) {
                onSelectTab(card.tab)
              } else {
                onOpenSettingsRoute(route)
              }
            },
          )
        }

        item {
          TalkEntryPanel(onOpenVoice = { onSelectTab(Tab.Chat) }, onOpenVoiceSettings = { onOpenSettingsRoute(SettingsRoute.Voice) })
        }

        item { RecentSessionsHeader(onOpenSessions = { onSelectTab(Tab.Sessions) }) }

        if (visibleRecentRows.isEmpty()) {
          item {
            ClawEmptyState(
              title = nativeString("No recent threads"),
              body = nativeString("Start a chat and your active OpenClaw conversations will appear here."),
              action = { ClawPrimaryButton(text = nativeString("Start Chat"), onClick = { onSelectTab(Tab.Chat) }) },
            )
          }
        } else {
          item {
            RecentSessionList(
              rows = visibleRecentRows,
              onOpen = { sessionKey, ownerAgentId ->
                viewModel.switchChatSession(sessionKey, ownerAgentId)
                onSelectTab(Tab.Chat)
              },
            )
          }
        }

        if (secondaryAttentionRows.isNotEmpty()) {
          item {
            HomeAttentionPanel(rows = secondaryAttentionRows, onSelectTab = onSelectTab, onOpenSettingsRoute = onOpenSettingsRoute)
          }
        }
      }
    }
  }
}

@Composable
private fun OverviewHeader(
  status: OverviewHeaderState,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onOpenStatus: () -> Unit,
  onOpenCommand: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (showSidebarButton) {
      ClawPlainIconButton(
        icon = Icons.Default.Menu,
        contentDescription = nativeString("Show Sidebar"),
        onClick = onOpenSidebar,
        modifier = Modifier.testTag("sidebar-open-overview"),
      )
    }
    OpenClawMascot(modifier = Modifier.size(25.dp))
    Text(
      text = nativeString("OpenClaw"),
      style = ClawTheme.type.title.copy(fontSize = 17.sp, lineHeight = 21.sp),
      color = ClawTheme.colors.text,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    OverviewStatusPill(status = status, onClick = onOpenStatus)
    ClawPlainIconButton(icon = Icons.Default.Search, contentDescription = nativeString("Search"), onClick = onOpenCommand)
  }
}

@Composable
private fun OverviewStatusPill(
  status: OverviewHeaderState,
  onClick: () -> Unit,
) {
  val colors = ClawTheme.colors
  val (dotColor, backgroundColor) =
    when (status.status) {
      ClawStatus.Success -> colors.success to colors.successSoft
      ClawStatus.Warning -> colors.warning to colors.warningSoft
      ClawStatus.Danger -> colors.danger to colors.dangerSoft
      ClawStatus.Neutral -> colors.textSubtle to colors.surfaceRaised
    }
  Surface(
    onClick = onClick,
    modifier = Modifier.heightIn(min = ClawTheme.spacing.touchTarget),
    shape = RoundedCornerShape(ClawTheme.radii.control),
    color = backgroundColor.copy(alpha = 0.82f),
    border = BorderStroke(1.dp, ClawTheme.colors.border.copy(alpha = 0.32f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
      Text(text = nativeString(status.label), style = ClawTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp), color = ClawTheme.colors.text, maxLines = 1)
      Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp), tint = ClawTheme.colors.textMuted)
    }
  }
}

@Composable
private fun OverviewPrimaryPanel(
  agentName: String,
  agentBadge: String,
  agentAvatarSource: AgentAvatarSource?,
  statusText: String,
  isConnected: Boolean,
  pendingRunCount: Int,
  sessionCount: Int,
  cronJobCount: Int,
  onOpenChat: () -> Unit,
  onOpenVoice: () -> Unit,
  onOpenAgent: () -> Unit,
  onOpenGateway: () -> Unit,
) {
  OverviewLayeredPanel(contentPadding = PaddingValues(ClawTheme.spacing.sm), elevated = true) {
    Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs)) {
      Text(text = nativeString("ACTIVE AGENT"), style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 15.sp), color = ClawTheme.colors.textMuted)
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        OverviewAgentBadge(text = agentBadge, active = isConnected, avatarSource = agentAvatarSource)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(text = if (pendingRunCount > 0) nativeString("\$agentName is working", agentName) else agentName, style = ClawTheme.type.title.copy(fontSize = 19.sp, lineHeight = 23.sp), color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
          }
          Text(text = overviewAgentActivityText(isConnected = isConnected, pendingRunCount = pendingRunCount, sessionCount = sessionCount, cronJobCount = cronJobCount, statusText = statusText), style = ClawTheme.type.caption.copy(fontSize = 13.5.sp, lineHeight = 17.sp), color = ClawTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ClawSecondaryButton(text = nativeString("View"), onClick = onOpenAgent)
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewStateChip(label = nativeString("Runs"), value = if (pendingRunCount > 0) nativeString("\$pendingRunCount active", pendingRunCount) else nativeString("Idle"), modifier = Modifier.weight(1f))
        OverviewStateChip(label = nativeString("Threads"), value = if (sessionCount == 0) nativeString("None") else nativeString("\$sessionCount recent", sessionCount), modifier = Modifier.weight(1f))
        OverviewStateChip(label = nativeString("Cron"), value = cronJobsSummary(cronJobCount), modifier = Modifier.weight(1f))
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OverviewActionPill(text = nativeString("Chat"), icon = Icons.Outlined.ChatBubbleOutline, emphasized = true, onClick = onOpenChat, modifier = Modifier.weight(1f))
        OverviewActionPill(text = nativeString("Talk"), icon = Icons.Outlined.MicNone, emphasized = false, onClick = onOpenVoice, modifier = Modifier.weight(1f))
      }
      if (!isConnected) {
        ClawSecondaryButton(text = nativeString("Reconnect gateway"), icon = Icons.Default.Cloud, onClick = onOpenGateway, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun OverviewActionPill(
  text: String,
  icon: ImageVector,
  emphasized: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.heightIn(min = ClawTheme.spacing.touchTarget),
    shape = RoundedCornerShape(ClawTheme.radii.control),
    color =
      if (emphasized) {
        ClawTheme.colors.surfacePressed.copy(alpha = 0.9f)
      } else {
        ClawTheme.colors.surfaceRaised.copy(alpha = 0.72f)
      },
    contentColor = ClawTheme.colors.text,
    border =
      if (emphasized) {
        null
      } else {
        BorderStroke(1.dp, ClawTheme.colors.borderStrong.copy(alpha = 0.7f))
      },
    tonalElevation = if (emphasized) 2.dp else 0.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = text, style = ClawTheme.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun OverviewLayeredPanel(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(14.dp),
  elevated: Boolean = false,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(ClawTheme.radii.button),
    color = if (elevated) ClawTheme.colors.surfaceRaised.copy(alpha = 0.98f) else ClawTheme.colors.surfaceRaised.copy(alpha = 0.86f),
    contentColor = ClawTheme.colors.text,
    tonalElevation = if (elevated) 4.dp else 1.dp,
    shadowElevation = if (elevated) 9.dp else 2.dp,
  ) {
    Column(modifier = Modifier.padding(contentPadding)) {
      content()
    }
  }
}

@Composable
private fun OverviewAgentBadge(
  text: String,
  active: Boolean,
  avatarSource: AgentAvatarSource?,
) {
  Surface(
    modifier = Modifier.size(42.dp),
    shape = CircleShape,
    color = if (active) ClawTheme.colors.successSoft else ClawTheme.colors.surfacePressed,
    contentColor = if (active) ClawTheme.colors.success else ClawTheme.colors.textMuted,
    tonalElevation = if (active) 3.dp else 1.dp,
    shadowElevation = if (active) 5.dp else 1.dp,
  ) {
    ClawAgentAvatar(source = avatarSource, size = 42.dp) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = text,
          style = ClawTheme.type.title.copy(fontSize = 16.sp, lineHeight = 20.sp),
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun OverviewStateChip(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.heightIn(min = ClawTheme.spacing.touchTarget),
    shape = RoundedCornerShape(ClawTheme.radii.control),
    color = ClawTheme.colors.surfacePressed.copy(alpha = 0.58f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      Text(text = localizedUppercase(label, currentAppLanguage().languageTag), style = ClawTheme.type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = ClawTheme.colors.textSubtle, maxLines = 1)
      Text(text = value, style = ClawTheme.type.caption.copy(fontSize = 14.sp, lineHeight = 17.sp), color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun OverviewMetricGrid(
  cards: List<OverviewMetricCardSpec>,
  onOpen: (OverviewMetricCardSpec) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    cards.chunked(2).forEach { row ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { card ->
          OverviewMetricTile(card = card, onClick = { onOpen(card) }, modifier = Modifier.weight(1f))
        }
        if (row.size == 1) {
          Box(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun OverviewMetricTile(
  card: OverviewMetricCardSpec,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tint =
    when (card.status) {
      ClawStatus.Success -> ClawTheme.colors.success
      ClawStatus.Warning -> ClawTheme.colors.warning
      ClawStatus.Danger -> ClawTheme.colors.danger
      ClawStatus.Neutral -> ClawTheme.colors.textMuted
    }
  Surface(
    onClick = onClick,
    modifier = modifier.heightIn(min = overviewMetricTileMinHeight),
    shape = RoundedCornerShape(ClawTheme.radii.button),
    color = ClawTheme.colors.surfaceRaised.copy(alpha = 0.84f),
    contentColor = ClawTheme.colors.text,
    tonalElevation = 2.dp,
    shadowElevation = 3.dp,
  ) {
    Column(modifier = Modifier.padding(ClawTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(imageVector = card.icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = tint)
        Text(text = localizedUppercase(card.title, currentAppLanguage().languageTag), style = ClawTheme.type.caption.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = ClawTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nativeString("Open \${card.title}", card.title), modifier = Modifier.size(15.dp), tint = ClawTheme.colors.textMuted)
      }
      Text(text = card.value, style = ClawTheme.type.title.copy(fontSize = 22.sp, lineHeight = 25.sp), color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(text = card.subtitle, style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.textSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
      card.progressFraction?.let { progress ->
        OverviewProgressBar(progress = progress, tint = tint)
      }
    }
  }
}

internal fun localizedUppercase(
  value: String,
  languageTag: String?,
  fallbackLocale: Locale = Locale.getDefault(),
): String = value.uppercase(languageTag?.let(Locale::forLanguageTag) ?: fallbackLocale)

internal fun localizedInitial(
  value: String,
  languageTag: String?,
  fallbackLocale: Locale = Locale.getDefault(),
): String? = value.firstGraphemeOrNull()?.let { localizedUppercase(it, languageTag, fallbackLocale) }

@Composable
private fun OverviewProgressBar(
  progress: Float,
  tint: Color,
) {
  val visualProgress =
    if (progress <= 0f) {
      0f
    } else {
      progress.coerceIn(0.16f, 1f)
    }
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(ClawTheme.colors.surfacePressed),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth(visualProgress)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(tint),
    )
  }
}

@Composable
private fun TalkEntryPanel(
  onOpenVoice: () -> Unit,
  onOpenVoiceSettings: () -> Unit,
) {
  Surface(
    onClick = onOpenVoice,
    modifier = Modifier.fillMaxWidth().heightIn(min = overviewTalkPanelMinHeight),
    shape = RoundedCornerShape(ClawTheme.radii.button),
    color = ClawTheme.colors.surfaceRaised.copy(alpha = 0.9f),
    contentColor = ClawTheme.colors.text,
    tonalElevation = 2.dp,
    shadowElevation = 3.dp,
  ) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color(0xFF1976D2),
        tonalElevation = 2.dp,
        shadowElevation = 5.dp,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(25.dp), tint = Color.White)
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = nativeString("Talk"), style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 15.sp), color = ClawTheme.colors.textMuted)
        Text(text = nativeString("Open Talk"), style = ClawTheme.type.body, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      ClawPlainIconButton(icon = Icons.Default.Tune, contentDescription = nativeString("Talk settings"), onClick = onOpenVoiceSettings)
    }
  }
}

@Composable
private fun RecentSessionsHeader(onOpenSessions: () -> Unit) {
  SectionLabel(
    title = nativeString("Recent Threads"),
    action = {
      Surface(
        onClick = onOpenSessions,
        modifier = Modifier.heightIn(min = ClawTheme.spacing.touchTarget),
        color = Color.Transparent,
        contentColor = ClawTheme.colors.textMuted,
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = nativeString("View all"),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
    },
  )
}

internal data class OverviewHeaderState(
  val label: String,
  val status: ClawStatus,
)

internal fun overviewHeaderState(
  isConnected: Boolean,
  hasAttention: Boolean,
  isLocalLlm: Boolean = false,
): OverviewHeaderState =
  when {
    isLocalLlm -> OverviewHeaderState("ローカル", ClawStatus.Success)
    !isConnected -> OverviewHeaderState("Offline", ClawStatus.Neutral)
    hasAttention -> OverviewHeaderState("Needs attention", ClawStatus.Warning)
    else -> OverviewHeaderState("Online", ClawStatus.Success)
  }

internal fun overviewHeaderRoute(attentionRows: List<HomeAttentionRow>): SettingsRoute = attentionRows.firstNotNullOfOrNull { it.settingsRoute } ?: SettingsRoute.Gateway

internal fun overviewRecentSessionCount(sessions: List<ChatSessionEntry>): Int = overviewRecentSessions(sessions).size

internal fun overviewRecentSessions(sessions: List<ChatSessionEntry>): List<ChatSessionEntry> =
  sessions
    .withIndex()
    .groupBy { entry -> entry.value.key }
    .values
    .map { entries ->
      entries
        .sortedWith(
          compareByDescending<IndexedValue<ChatSessionEntry>> { entry -> entry.value.overviewRecentSessionRecencyMs() }
            .thenBy { entry -> entry.index },
        ).first()
    }.sortedWith(
      compareByDescending<IndexedValue<ChatSessionEntry>> { entry -> entry.value.overviewRecentSessionRecencyMs() }
        .thenBy { entry -> entry.value.key },
    ).take(overviewRecentSessionLimit)
    .map { entry -> entry.value }

private fun ChatSessionEntry.overviewRecentSessionRecencyMs(): Long = lastActivityAt ?: updatedAtMs ?: Long.MIN_VALUE

internal data class OverviewMetricCardSpec(
  val title: String,
  val value: String,
  val subtitle: String,
  val icon: ImageVector,
  val status: ClawStatus,
  val tab: Tab,
  val settingsRoute: SettingsRoute? = null,
  val progressFraction: Float? = null,
)

internal fun overviewMetricCardSpecs(
  isConnected: Boolean,
  hasAttention: Boolean,
  nodesDevicesSummary: GatewayNodesDevicesSummary,
  pendingApprovals: Int,
  sessionCount: Int,
): List<OverviewMetricCardSpec> {
  val onlineNodes = nodesDevicesSummary.nodes.count { it.connected }
  val nodeCount = nodesDevicesSummary.nodes.size
  return listOf(
    OverviewMetricCardSpec(
      title = nativeString("Gateway"),
      value =
        when {
          !isConnected -> nativeString("Offline")
          hasAttention -> nativeString("Online")
          else -> nativeString("Healthy")
        },
      subtitle =
        when {
          !isConnected -> nativeString("Reconnect to continue")
          hasAttention -> nativeString("Review highlighted items")
          else -> nativeString("All systems nominal")
        },
      icon = Icons.Default.Favorite,
      status =
        when {
          !isConnected -> ClawStatus.Neutral
          hasAttention -> ClawStatus.Warning
          else -> ClawStatus.Success
        },
      tab = Tab.Settings,
      settingsRoute = SettingsRoute.Gateway,
    ),
    OverviewMetricCardSpec(
      title = nativeString("Nodes"),
      value = if (nodeCount == 0) nativeString("None") else nativeString("\$onlineNodes/\$nodeCount", onlineNodes, nodeCount),
      subtitle =
        if (nodesDevicesSummary.hasNodeCapabilityApprovalPending()) {
          nativeString("Review node access")
        } else if (nodeCount > 0) {
          nativeString(
            "\${nodeOnlinePercent(onlineNodes = onlineNodes, nodeCount = nodeCount)}% online",
            nodeOnlinePercent(onlineNodes = onlineNodes, nodeCount = nodeCount),
          )
        } else {
          nodesDevicesSummaryText(nodesDevicesSummary)
        },
      icon = Icons.Default.Cloud,
      status =
        when {
          nodesDevicesSummary.pendingDevices.isNotEmpty() || nodesDevicesSummary.hasNodeCapabilityApprovalPending() -> ClawStatus.Warning
          onlineNodes > 0 -> ClawStatus.Success
          else -> ClawStatus.Neutral
        },
      tab = Tab.Settings,
      settingsRoute = SettingsRoute.NodesDevices,
      progressFraction = if (nodeCount > 0) onlineNodes.toFloat() / nodeCount.toFloat() else null,
    ),
    OverviewMetricCardSpec(
      title = nativeString("Approvals"),
      value = pendingApprovals.toString(),
      subtitle = approvalsSummary(pendingApprovals),
      icon = Icons.Default.Security,
      status = if (pendingApprovals > 0) ClawStatus.Warning else ClawStatus.Neutral,
      tab = Tab.Settings,
      settingsRoute = SettingsRoute.Approvals,
    ),
    OverviewMetricCardSpec(
      title = nativeString("Threads"),
      value = sessionCount.toString(),
      subtitle = if (sessionCount == 0) nativeString("No recent threads") else nativeString("Recent conversations"),
      icon = Icons.Default.Groups,
      status = if (sessionCount > 0) ClawStatus.Success else ClawStatus.Neutral,
      tab = Tab.Sessions,
    ),
    OverviewMetricCardSpec(
      title = nativeString("Files"),
      value = if (isConnected) nativeString("Browse") else nativeString("Offline"),
      subtitle = nativeString("Agent workspace files"),
      icon = Icons.Outlined.Folder,
      status = if (isConnected) ClawStatus.Success else ClawStatus.Neutral,
      tab = Tab.Files,
    ),
  )
}

internal fun overviewAgentName(
  agents: List<GatewayAgentSummary>,
  defaultAgentId: String?,
): String {
  val agent = overviewAgent(agents = agents, defaultAgentId = defaultAgentId)
  return agent?.name?.takeIf { it.isNotBlank() } ?: agent?.id?.takeIf { it.isNotBlank() } ?: nativeString("OpenClaw")
}

internal fun overviewAgentBadgeText(
  agents: List<GatewayAgentSummary>,
  defaultAgentId: String?,
): String {
  val agent = overviewAgent(agents = agents, defaultAgentId = defaultAgentId)
  agent
    ?.emoji
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { return it }
  if (agent == null) return "OC"
  val source = agent.name?.takeIf { it.isNotBlank() } ?: agent.id.takeIf { it.isNotBlank() } ?: nativeString("OpenClaw")
  return agentInitials(source)
}

internal fun overviewAgentAvatar(
  agents: List<GatewayAgentSummary>,
  defaultAgentId: String?,
): AgentAvatarSource? = overviewAgent(agents = agents, defaultAgentId = defaultAgentId)?.let(::agentAvatarSource)

private fun overviewAgent(
  agents: List<GatewayAgentSummary>,
  defaultAgentId: String?,
): GatewayAgentSummary? {
  val defaultId = defaultAgentId?.trim().orEmpty()
  return if (defaultId.isBlank()) {
    agents.firstOrNull()
  } else {
    agents.firstOrNull { it.id == defaultId } ?: agents.firstOrNull()
  }
}

internal fun overviewAgentActivityText(
  isConnected: Boolean,
  pendingRunCount: Int,
  sessionCount: Int,
  cronJobCount: Int,
  statusText: String,
): String {
  if (!isConnected) return statusText
  if (pendingRunCount > 0) {
    return if (pendingRunCount == 1) {
      nativeString("Working · 1 active run")
    } else {
      nativeString("Working · \$pendingRunCount active runs", pendingRunCount)
    }
  }
  return when {
    sessionCount == 1 -> nativeString("Monitoring · 1 thread")
    sessionCount > 1 -> nativeString("Monitoring · \$sessionCount threads", sessionCount)
    cronJobCount == 1 -> nativeString("Monitoring · 1 scheduled job")
    cronJobCount > 1 -> nativeString("Monitoring · \$cronJobCount scheduled jobs", cronJobCount)
    else -> statusText
  }
}

internal fun nodeOnlinePercent(
  onlineNodes: Int,
  nodeCount: Int,
): Int =
  if (nodeCount <= 0) {
    0
  } else {
    ((onlineNodes.coerceAtLeast(0) * 100) + (nodeCount / 2)) / nodeCount
  }

private fun agentInitials(name: String): String =
  name
    .split(' ', '-', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { part -> localizedInitial(part, currentAppLanguage().languageTag) }
    .joinToString("")
    .ifBlank { "OC" }

private val sessionSourceLabels =
  mapOf(
    "cron" to "Cron",
    "discord" to "Discord",
    "guildchat" to "Guildchat",
    "imessage" to "iMessage",
    "matrix" to "Matrix",
    "slack" to "Slack",
    "telegram" to "Telegram",
    "whatsapp" to "WhatsApp",
    "workspace" to "Workspace",
  )

internal fun sessionSourceLabel(
  sessionKey: String,
  channelsSummary: GatewayChannelsSummary? = null,
): String {
  val normalized = sessionKey.trim()
  val scopedKey =
    if (normalized.startsWith("agent:", ignoreCase = true)) {
      normalized.substringAfter(':', missingDelimiterValue = "").substringAfter(':', missingDelimiterValue = "")
    } else {
      normalized
    }
  if (!scopedKey.contains(':') && !scopedKey.contains('#')) return nativeString("OpenClaw")
  val source = scopedKey.substringBefore(':').substringBefore('#').lowercase()
  val channelLabel =
    channelsSummary
      ?.channels
      ?.firstOrNull { channel ->
        channel.id.equals(source, ignoreCase = true)
      }?.label
      ?.takeIf { it.isNotBlank() }
  if (channelLabel != null) return channelLabel
  return nativeString(sessionSourceLabels[source] ?: "OpenClaw")
}

internal data class HomeAttentionRow(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val tab: Tab,
  val settingsRoute: SettingsRoute? = null,
)

internal fun homeAttentionRows(
  isConnected: Boolean,
  pendingApprovals: Int,
  channelsSummary: GatewayChannelsSummary?,
  nodesDevicesSummary: GatewayNodesDevicesSummary,
  readyProviderCount: Int,
  unknownProviderCount: Int = 0,
): List<HomeAttentionRow> =
  listOfNotNull(
    if (!isConnected) {
      HomeAttentionRow(
        nativeString("Gateway"),
        nativeString("Connect before chat, voice, and live status."),
        Icons.Default.Cloud,
        Tab.Settings,
        SettingsRoute.Gateway,
      )
    } else {
      null
    },
    if (pendingApprovals > 0) {
      HomeAttentionRow(nativeString("Approvals"), approvalsSummary(pendingApprovals), Icons.Default.Lock, Tab.Settings, SettingsRoute.Approvals)
    } else {
      null
    },
    if (channelsSummary?.channels?.any { it.error != null } == true) {
      HomeAttentionRow(nativeString("Channels"), channelsSummaryText(channelsSummary), Icons.Default.Notifications, Tab.Settings, SettingsRoute.Channels)
    } else {
      null
    },
    if (nodesDevicesSummary.pendingDevices.isNotEmpty() || nodesDevicesSummary.hasNodeCapabilityApprovalPending()) {
      HomeAttentionRow(nativeString("Nodes & Devices"), nodesDevicesSummaryText(nodesDevicesSummary), Icons.Default.Cloud, Tab.Settings, SettingsRoute.NodesDevices)
    } else {
      null
    },
    if (isConnected && readyProviderCount == 0 && unknownProviderCount == 0) {
      HomeAttentionRow(nativeString("Providers"), nativeString("No ready providers"), Icons.Outlined.Inventory2, Tab.Settings, SettingsRoute.ProvidersModels)
    } else {
      null
    },
  )

@Composable
private fun HomeAttentionPanel(
  rows: List<HomeAttentionRow>,
  onSelectTab: (Tab) -> Unit,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
) {
  OverviewLayeredPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text = nativeString("Needs attention"), style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.warning)
      rows.forEach { row ->
        HomeAttentionListRow(
          row = row,
          onClick = {
            val route = row.settingsRoute
            if (route == null) {
              onSelectTab(row.tab)
            } else {
              onOpenSettingsRoute(route)
            }
          },
        )
      }
    }
  }
}

@Composable
private fun SectionLabel(
  title: String,
  action: (@Composable () -> Unit)? = null,
) {
  val localizedTitle = nativeString(title)
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = localizedUppercase(localizedTitle, currentAppLanguage().languageTag),
      style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
      color = ClawTheme.colors.textMuted,
    )
    action?.invoke()
  }
}

@Composable
private fun HomeAttentionListRow(
  row: HomeAttentionRow,
  onClick: () -> Unit,
) {
  val localizedTitle = nativeString(row.title)
  Surface(color = Color.Transparent, contentColor = ClawTheme.colors.text) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 54.dp)
          .clip(RoundedCornerShape(ClawTheme.radii.row))
          .clickable(onClick = onClick)
          .padding(horizontal = 0.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Icon(imageVector = row.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = ClawTheme.colors.text)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = localizedTitle,
          style = ClawTheme.type.body,
          color = ClawTheme.colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(text = nativeString(row.subtitle), style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = settingsRowDisclosureDescription(localizedTitle, opensRoute = true),
        modifier = Modifier.size(17.dp),
        tint = ClawTheme.colors.textMuted,
      )
    }
  }
}

internal data class RecentSessionListItem(
  val key: String,
  val title: String,
  val source: String,
  val metadata: String,
  val ownerAgentId: String? = null,
)

internal fun overviewRecentSessionRows(
  sessions: List<ChatSessionEntry>,
  channelsSummary: GatewayChannelsSummary?,
): List<RecentSessionListItem> =
  sessions
    .take(overviewRecentSessionVisibleLimit)
    .map { session ->
      val title = sessionPresentationTitle(session) { nativeString("Main thread") }
      RecentSessionListItem(
        key = session.key,
        ownerAgentId = session.ownerAgentId,
        title = title,
        source = sessionListSubtitle(session, sessionSourceLabel(session.key, channelsSummary)),
        metadata = (session.lastActivityAt ?: session.updatedAtMs)?.let(::relativeSessionTime) ?: "",
      )
    }

internal fun stableOverviewRecentRows(
  previousRows: List<RecentSessionListItem>,
  candidateRows: List<RecentSessionListItem>,
): List<RecentSessionListItem> {
  val previousRowsByKey = previousRows.associateBy { row -> row.key }
  return candidateRows.map { candidateRow ->
    val previousRow = previousRowsByKey[candidateRow.key]
    if (previousRow == null) candidateRow else candidateRow.withStableFieldsFrom(previousRow)
  }
}

private fun RecentSessionListItem.withStableFieldsFrom(previousRow: RecentSessionListItem): RecentSessionListItem =
  copy(
    title = title.ifBlank { previousRow.title },
    source = source.ifBlank { previousRow.source },
    metadata = metadata.ifBlank { previousRow.metadata },
  )

/** Recent sessions panel that preserves the session key behind display labels. */
@Composable
private fun RecentSessionList(
  rows: List<RecentSessionListItem>,
  onOpen: (String, String?) -> Unit,
) {
  OverviewLayeredPanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
    Column {
      rows.forEachIndexed { index, row ->
        RecentSessionRowContent(
          title = row.title,
          source = row.source,
          metadata = row.metadata,
          onClick = { onOpen(row.key, row.ownerAgentId) },
        )
        if (index != rows.lastIndex) {
          HorizontalDivider(color = ClawTheme.colors.border.copy(alpha = 0.48f), thickness = 1.dp)
        }
      }
    }
  }
}

@Composable
private fun RecentSessionRowContent(
  title: String,
  source: String,
  metadata: String,
  onClick: () -> Unit,
) {
  Surface(color = Color.Transparent, contentColor = ClawTheme.colors.text) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = overviewListRowMinHeight)
          .clip(RoundedCornerShape(ClawTheme.radii.row))
          .clickable(onClick = onClick)
          .padding(horizontal = 0.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = ClawTheme.colors.canvas,
        border = BorderStroke(1.dp, ClawTheme.colors.border.copy(alpha = 0.7f)),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(imageVector = Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = ClawTheme.colors.text)
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = title, style = ClawTheme.type.body, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = source, style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Text(text = metadata, style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.textMuted)
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = nativeString("Open thread"),
        modifier = Modifier.size(14.dp),
        tint = ClawTheme.colors.textMuted,
      )
    }
  }
}

@Composable
private fun SettingsShellScreen(
  viewModel: MainViewModel,
  route: SettingsRoute,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onRouteChange: (SettingsRoute) -> Unit,
  onBack: () -> Unit,
  onOpenCommand: () -> Unit,
  settingsListState: LazyListState,
) {
  val displayName by viewModel.displayName.collectAsState()
  val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()
  val isConnected = gatewayConnectionDisplay.isConnected
  val systemAgentChatState by viewModel.systemAgentChatState.collectAsState()
  val models by viewModel.providerModelCatalog.collectAsState()
  val providers by viewModel.modelAuthProviders.collectAsState()
  val cameraEnabled by viewModel.cameraEnabled.collectAsState()
  val notificationForwardingEnabled by viewModel.notificationForwardingEnabled.collectAsState()
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()
  val agents by viewModel.gatewayAgents.collectAsState()
  val approvalInbox by viewModel.execApprovalInbox.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val cronStatus by viewModel.cronStatus.collectAsState()
  val usageState by viewModel.usageState.collectAsState()
  val usageSummary = usageState.summary
  val skillsState by viewModel.skillsState.collectAsState()
  val skillsSummary = skillsState.summary
  val skillWorkshopSummary by viewModel.skillWorkshopSummary.collectAsState()
  val nodesDevicesSummary by viewModel.nodesDevicesSummary.collectAsState()
  val channelsState by viewModel.channelsState.collectAsState()
  val channelsSummary = channelsState.summary
  val dreamingState by viewModel.dreamingState.collectAsState()
  val dreamingSummary = dreamingState.summary
  val desktopObserveAvailable by viewModel.desktopObserveAvailable.collectAsState()
  val appearanceThemeMode by viewModel.appearanceThemeMode.collectAsState()
  val providerRows = providerRows(providers = providers, models = models)
  val readyProviderCount = providerRows.count { it.ready }
  val unknownProviderCount = providerRows.count { it.availability == ProviderAvailability.Unknown }
  val pendingApprovalsCount = approvalInbox.approvals.size + pendingToolCalls.size

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshAgents()
      viewModel.refreshModelCatalog()
      viewModel.refreshProviderModels()
      viewModel.refreshCronJobs()
      viewModel.refreshUsage()
      viewModel.refreshSkills()
      viewModel.refreshSkillWorkshopProposals()
      viewModel.refreshNodesDevices()
      viewModel.refreshChannels()
      viewModel.refreshDreaming()
      viewModel.refreshExecApprovals()
    }
  }

  // System Back for settings routes is owned by the shell-level BackHandler, which
  // unwinds cross-tab opens to their originating tab via ShellNavigation. A local
  // BackHandler here would shadow it and strand cross-tab opens on Settings Home.
  val appLanguage = currentAppLanguage()

  @Composable
  fun SettingsHomeList(selectedRoute: SettingsRoute?) {
  ClawScaffold(
    contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 4.dp),
    contentWindowInsets = shellContentInsets,
  ) {
    LazyColumn(
      state = settingsListState,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(9.dp),
      contentPadding = PaddingValues(bottom = 4.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
          if (showSidebarButton) {
            ClawPlainIconButton(
              icon = Icons.Default.Menu,
              contentDescription = nativeString("Show Sidebar"),
              onClick = onOpenSidebar,
              modifier = Modifier.testTag("sidebar-open-settings"),
            )
          }
          Text(text = nativeString("Settings"), style = ClawTheme.type.display.copy(fontSize = 24.sp, lineHeight = 28.sp), color = ClawTheme.colors.text, modifier = Modifier.weight(1f))
          ClawPlainIconButton(
            icon = Icons.Default.Search,
            contentDescription = nativeString("Search settings"),
            onClick = onOpenCommand,
          )
        }
      }

      item {
        ProfilePanel(displayName = displayName.ifBlank { "OpenClaw" }, onClick = { onRouteChange(SettingsRoute.Profile) })
      }

      item {
        SettingsQuickTiles(onOpen = onRouteChange)
      }

      val settingsRows =
        listOf(
          // ── 🤖 AIパートナー ──
          SettingsRow(
            SettingsRoute.PartnerManagement,
            nativeText("作成・ペルソナ・画像"),
            status = true,
          ),
          SettingsRow(
            SettingsRoute.CoreAgentSettings,
            nativeText("人格・口調・SOUL"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.GroupChat,
            nativeText("複数パートナーで会話"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.VoiceStudio,
            nativeText("録音からオリジナルの声を作成"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.AvatarCustomize,
            nativeText("見た目・カラー変更"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.PartnerBond,
            nativeText("レベル・バッジ"),
            status = isConnected,
          ),

          // ── 🧠 知識・記憶 ──
          SettingsRow(
            SettingsRoute.MemoryInspector,
            nativeText("長期・短期・RAG"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.PartnerMemory,
            nativeText("事実・学習データ"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.FeatureSettings,
            nativeText("検索プロバイダ・防災"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.PartnerDream,
            nativeText("夜空の記録"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.MoodDashboard,
            nativeText("心の推移グラフ"),
            status = isConnected,
          ),

          // ── 🎮 生活・エンタメ ──
          SettingsRow(
            SettingsRoute.DailySettings,
            nativeText("毎朝の挨拶・運勢"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.Diary,
            nativeText("AI代筆"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.PartnerAlbum,
            nativeText("写真を見せて感想をもらう"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.MiniGame,
            nativeText("しりとり・クイズ・創作"),
            status = isConnected,
          ),

          // ── ⚙️ モデル・自動化 ──
          SettingsRow(
            SettingsRoute.OllamaCatalog,
            nativeText("DL・切替・HF検索"),
            status = isConnected,
          ),
          SettingsRow(
            SettingsRoute.OnDeviceGguf,
            nativeText("スマホ単体で推論"),
            status = true,
          ),
          SettingsRow(SettingsRoute.Agents, if (agents.isEmpty()) nativeText("Load from gateway") else nativeText("${agents.size} available", agents.size), status = agents.isNotEmpty()),
          SettingsRow(
            SettingsRoute.ProvidersModels,
            when {
              readyProviderCount > 0 -> nativeText("\$readyProviderCount ready", readyProviderCount)
              unknownProviderCount > 0 -> nativeText("Availability unknown")
              else -> nativeText("Review readiness")
            },
            status =
              when {
                !isConnected -> false
                readyProviderCount > 0 -> true
                unknownProviderCount > 0 -> null
                else -> false
              },
          ),
          SettingsRow(SettingsRoute.SpecialistJobs, nativeText("裏で動く小型モデルと作業の状況"), status = isConnected),
          SettingsRow(SettingsRoute.Approvals, verbatimText(approvalsSummary(pendingApprovalsCount)), status = approvalsStatus(pendingApprovalsCount)),
          SettingsRow(SettingsRoute.CronJobs, verbatimText(cronJobsSummary(cronStatus.jobs)), status = if (cronStatus.jobs > 0) cronStatus.enabled else null),
          SettingsRow(SettingsRoute.Usage, usageState.summaryText { usageSummaryText(it.providers.size) }, status = if (usageState.errorText != null) false else true.takeIf { usageSummary?.providers?.isNotEmpty() == true }),
          SettingsRow(SettingsRoute.Skills, skillsState.summaryText { skillsSummaryText(it.skills) }, status = if (skillsState.errorText != null) false else skillsSummary?.skills?.let(::skillsStatus)),
          SettingsRow(
            SettingsRoute.SkillWorkshop,
            verbatimText(skillWorkshopSummaryText(skillWorkshopSummary)),
            status = skillWorkshopStatus(skillWorkshopSummary),
          ),
          SettingsRow(
            SettingsRoute.SystemAgent,
            nativeText("Setup, status, and repair"),
            status =
              when (systemAgentChatState.access) {
                SystemAgentChatAccess.Ready -> true
                SystemAgentChatAccess.CheckingGateway -> null
                else -> false
              },
          ),
          SettingsRow(SettingsRoute.Dreaming, dreamingState.summaryText(::dreamingSummaryText), status = if (dreamingState.errorText != null) false else dreamingSummary?.let(::dreamingStatus)),
          SettingsRow(SettingsRoute.Terminal, nativeText("Shell in the agent workspace"), status = isConnected),
          SettingsRow(SettingsRoute.Desktop, nativeText("View a machine screen"), status = isConnected),

          // ── 🔗 接続・サーバー ──
          SettingsRow(
            SettingsRoute.Gateway,
            verbatimText(gatewaySummary(gatewayConnectionDisplay)),
            status = gatewayConnectionDisplay.isConnected,
          ),
          SettingsRow(SettingsRoute.GatewayConfig, nativeText("Raw JSON configuration editor"), status = gatewayConnectionDisplay.isConnected),
          SettingsRow(SettingsRoute.NodesDevices, verbatimText(nodesDevicesSummaryText(nodesDevicesSummary)), status = nodesDevicesStatus(nodesDevicesSummary)),
          SettingsRow(SettingsRoute.Channels, channelsState.summaryText(::channelsSummaryText), status = if (channelsState.errorText != null) false else channelsSummary?.let(::channelsStatus)),
          SettingsRow(
            SettingsRoute.BackupRestore,
            nativeText("設定・ペアリングの保存"),
            status = true,
          ),

          // ── 📱 端末・プライバシー ──
          SettingsRow(SettingsRoute.Voice, if (speakerEnabled) nativeText("Speaker on") else nativeText("Speaker muted")),
          SettingsRow(SettingsRoute.Notifications, if (notificationForwardingEnabled) nativeText("Smart delivery") else nativeText("Off")),
          SettingsRow(SettingsRoute.PhoneCapabilities, if (cameraEnabled) nativeText("Camera enabled") else nativeText("Locked"), status = !cameraEnabled),

          // ── 🎨 プロフィール・外観 ──
          SettingsRow(
            SettingsRoute.Appearance,
            joinedNativeText(
              separator = " · ",
              parts = listOf(verbatimText(appearanceThemeSummary(appearanceThemeMode)), verbatimText(appLanguage.displayName)),
            ),
          ),
          SettingsRow(SettingsRoute.About, nativeText("Version and update")),

          // ── 🔧 診断 ──
          SettingsRow(SettingsRoute.PcHealth, nativeText("温度・メモリ・稼働状況"), status = isConnected),
          SettingsRow(SettingsRoute.Health, nativeText("Diagnostics"), status = isConnected),
        ).filter { it.route.isAvailable(desktopObserveAvailable) }

      settingsSections(settingsRows).forEach { section ->
        item(key = "section_${section.category.name}") {
          CollapsibleSettingsSection(
            section = section,
            selectedRoute = selectedRoute,
            onOpen = onRouteChange,
          )
        }
      }

      item {
        SettingsSectionTitle(nativeText("Account"))
      }
      item {
        ClawPanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
          SettingsListRow(
            title = nativeText("Sign Out"),
            value = nativeText("Return to setup"),
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            opensRoute = false,
            onClick = viewModel::pairNewGateway,
          )
        }
      }

      item {
        SettingsSectionTitle(nativeText("Licenses"))
      }
      item {
        SettingsGroup(
          rows = listOf(SettingsRow(SettingsRoute.Licenses, verbatimText(""))),
          onOpen = onRouteChange,
        )
      }

      item {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(text = nativeString("OpenClaw \${BuildConfig.VERSION_NAME} (\${BuildConfig.VERSION_CODE})", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.textMuted)
          Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (isConnected) nativeString("All systems operational") else nativeString("Gateway not connected"),
              style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
              color = ClawTheme.colors.textSubtle,
            )
            Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(if (isConnected) ClawTheme.colors.success else ClawTheme.colors.textSubtle))
          }
        }
      }
    }
  }
  }

  // Unfolded foldables and tablets: list on the left, the open setting on the right.
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val twoPane = maxWidth >= SHELL_TWO_PANE_MIN_WIDTH
    val listPaneWidth = shellListPaneWidth(maxWidth)
    val detail: @Composable () -> Unit = {
      SettingsDetailScreen(
        viewModel = viewModel,
        route = route,
        onBack = onBack,
        onOpenSettingsRoute = onRouteChange,
      )
    }
    // One call site per pane so folding/unfolding keeps an open setting's unsaved edits.
    Row(modifier = Modifier.fillMaxSize()) {
      if (twoPane) {
        Box(modifier = Modifier.width(listPaneWidth).fillMaxHeight().testTag("settings-list-pane")) {
          SettingsHomeList(selectedRoute = route)
        }
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(ClawTheme.colors.border))
      }
      Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        when {
          route != SettingsRoute.Home -> detail()
          twoPane -> SettingsDetailPlaceholder()
          else -> SettingsHomeList(selectedRoute = null)
        }
      }
    }
  }
}

@Composable
private fun SettingsDetailPlaceholder() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(40.dp), tint = ClawTheme.colors.textSubtle)
    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = "左の一覧から設定を選ぶと、ここに表示されます",
      style = ClawTheme.type.body,
      color = ClawTheme.colors.textMuted,
      textAlign = TextAlign.Center,
    )
  }
}

/** Content width from which list + detail sit side by side (an unfolded book-style foldable in portrait). */
internal val SHELL_TWO_PANE_MIN_WIDTH = 600.dp

/** List pane width: wide enough to read rows, leaving most of the space for the detail. */
internal fun shellListPaneWidth(totalWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
  (totalWidth * 0.4f).coerceIn(260.dp, 400.dp)

@Composable
private fun SettingsQuickTiles(onOpen: (SettingsRoute) -> Unit) {
  val tiles = listOf(SettingsRoute.PartnerManagement, SettingsRoute.OllamaCatalog, SettingsRoute.Gateway, SettingsRoute.Appearance)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = "よく使う", style = ClawTheme.type.section.copy(fontSize = 15.sp), color = ClawTheme.colors.text)
    tiles.chunked(2).forEach { rowTiles ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rowTiles.forEach { tile ->
          val title = tile.title.resolveNativeTextResource()
          Surface(
            onClick = { onOpen(tile) },
            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            shape = RoundedCornerShape(ClawTheme.radii.row),
            color = ClawTheme.colors.surfaceRaised,
            border = BorderStroke(1.dp, ClawTheme.colors.border),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(tile.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = ClawTheme.colors.primary)
              Text(text = title, style = ClawTheme.type.body.copy(fontSize = 14.sp), color = ClawTheme.colors.text, maxLines = 2)
            }
          }
        }
        if (rowTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
      }
    }
  }
}

private fun approvalsSummary(count: Int): String =
  when (count) {
    0 -> nativeString("No pending approvals")
    1 -> nativeString("1 pending")
    else -> nativeString("\$count pending", count)
  }

private fun approvalsStatus(count: Int): Boolean? = if (count > 0) true else null

/** Summarizes scheduled gateway jobs for overview and settings rows. */
private fun cronJobsSummary(count: Int): String =
  when (count) {
    0 -> nativeString("No scheduled jobs")
    1 -> nativeString("1 scheduled")
    else -> nativeString("\$count scheduled", count)
  }

private fun <T> GatewaySummaryState<T>.summaryText(format: (T) -> String): NativeText =
  errorText ?: summary?.let { verbatimText(format(it)) }
    ?: if (refreshing) nativeText("Refreshing") else nativeText("Load from gateway")

private fun usageSummaryText(count: Int): String =
  when (count) {
    0 -> nativeString("No provider usage")
    1 -> nativeString("1 provider")
    else -> nativeString("\$count providers", count)
  }

/** Reports how many gateway skills are enabled, eligible, and dependency-complete. */
private fun skillsSummaryText(skills: List<GatewaySkillSummary>): String {
  val ready =
    skills.count {
      !it.disabled && it.eligible && !it.blockedByAllowlist && !it.blockedByAgentFilter && it.missingCount == 0
    }
  return if (skills.isEmpty()) {
    nativeString("No skills")
  } else {
    nativeString("\$ready/\${skills.size} ready", ready, skills.size)
  }
}

/** Converts gateway skill health into a tri-state settings status dot. */
private fun skillsStatus(skills: List<GatewaySkillSummary>): Boolean? =
  when {
    skills.isEmpty() -> null

    skills.any {
      it.blockedByAllowlist ||
        it.blockedByAgentFilter ||
        (!it.disabled && (!it.eligible || it.missingCount > 0))
    } -> false

    else -> true
  }

/** Mirrors the Skill Workshop review queue in one compact Settings row. */
internal fun skillWorkshopSummaryText(summary: GatewaySkillWorkshopSummary): String {
  val pending = summary.proposals.count { it.status == "pending" }
  if (pending > 0) return if (pending == 1) nativeString("1 pending") else nativeString("\$pending pending", pending)
  val held = summary.proposals.count { it.status == "quarantined" || it.status == "stale" }
  val applied = summary.proposals.count { it.status == "applied" }
  return when {
    summary.proposals.isEmpty() -> nativeString("No proposals")
    held > 0 -> if (held == 1) nativeString("1 held") else nativeString("\$held held", held)
    applied > 0 -> if (applied == 1) nativeString("1 applied") else nativeString("\$applied applied", applied)
    else -> nativeString("\${summary.proposals.size} proposals", summary.proposals.size)
  }
}

internal fun skillWorkshopStatus(summary: GatewaySkillWorkshopSummary): Boolean? =
  when {
    summary.proposals.any { it.status == "pending" } -> false
    summary.proposals.any { it.status == "quarantined" || it.status == "stale" } -> false
    summary.proposals.any { it.status == "applied" } -> true
    else -> null
  }

/** Prioritizes pending pairings over online counts for compact node/device summaries. */
private fun nodesDevicesSummaryText(summary: GatewayNodesDevicesSummary): String {
  val online = summary.nodes.count { it.connected }
  val devices = summary.pairedDevices.size
  return when {
    summary.pendingDevices.isNotEmpty() -> nativeString("\${summary.pendingDevices.size} pending", summary.pendingDevices.size)
    summary.hasNodeCapabilityApprovalPending() -> nativeString("Node approval pending")
    summary.nodes.isNotEmpty() -> nativeString("\$online/\${summary.nodes.size} online", online, summary.nodes.size)
    devices > 0 -> nativeString("\$devices paired", devices)
    else -> nativeString("No devices")
  }
}

/** Maps node/device state to a settings status dot, treating pending pairings as attention-needed. */
private fun nodesDevicesStatus(summary: GatewayNodesDevicesSummary): Boolean? =
  when {
    summary.pendingDevices.isNotEmpty() -> false
    summary.hasNodeCapabilityApprovalPending() -> false
    summary.nodes.any { it.connected } -> true
    summary.pairedDevices.isNotEmpty() -> true
    else -> null
  }

private fun GatewayNodesDevicesSummary.hasNodeCapabilityApprovalPending(): Boolean =
  nodes.any { node ->
    node.approvalState == GatewayNodeApprovalState.PendingApproval ||
      node.approvalState == GatewayNodeApprovalState.PendingReapproval ||
      node.approvalState == GatewayNodeApprovalState.Unapproved
  }

/** Summarizes channel connection state, surfacing errors before connected counts. */
internal fun channelsSummaryText(summary: GatewayChannelsSummary): String {
  val connected = summary.channels.count { it.connected }
  val issueCount = summary.channels.count { it.error != null }
  return when {
    issueCount == 1 -> nativeString("1 issue")
    issueCount > 1 -> nativeString("\$issueCount issues", issueCount)
    summary.channels.isNotEmpty() -> nativeString("\$connected/\${summary.channels.size} connected", connected, summary.channels.size)
    else -> nativeString("No channels")
  }
}

/** Maps channel health to the settings status dot shown in the shell. */
private fun channelsStatus(summary: GatewayChannelsSummary): Boolean? =
  when {
    summary.channels.any { it.error != null } -> false
    summary.channels.any { it.connected || it.running } -> true
    summary.channels.any { it.configured || it.linked } -> true
    else -> null
  }

/** Summarizes dreaming memory health before enabled/off state. */
private fun dreamingSummaryText(summary: GatewayDreamingSummary): String =
  when {
    !summary.storeHealthy || !summary.phaseSignalHealthy -> nativeString("Needs attention")
    summary.enabled -> nativeString("\${summary.shortTermCount} waiting", summary.shortTermCount)
    else -> nativeString("Off")
  }

/** Maps dreaming store/phase health and enabled state to a settings status dot. */
private fun dreamingStatus(summary: GatewayDreamingSummary): Boolean? =
  when {
    !summary.storeHealthy || !summary.phaseSignalHealthy -> false
    summary.enabled -> true
    else -> null
  }

internal data class SettingsRow(
  val route: SettingsRoute,
  val value: NativeText,
  val status: Boolean? = null,
)

internal data class SettingsSection(
  val title: NativeText,
  val category: SettingsCategory,
  val rows: List<SettingsRow>,
)

internal fun settingsSections(rows: List<SettingsRow>): List<SettingsSection> =
  SettingsCategory.entries.mapNotNull { category ->
    val sectionRows = rows.filter { row -> row.route.category == category }
    if (sectionRows.isEmpty()) null else SettingsSection(title = category.title, category = category, rows = sectionRows)
  }

@Composable
private fun SettingsSectionTitle(title: NativeText) {
  val localizedTitle = title.resolveNativeTextResource()
  Text(
    text = localizedUppercase(localizedTitle, currentAppLanguage().languageTag),
    style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
    color = ClawTheme.colors.textMuted,
  )
}

/**
 * 折りたたみ可能なセクション。タップでヘッダーを展開/折りたたみ切替。
 * defaultExpanded がカテゴリごとに定義されている。
 */
@Composable
private fun CollapsibleSettingsSection(
  section: SettingsSection,
  selectedRoute: SettingsRoute? = null,
  onOpen: (SettingsRoute) -> Unit,
) {
  var expanded by rememberSaveable(section.category.name) {
    mutableStateOf(section.category.defaultExpanded)
  }
  val arrowRotation by animateFloatAsState(
    targetValue = if (expanded) 0f else -90f,
    label = "arrow",
  )

  Column {
    // タップ可能ヘッダー（タイトル + 項目数 + 矢印）
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp))
        .clickable { expanded = !expanded }
        .testTag("settings-section-${section.category.name}")
        .padding(vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = section.title.resolveNativeTextResource(),
        style = ClawTheme.type.section.copy(fontSize = 15.sp, lineHeight = 20.sp),
        color = ClawTheme.colors.text,
      )
      Spacer(modifier = Modifier.width(6.dp))
      // 項目数バッジ
      Text(
        text = "${section.rows.size}",
        style = ClawTheme.type.caption.copy(fontSize = 10.sp),
        color = ClawTheme.colors.textMuted,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(ClawTheme.colors.surfaceRaised)
          .padding(horizontal = 6.dp, vertical = 1.dp),
      )
      Spacer(modifier = Modifier.weight(1f))
      Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = if (expanded) "折りたたむ" else "展開",
        tint = ClawTheme.colors.textMuted,
        modifier = Modifier
          .size(18.dp)
          .graphicsLayer { rotationZ = arrowRotation },
      )
    }

    // コンテンツ (アニメーション付き展開)
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
      SettingsGroup(rows = section.rows, selectedRoute = selectedRoute, onOpen = onOpen)
    }
  }
}

@Composable
private fun ProfilePanel(
  displayName: String,
  onClick: () -> Unit,
) {
  ClawPanel(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(ClawTheme.radii.row))
          .clickable(onClick = onClick),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = ClawTheme.colors.surfacePressed,
        border = BorderStroke(1.dp, ClawTheme.colors.borderStrong),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text =
              localizedInitial(displayName, currentAppLanguage().languageTag) ?: "O",
            style = ClawTheme.type.title.copy(fontSize = 14.sp, lineHeight = 17.sp),
            color = ClawTheme.colors.text,
            textAlign = TextAlign.Center,
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = displayName, style = ClawTheme.type.section, color = ClawTheme.colors.text, maxLines = 1)
        Text(text = nativeString("OpenClaw mobile"), style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = ClawTheme.colors.textMuted, maxLines = 1)
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = nativeString("Open profile"),
        modifier = Modifier.size(15.dp),
        tint = ClawTheme.colors.text,
      )
    }
  }
}

@Composable
private fun SettingsGroup(
  rows: List<SettingsRow>,
  selectedRoute: SettingsRoute? = null,
  onOpen: (SettingsRoute) -> Unit,
) {
  ClawPanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
    ClawSeparatedColumn(items = rows) { row ->
      Box(
        modifier =
          Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (row.route == selectedRoute) ClawTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .semantics { selected = row.route == selectedRoute },
      ) {
        SettingsListRow(
          title = row.route.title,
          value = row.value,
          icon = row.route.icon,
          status = row.status,
          onClick = { onOpen(row.route) },
        )
      }
    }
  }
}

@Composable
private fun SettingsListRow(
  title: NativeText,
  value: NativeText,
  icon: ImageVector,
  status: Boolean? = null,
  opensRoute: Boolean = true,
  onClick: () -> Unit,
) {
  val localizedTitle = title.resolveNativeTextResource()
  ClawListItem(
    title = localizedTitle,
    subtitle = value.resolveNativeTextResource().takeIf { it.isNotBlank() },
    leading = {
      Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(ClawTheme.colors.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = ClawTheme.colors.primary)
      }
    },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        status?.let { active ->
          Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (active) ClawTheme.colors.success else ClawTheme.colors.textSubtle))
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = settingsRowDisclosureDescription(localizedTitle, opensRoute = opensRoute),
          modifier = Modifier.size(17.dp),
          tint = ClawTheme.colors.text,
        )
      }
    },
    onClick = onClick,
  )
}

internal fun settingsRowDisclosureDescription(
  localizedTitle: String,
  opensRoute: Boolean,
): String = if (opensRoute) nativeString("Open \${row.title}", localizedTitle) else localizedTitle

internal fun gatewaySummary(
  statusText: String,
  isConnected: Boolean,
  gatewayConnectionProblem: GatewayConnectionProblem? = null,
): String {
  if (isConnected) return if (statusText == "Connected (node offline)") gatewayStatusForDisplay(statusText) else nativeString("Online and ready")
  val status = statusText.trim().lowercase()
  return when {
    status.contains("connecting") || status.contains("reconnecting") -> nativeString("Connecting...")
    status.contains("pairing") -> nativeString("Waiting for pairing")
    status.contains("auth") || status.contains("device identity") -> gatewayAuthRecoveryLabel(gatewayConnectionProblem) ?: nativeString("Authentication needed")
    status.contains("fingerprint verification timed out") -> nativeString("TLS timed out")
    status.contains("no tls endpoint") -> nativeString("No TLS endpoint")
    status.contains("certificate") || status.contains("tls") -> nativeString("Certificate review needed")
    else -> nativeString("Not connected")
  }
}

internal fun gatewaySummary(display: GatewayConnectionDisplay): String = gatewaySummary(display.statusText, display.isConnected, display.problem)

private data class QuickPartnerEntry(
  val id: String,
  val name: String,
  val avatarUrl: String,
  val modelId: String,
)

@Composable
internal fun QuickPartnerChatPanel(
  viewModel: MainViewModel,
  onOpenPartnerChat: (String) -> Unit,
  onOpenPartnerManagement: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  var partners by remember { mutableStateOf<List<QuickPartnerEntry>>(emptyList()) }
  val connectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()

  fun loadPartners() {
    coroutineScope.launch {
      try {
        val resultJson = viewModel.runGatewayRpc(GatewayMethod.PartnerList, "{}")
        val obj = JSONObject(resultJson)
        val arr = obj.optJSONArray("partners") ?: JSONArray()
        val list = mutableListOf<QuickPartnerEntry>()
        for (i in 0 until arr.length()) {
          val p = arr.getJSONObject(i)
          list.add(
            QuickPartnerEntry(
              id = p.optString("id", ""),
              name = p.optString("name", "パートナー"),
              avatarUrl = p.optString("avatarUrl", ""),
              modelId = p.optString("modelId", "")
            )
          )
        }
        partners = list
      } catch (_: Exception) {}
    }
  }

  LaunchedEffect(connectionDisplay.isConnected) {
    if (connectionDisplay.isConnected) {
      loadPartners()
    }
  }

  if (partners.isNotEmpty()) {
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "💬 誰と話しますか？",
              style = ClawTheme.type.section.copy(fontSize = 15.sp),
              color = ClawTheme.colors.text,
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
          }
          ClawSecondaryButton(
            text = "パートナー管理",
            onClick = onOpenPartnerManagement,
          )
        }

        androidx.compose.foundation.lazy.LazyRow(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        ) {
          items(partners) { partner ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onOpenPartnerChat(partner.id)
                }
                .padding(vertical = 4.dp, horizontal = 6.dp),
            ) {
              PartnerAvatar(
                avatarUrl = partner.avatarUrl,
                name = partner.name,
                size = 54.dp,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = partner.name,
                style = ClawTheme.type.caption.copy(
                  fontSize = 12.sp,
                  fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = ClawTheme.colors.text,
                maxLines = 1,
              )
              Text(
                text = "タップでチャット",
                style = ClawTheme.type.caption.copy(fontSize = 10.sp),
                color = ClawTheme.colors.primary,
              )
            }
          }
        }
      }
    }
  }
}

private data class EngineModelChoice(
  val id: String,
  val label: String,
  val badge: String,
  val description: String,
  val vramText: String,
  val isRecommended: Boolean = false,
  /** Section heading in the picker; choices are shown grouped in list order. */
  val group: String = "",
)

private data class EngineThinkingChoice(
  val id: String,
  val label: String,
  val budgetText: String,
)

@Composable
internal fun OverviewModelEngineControlPanel(
  viewModel: MainViewModel,
  onOpenChat: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val connectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()
  val isConnected = connectionDisplay.isConnected

  val modelChoices = remember {
    listOf(
      EngineModelChoice(
        id = "bonsai/swift-bonsai-ptq1",
        label = "Swift-Bonsai 27B (PTQ1)",
        badge = "爆速推奨",
        description = "いつものメイン。1bit超軽量+ドラフトで速い。仕事中もチャットもOK。",
        vramText = "6.7 GB",
        group = "Bonsai 27B（メイン）",
        isRecommended = true,
      ),
      EngineModelChoice(
        id = "bonsai/swift-bonsai-pq2",
        label = "Swift-Bonsai 27B (PQ2)",
        badge = "高精度",
        description = "2bitで少し賢い。切替時にChrome等を止めてVRAMを空ける。チャット専用時に。",
        vramText = "6.9 GB",
        group = "Bonsai 27B（メイン）",
      ),
      EngineModelChoice(
        id = "bonsai/bonsai2-pq1",
        label = "Bonsai2 (PQ1)",
        badge = "新世代",
        description = "Bonsai第2世代+ドラフト。軽くて速く、日常会話・作業向け。",
        vramText = "6.7 GB",
        group = "Bonsai 27B（メイン）",
      ),
      EngineModelChoice(
        id = "bonsai/bonsai2CLACKpq1",
        label = "Bonsai2 CLACK (PQ1)",
        badge = "制限解除",
        description = "Bonsaiの制限解除版(1bit)。創作・なんでも話したいときに。",
        vramText = "6.7 GB",
        group = "Bonsai 27B（メイン）",
      ),
      EngineModelChoice(
        id = "bonsai/ternary-bonsai-2-27b",
        label = "Ternary-Bonsai 27B (PQ2)",
        badge = "標準",
        description = "バランス型27B。切替時にChrome等を止めてVRAMを空ける。",
        vramText = "6.9 GB",
        group = "Bonsai 27B（メイン）",
      ),
      EngineModelChoice(
        id = "bonsai/bonsai-crack",
        label = "Bonsai-Crack 27B (PQ2)",
        badge = "制限解除",
        description = "Bonsaiの制限解除版(2bit)。切替時にChrome等を止めてVRAMを空ける。",
        vramText = "6.9 GB",
        group = "Bonsai 27B（メイン）",
      ),
      EngineModelChoice(
        id = "bonsai/nemoaurora-rp-12b",
        label = "NemoAurora RP 12B",
        badge = "日本語RP",
        description = "日本語のキャラなりきり特化(Aratako製)。口調や性格をよく守る。まず試すならこれ。",
        vramText = "6.7 GB",
        group = "日本語ロールプレイ・なりきり",
      ),
      EngineModelChoice(
        id = "bonsai/himeyuri-magnum-12b",
        label = "Himeyuri Magnum 12B",
        badge = "日本語RP",
        description = "日本語の小説・ロールプレイ向け+制限解除。描写が豊かで感情表現が得意。",
        vramText = "6.7 GB",
        group = "日本語ロールプレイ・なりきり",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3-8b-rp",
        label = "Qwen3 8B RP",
        badge = "日本語RP",
        description = "日本語のキャラなりきり特化(Aratako製)。軽くて反応が速い。",
        vramText = "5.0 GB",
        group = "日本語ロールプレイ・なりきり",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3-8b-erp",
        label = "Qwen3 8B ERP",
        badge = "日本語RP",
        description = "上のRP版の制限なし版。大人向けの会話もできる。",
        vramText = "5.0 GB",
        group = "日本語ロールプレイ・なりきり",
      ),
      EngineModelChoice(
        id = "bonsai/shisa-v2-nemo-12b-abliterated",
        label = "Shisa v2 Nemo 12B",
        badge = "日本語",
        description = "日本語を強化したモデルの制限解除版。自然な日本語で雑談・なりきり。",
        vramText = "6.7 GB",
        group = "日本語ロールプレイ・なりきり",
      ),
      EngineModelChoice(
        id = "bonsai/mag-mell-r1-12b",
        label = "Mag-Mell R1 12B",
        badge = "英語RP",
        description = "英語圏で大人気のRP・創作モデル。日本語は少し不自然になることがある。",
        vramText = "6.8 GB",
        group = "ロールプレイ（英語中心）",
      ),
      EngineModelChoice(
        id = "bonsai/magnum-v2-12b",
        label = "Magnum v2 12B",
        badge = "英語RP",
        description = "小説風の文章が得意な英語RPモデル。日本語もそこそこ。",
        vramText = "6.8 GB",
        group = "ロールプレイ（英語中心）",
      ),
      EngineModelChoice(
        id = "bonsai/hermes3-8b",
        label = "Hermes 3 8B",
        badge = "会話",
        description = "Llama 3.1ベースの会話・RP向け。指示に素直に従う。",
        vramText = "4.7 GB",
        group = "ロールプレイ（英語中心）",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3.8-9b-heretic",
        label = "Qwen3.8 9B",
        badge = "最新・制限解除",
        description = "Qwenの最新世代9Bの制限解除版。日本語が上手く賢い。普段使いの本命候補。",
        vramText = "5.6 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3.5-9b-uncensored",
        label = "Qwen3.5 9B",
        badge = "人気・制限解除",
        description = "70万DL超の人気制限解除版(HauhauCS)。日本語◎、何でも答える。",
        vramText = "5.6 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3.5-9b-defiant-fable",
        label = "Qwen3.5 9B Defiant Fable",
        badge = "創作",
        description = "190万DL超の人気モデル。物語・創作・ロールプレイに強い制限解除版。",
        vramText = "6.4 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/gemma4-12b-uncensored",
        label = "Gemma 4 12B",
        badge = "人気・制限解除",
        description = "Googleの最新Gemma 4の制限解除版(88万DL)。日本語が自然で丁寧。",
        vramText = "7.4 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/gemma4-e4b-uncensored",
        label = "Gemma 4 E4B",
        badge = "制限解除",
        description = "Gemma 4小型の制限解除版(130万DL)。軽くて日本語も上手。",
        vramText = "5.3 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/elyza-jp-8b",
        label = "ELYZA Llama-3 JP 8B",
        badge = "日本語",
        description = "ELYZA社の日本語特化モデル。制限は通常どおり。",
        vramText = "4.9 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/gemma2-9b",
        label = "Gemma 2 9B",
        badge = "制限解除",
        description = "Googleの一つ前の世代の制限解除版。丁寧な文章。",
        vramText = "5.8 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/llama3.1-8b",
        label = "Llama 3.1 8B",
        badge = "制限解除",
        description = "Metaの8Bの制限解除版。英語が中心。",
        vramText = "4.9 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/qwen2.5-7b-abliterated",
        label = "Qwen2.5 7B",
        badge = "制限解除",
        description = "少し前のQwenの制限解除版。日本語も可。",
        vramText = "4.7 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/deepseek-r1-qwen3-8b-abliterated",
        label = "DeepSeek R1 Qwen3 8B",
        badge = "推論",
        description = "考えてから答える推論モデルの制限解除版。計算・論理向け。",
        vramText = "5.0 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/dolphin-llama3-8b",
        label = "Dolphin Llama3 8B",
        badge = "制限少なめ",
        description = "もともと制限の少ない会話モデル。英語寄り。",
        vramText = "4.7 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/dolphin-mistral-7b",
        label = "Dolphin Mistral 7B",
        badge = "制限少なめ",
        description = "もともと制限の少ない会話モデル(Mistral)。英語寄り。",
        vramText = "4.1 GB",
        group = "制限解除・汎用",
      ),
      EngineModelChoice(
        id = "bonsai/gemma4-e2b-uncensored",
        label = "Gemma 4 E2B",
        badge = "超軽量",
        description = "Gemma 4最小の制限解除版。とにかく速い。簡単な会話向け。",
        vramText = "3.9 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3.5-4b-abliterated",
        label = "Qwen3.5 4B",
        badge = "軽量",
        description = "Qwen3.5小型の制限解除版。軽いわりに賢い。",
        vramText = "4.5 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/qwen3-4b-abliterated",
        label = "Qwen3 4B 2507",
        badge = "軽量",
        description = "Qwen3小型の制限解除版。",
        vramText = "2.5 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/phi4-mini-abliterated",
        label = "Phi-4 Mini",
        badge = "軽量",
        description = "Microsoftの小型の制限解除版。英語・計算向け。",
        vramText = "4.1 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/gemma3-4b-abliterated",
        label = "Gemma 3 4B",
        badge = "軽量",
        description = "Gemma 3小型の制限解除版。",
        vramText = "3.2 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/qwen2.5-coder-3b-abliterated",
        label = "Qwen2.5 Coder 3B",
        badge = "コード",
        description = "プログラミング向け小型の制限解除版。",
        vramText = "3.3 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/phi3-mini",
        label = "Phi-3 Mini",
        badge = "超軽量",
        description = "軽くて速い小型の制限解除版。英語向け。",
        vramText = "2.4 GB",
        group = "軽量・高速",
      ),
      EngineModelChoice(
        id = "bonsai/qwen2.5-3b",
        label = "Qwen2.5 3B",
        badge = "超軽量",
        description = "軽くて速い小型の制限解除版。",
        vramText = "1.9 GB",
        group = "軽量・高速",
      ),
    )
  }

  val thinkingChoices = remember {
    listOf(
      EngineThinkingChoice("off", "⚡ Off", "思考なし"),
      EngineThinkingChoice("low", "🟢 低", "512t"),
      EngineThinkingChoice("medium", "🟡 中", "2K t"),
      EngineThinkingChoice("high", "🔴 高", "8K t"),
      EngineThinkingChoice("max", "💥 Max", "無制限"),
    )
  }


  var selectedModelId by remember { mutableStateOf("bonsai/swift-bonsai-ptq1") }
  val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
  var selectedThinkingLevel by remember { mutableStateOf("medium") }
  var isApplying by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var isSuccess by remember { mutableStateOf(false) }

  ClawPanel {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // ヘッダー行
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "🧠 推論モデル・思考エンジン統括",
            style = ClawTheme.type.section.copy(fontSize = 15.sp),
            color = ClawTheme.colors.text,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
          )
        }
        Text(
          text = if (isApplying) "適用中..." else "VRAM安全制御",
          style = ClawTheme.type.caption.copy(fontSize = 11.sp),
          color = if (isApplying) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
        )
      }

      // 説明
      Text(
        text = "ホームから全チャットのモデルと推論思考を一括設定。古いプロセスを安全終了しVRAM溢れを防ぎます。",
        style = ClawTheme.type.caption.copy(fontSize = 12.sp),
        color = ClawTheme.colors.textMuted,
      )

      // モデル選択エリア
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "実行モデル選択",
          style = ClawTheme.type.caption.copy(
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
          ),
          color = ClawTheme.colors.text,
        )

        modelChoices.groupBy { it.group }.forEach { (group, choices) ->
          val holdsSelection = choices.any { it.id == selectedModelId }
          // The section holding the current choice starts open; the others start closed.
          val expanded = expandedGroups[group] ?: holdsSelection
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { expandedGroups[group] = !expanded }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "$group（${choices.size}）",
              style = ClawTheme.type.caption.copy(
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
              ),
              color = if (holdsSelection) ClawTheme.colors.primary else ClawTheme.colors.text,
            )
            Icon(
              imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = ClawTheme.colors.textMuted,
            )
          }
          if (expanded) {
            choices.forEach { choice ->
              val isSelected = choice.id == selectedModelId
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                  .background(
                    if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.12f)
                    else ClawTheme.colors.surfaceRaised.copy(alpha = 0.5f)
                  )
                  .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                  )
                  .clickable { selectedModelId = choice.id }
                  .padding(horizontal = 12.dp, vertical = 8.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text(
                        text = choice.label,
                        style = ClawTheme.type.body.copy(
                          fontSize = 13.sp,
                          fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                        ),
                        color = if (isSelected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
                      )
                      if (choice.isRecommended) {
                        Box(
                          modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(ClawTheme.colors.primary)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                          Text(
                            text = choice.badge,
                            style = ClawTheme.type.caption.copy(
                              fontSize = 9.sp,
                              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                            color = androidx.compose.ui.graphics.Color.White,
                          )
                        }
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = choice.description,
                      style = ClawTheme.type.caption.copy(fontSize = 11.sp),
                      color = ClawTheme.colors.textMuted,
                      maxLines = 3,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = choice.vramText,
                    style = ClawTheme.type.caption.copy(
                      fontSize = 11.sp,
                      fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    ),
                    color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
                  )
                }
              }
            }
          }
        }
      }

      // 思考レベル選択
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "思考レベル (Reasoning Budget)",
          style = ClawTheme.type.caption.copy(
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
          ),
          color = ClawTheme.colors.text,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          thinkingChoices.forEach { choice ->
            val isSelected = choice.id == selectedThinkingLevel
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(
                  if (isSelected) ClawTheme.colors.primary.copy(alpha = 0.15f)
                  else ClawTheme.colors.surfaceRaised.copy(alpha = 0.45f)
                )
                .border(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.border.copy(alpha = 0.6f),
                  shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                )
                .clickable { selectedThinkingLevel = choice.id }
                .padding(vertical = 8.dp, horizontal = 4.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = choice.label,
                  style = ClawTheme.type.caption.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                  ),
                  color = if (isSelected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
                  maxLines = 1,
                )
                Text(
                  text = choice.budgetText,
                  style = ClawTheme.type.caption.copy(fontSize = 9.sp),
                  color = if (isSelected) ClawTheme.colors.primary else ClawTheme.colors.textMuted.copy(alpha = 0.7f),
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      // ステータスメッセージ
      statusMessage?.let { msg ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(
              if (isSuccess) androidx.compose.ui.graphics.Color(0xFF1B5E20).copy(alpha = 0.3f)
              else androidx.compose.ui.graphics.Color(0xFFB71C1C).copy(alpha = 0.3f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(
            text = msg,
            style = ClawTheme.type.caption.copy(fontSize = 12.sp),
            color = if (isSuccess) androidx.compose.ui.graphics.Color(0xFF81C784) else androidx.compose.ui.graphics.Color(0xFFEF9A9A),
          )
        }
      }

      // 適用ボタン
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(
              if (isApplying || !isConnected) ClawTheme.colors.primary.copy(alpha = 0.4f)
              else ClawTheme.colors.primary
            )
            .clickable(enabled = !isApplying && isConnected) {
              isApplying = true
              statusMessage = "プロセス切替中... VRAM最適化中..."
              isSuccess = false

              coroutineScope.launch {
                try {
                  val params = JSONObject().apply {
                    put("model", selectedModelId)
                    put("thinkingLevel", selectedThinkingLevel)
                  }
                  val resultJson = viewModel.runGatewayRpc(
                    GatewayMethod.OllamaModelSwitch,
                    params.toString(),
                    timeoutMs = 45_000L,
                  )
                  val rawObj = JSONObject(resultJson)
                  val obj = rawObj.optJSONObject("payload") ?: rawObj
                  val ok = obj.optBoolean("success", false) || obj.optBoolean("ok", false)

                  if (ok) {
                    isSuccess = true
                    val activeM = obj.optString("activeModel", selectedModelId)
                    statusMessage = "✅ 適用完了！「$activeM」(思考: $selectedThinkingLevel) で起動しました。"
                  } else {
                    val errMsg = obj.optString("error", "切替に失敗しました")
                    statusMessage = "❌ エラー: $errMsg"
                  }
                } catch (e: Exception) {
                  statusMessage = "❌ 通信エラー: ${e.message}"
                } finally {
                  isApplying = false
                }
              }
            }
            .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (isApplying) {
              androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                strokeWidth = 2.dp,
              )
            }
            Text(
              text = if (isApplying) "適用・起動中..." else "⚡ 設定を適用して起動",
              style = ClawTheme.type.body.copy(
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              ),
              color = androidx.compose.ui.graphics.Color.White,
            )
          }
        }

        if (isSuccess) {
          Box(
            modifier = Modifier
              .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
              .background(ClawTheme.colors.surfaceRaised)
              .clickable(onClick = onOpenChat)
              .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "チャットへ →",
              style = ClawTheme.type.body.copy(
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              ),
              color = ClawTheme.colors.primary,
            )
          }
        }
      }
    }
  }
}
