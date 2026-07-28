package com.iblu01.portallauncher
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.domain.model.TemperatureSummary

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iblu01.portallauncher.ui.CallService
import com.iblu01.portallauncher.ui.LauncherViewModel
import com.iblu01.portallauncher.ui.LocalAreas
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.LocalHaStates
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.iblu01.portallauncher.ui.mapper.chipPlacement
import com.iblu01.portallauncher.ui.mapper.toChipAction
import com.iblu01.portallauncher.ui.mapper.toPanelKind
import com.iblu01.portallauncher.ui.model.ChipAction
import com.iblu01.portallauncher.ui.model.ChipPlacement
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.panel.PanelEvent
import com.iblu01.portallauncher.ui.panel.PanelSource
import com.iblu01.portallauncher.ui.panel.PanelRequest
import com.iblu01.portallauncher.ui.components.AlertOverlay
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.AppEntry
import com.iblu01.portallauncher.ui.components.AutoReturnOverlay
import com.iblu01.portallauncher.ui.components.CameraOverlay
import com.iblu01.portallauncher.ui.components.ChipActionsPanel
import com.iblu01.portallauncher.ui.components.ClockScreen
import com.iblu01.portallauncher.ui.components.MediaPlayerView
import com.iblu01.portallauncher.ui.components.PanelContent
import com.iblu01.portallauncher.ui.components.PresenceIndicator
import com.iblu01.portallauncher.ui.components.QuickActionsOverlay
import com.iblu01.portallauncher.ui.components.WeatherController
import com.iblu01.portallauncher.ui.components.WeatherPanel
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.blurCompat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var pills: PillRepository
    private var lastLaunchMs = 0L
    private lateinit var autoReturnTimer: AutoReturnTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MqttBridgeService.start(this)
        applyPowerPolicy()

        autoReturnTimer = AutoReturnTimer(lifecycleScope, prefs)

        setContent {
            PortalTheme {
                PortalLauncherApp(
                    prefs = prefs,
                    pills = pills,
                    autoReturnTimer = autoReturnTimer,
                    onOpenHomeAssistant = ::openHomeAssistant,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenPlayground = { startActivity(Intent(this, PlaygroundActivity::class.java)) },
                    loadApps = ::launchableApps,
                    onLaunchApp = ::launchApp
                )
            }
        }
        requestLocationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        pills.start(prefs)
        applyPowerPolicy()
        DeviceStateHub.onLauncherForeground(true, this)
        enableImmersive()
    }

    override fun onPause() {
        DeviceStateHub.onLauncherForeground(false, this)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersive()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) {
            SleepScheduler.onInteraction(this)
            autoReturnTimer.onInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun applyPowerPolicy() {
        if (prefs.powerMode == PowerMode.ALWAYS_ON || prefs.devKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun openHomeAssistant() {
        val now = System.currentTimeMillis()
        if (now - lastLaunchMs < 1_000L) return
        lastLaunchMs = now

        val pkg = prefs.homeAssistantPackage
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "Application introuvable: $pkg", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        DeviceStateHub.noteLaunchingApp(pkg, this)
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun launchApp(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        DeviceStateHub.noteLaunchingApp(app.packageName, this)
        startActivity(intent)
    }

    private fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo?.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
            .mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                AppEntry(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = ai.packageName,
                    activityName = ai.name
                )
            }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 2)
        }
    }

    @Suppress("DEPRECATION")
    private fun enableImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

/**
 * The whole launcher UI: ambient clock, floating widget tray, and the long-press
 * quick-actions overlay. All Android side-effects are injected as lambdas so this
 * composable stays previewable and free of Activity state.
 */
@Composable
private fun PortalLauncherApp(
    prefs: Prefs,
    pills: PillRepository,
    autoReturnTimer: AutoReturnTimer,
    onOpenHomeAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayground: () -> Unit,
    loadApps: () -> List<AppEntry>,
    onLaunchApp: (AppEntry) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var backgroundMode by remember { mutableStateOf(prefs.backgroundMode) }
    var bgOverlayOpacity by remember { mutableStateOf(prefs.bgOverlayOpacity) }
    var clockTheme by remember { mutableStateOf(prefs.clockTheme) }
    // Bumped on every "backgroundMode" emission (even custom->custom) so CustomWallpaper
    // re-reads the file's lastModified() and Coil busts its stale cache on replacement.
    var wallpaperVersion by remember { mutableStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        SettingsChangeBus.get().changes.collect { key ->
            when (key) {
                "backgroundMode" -> {
                    backgroundMode = prefs.backgroundMode
                    wallpaperVersion++
                }
                "haUrl", "haToken" -> pills.start(prefs)
                "brokerHost", "brokerPort", "username", "password" ->
                    MqttBridgeService.reconnect(context)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                backgroundMode = prefs.backgroundMode
                bgOverlayOpacity = prefs.bgOverlayOpacity
                clockTheme = prefs.clockTheme
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Single state-holder collection (MAD/UDF). Replaces the ~9 mutableStateOf + PillHub.Listener
    // DisposableEffect: transforms run off-main in PillHub.snapshotFlow (flowOn(Default)), conflated
    // by the VM's StateFlow. chips/temperatures/mediaSessions/connection derive from this one snapshot.
    val vm: LauncherViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    snapshots = pills.snapshotFlow(prefs),
                    callServiceFn = pills::callService,
                )
            }
        }
    )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val chips = ui.chips
    val temperatures = ui.temperatures
    val mediaSessions = ui.mediaSessions
    val haConnected = ui.connected
    val haLastUpdateAt = ui.lastUpdateAt
    // Media-selection state stays local (moves to the panel reducer at step 6).
    var activeMedia by remember { mutableStateOf<PlayingMedia?>(null) }
    var secondaryMedia by remember { mutableStateOf(emptyList<PlayingMedia>()) }
    var displayedSecondaryMedia by remember { mutableStateOf(emptyList<PlayingMedia>()) }
    var selectedMediaEntityId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaSessions, selectedMediaEntityId) {
        val selected = mediaSessions.firstOrNull { session ->
            session.players.any { it.entityId == selectedMediaEntityId }
        } ?: mediaSessions.firstOrNull()
        activeMedia = selected
        selectedMediaEntityId = selected?.entityId
        secondaryMedia = mediaSessions.filterNot { it.entityId == selected?.entityId }
    }
    LaunchedEffect(mediaSessions, secondaryMedia) {
        val activePlayerIds = mediaSessions.flatMap { it.players }.map { it.entityId }.toSet()
        val recentlyRemoved = displayedSecondaryMedia.filter { session ->
            session.players.none { it.entityId in activePlayerIds }
        }
        displayedSecondaryMedia = secondaryMedia + recentlyRemoved
        if (recentlyRemoved.isNotEmpty()) {
            delay(6_000)
            displayedSecondaryMedia = secondaryMedia
        }
    }
    val weatherController = remember { WeatherController(context.applicationContext, pills) }
    val weather = weatherController.state
    DisposableEffect(weatherController) {
        weatherController.start()
        onDispose { weatherController.stop() }
    }
    var overlayVisible by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var pillsExpanded by remember { mutableStateOf(false) }

    // Side panel state lives in the reducer (VM, step 6). Media auto-open/stop is driven by the
    // media flow; user taps dispatch PanelEvents. Panel no longer closes on chip disappearance —
    // panelChip is resolved last-known-good by the VM.
    val panel by vm.panel.collectAsStateWithLifecycle()
    val panelChip by vm.panelChip.collectAsStateWithLifecycle()
    val autoReturnState by autoReturnTimer.state.collectAsStateWithLifecycle()

    // Auto-return is for *user* state only: a USER panel, the expanded tray, the app overlay. An
    // AUTO (media) panel is the resting state while something plays, so it must not arm the timer.
    LaunchedEffect(panel.request, panel.source, pillsExpanded, overlayVisible) {
        val userPanelOpen = panel.request != null && panel.source == PanelSource.USER
        if (userPanelOpen || pillsExpanded || overlayVisible) autoReturnTimer.start() else autoReturnTimer.stop()
    }

    LaunchedEffect(autoReturnState.shouldReturn) {
        if (autoReturnState.shouldReturn) {
            // Only a USER panel is dismissed: dismissing an AUTO media panel here would be read as
            // a user dismissal by the reducer (dismissedAutoKey) and suppress it for that session.
            if (panel.request != null && panel.source == PanelSource.USER) vm.onEvent(PanelEvent.Dismiss)
            if (pillsExpanded) pillsExpanded = false
            if (overlayVisible) overlayVisible = false
            autoReturnTimer.stop()
        }
    }
    val media = activeMedia
    val panelContent: PanelContent? = when (val req = panel.request) {
        is PanelRequest.Weather -> PanelContent.Weather(weather)
        is PanelRequest.Media -> {
            val session = mediaSessions.firstOrNull { it.entityId == req.key } ?: media
            session?.let { PanelContent.Media(it) }
        }
        is PanelRequest.Chip ->
            if (req.panelKind == PanelKind.MEDIA) media?.let { PanelContent.Media(it) }
            else panelChip?.let { PanelContent.ChipActions(it) }
        null -> null
    }
    val isSplit = panelContent != null
    // Remembered so it stays the same instance across the recompositions every HA push triggers
    // (unstable-param skip guard for the open panel, e.g. the alarm keypad — removed at step 10).
    val onPanelDismiss: () -> Unit = remember(vm) { { vm.onEvent(PanelEvent.Dismiss) } }
    // Dumb dispatcher (design §4): the mapper resolved the action, so no chip.id/kind branching here.
    val onChipClick: (LauncherChip) -> Unit = { chip ->
        when (val action = chip.toChipAction()) {
            is ChipAction.ServiceToggle -> vm.callService(action.domain, action.service, chip.entityId)
            is ChipAction.OpenPanel -> vm.onEvent(PanelEvent.OpenChip(PanelRequest.Chip(chip.id, action.panelKind)))
        }
    }
    // Long-press always opens the control panel (fan speed, switch info, …), except media.
    val onChipLongPress: (LauncherChip) -> Unit = { chip ->
        val kind = chip.toPanelKind()
        if (kind != PanelKind.MEDIA) {
            vm.onEvent(PanelEvent.LongPressChip(PanelRequest.Chip(chip.id, kind)))
        }
    }
    val onSecondaryPlayPause: (PlayingMedia) -> Unit = { session ->
        displayedSecondaryMedia = displayedSecondaryMedia.map {
            if (it.entityId == session.entityId) it.copy(
                state = if (it.state in setOf("playing", "buffering")) "paused" else "playing"
            ) else it
        }
        session.players.forEach { player ->
            vm.callService("media_player", "media_play_pause", player.entityId)
        }
    }

    // Only the media chip hides when its panel is open — other chips stay visible.
    val mediaChipId = if (panelContent is PanelContent.Media) "media_group" else null
    // Placement (design §7) decides tray vs floating; the media chip hides while its panel is open.
    val presenceChip = chips.firstOrNull { it.chipPlacement() == ChipPlacement.FLOATING }
    val visibleChips = chips.filterNot { it.id == mediaChipId || it.chipPlacement() == ChipPlacement.FLOATING }
    // The selected chip (its panel is open) gets a highlighted style in the tray.
    val selectedChipKey = (panel.request as? PanelRequest.Chip)?.key

    val bottomGradientHeight by animateDpAsState(
        targetValue = if (pillsExpanded) 620.dp else 360.dp,
        animationSpec = tween(450),
        label = "bottomGradientHeight"
    )

    LaunchedEffect(overlayVisible) {
        if (overlayVisible) apps = loadApps()
    }

    var cameraOverlay by remember { mutableStateOf<CameraPair?>(null) }
    val prevCameraOn = remember { HashMap<String, Boolean>() }
    LaunchedEffect(ui.latestStates) {
        prefs.cameraPairs.forEach { pair ->
            val on = ui.latestStates[pair.trigger]?.state?.equals("on", true) ?: return@forEach
            val was = prevCameraOn[pair.trigger]
            prevCameraOn[pair.trigger] = on
            if (on && was == false) cameraOverlay = pair
        }
    }

    val alertMessage = AlertOverlayState.activeMessage
    val blurRadius by animateDpAsState(
        targetValue = if (alertMessage != null || overlayVisible) 16.dp else 0.dp,
        animationSpec = tween(300),
        label = "blurRadius"
    )

    val sidePanel: @Composable (PanelContent) -> Unit = { content ->
        when (content) {
            is PanelContent.Media -> MediaPlayerPanel(
                media = content.session,
                secondaryMedia = displayedSecondaryMedia,
                prefs = prefs,
                mediaSessions = mediaSessions,
                onSelectSession = { selectedMediaEntityId = it },
                onDismiss = onPanelDismiss,
                onSecondaryPlayPause = onSecondaryPlayPause,
            )
            is PanelContent.ChipActions -> ChipActionsPanel(
                chip = content.chip,
                onDismiss = onPanelDismiss,
            )
            is PanelContent.Weather -> WeatherPanel(
                weather = content.weather,
                onDismiss = onPanelDismiss,
            )
        }
    }

    // Provide the HA service caller to the whole subtree (design §8): panels read LocalCallService
    // instead of the PillHub singleton. Remembered so the provided value stays a stable instance.
    val callServiceProvider = remember(vm) {
        object : CallService {
            override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) =
                vm.callService(domain, service, entityId, data)
        }
    }
    CompositionLocalProvider(
        LocalCallService provides callServiceProvider,
        LocalHaStates provides ui.latestStates,
        LocalAreas provides ui.areaByEntity,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blurCompat(blurRadius) else Modifier)
        ) {
            AmbientBackground(
                mode = backgroundMode,
                wallpaperVersion = wallpaperVersion,
                modifier = Modifier.fillMaxSize()
            )

            if (backgroundMode != "neutral") {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = bgOverlayOpacity))
                )
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomGradientHeight)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.42f to Color.Black.copy(alpha = 0.28f),
                                0.72f to Color.Black.copy(alpha = 0.68f),
                                1f to Color.Black.copy(alpha = 0.92f),
                            )
                        )
                    )
            )

            val topHeightFraction by animateFloatAsState(
                targetValue = if (isSplit) 0.67f else 1.0f,
                animationSpec = tween(500),
                label = "topHeightFraction"
            )
            val leftWidthFraction by animateFloatAsState(
                targetValue = if (isSplit) 0.67f else 1.0f,
                animationSpec = tween(500),
                label = "leftWidthFraction"
            )

            val clockScreen: @Composable () -> Unit = {
                ClockScreen(
                    backgroundMode = backgroundMode,
                    weather = weather,
                    temperatures = temperatures,
                    chips = visibleChips,
                    onTap = onOpenHomeAssistant,
                    onLongPress = { overlayVisible = true },
                    pillsExpanded = pillsExpanded,
                    onPillsExpandedChange = { pillsExpanded = it },
                    onChipClick = onChipClick,
                    onChipLongPress = onChipLongPress,
                    selectedChipKey = selectedChipKey,
                    onWeatherClick = { vm.onEvent(PanelEvent.WeatherTap) },
                    connected = haConnected,
                    lastUpdateAt = haLastUpdateAt,
                    drawBackground = false,
                    clockTheme = clockTheme,
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight
                if (landscape) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(leftWidthFraction)
                        ) { clockScreen() }
                        if (leftWidthFraction < 1.0f && panelContent != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                            ) { sidePanel(panelContent) }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(topHeightFraction)
                        ) { clockScreen() }
                        if (topHeightFraction < 1.0f && panelContent != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            ) { sidePanel(panelContent) }
                        }
                    }
                }
            }
        }

        PresenceIndicator(
            chip = presenceChip,
            onClick = { presenceChip?.let(onChipClick) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 16.dp),
        )

        QuickActionsOverlay(
            visible = overlayVisible,
            apps = apps,
            onDismiss = { overlayVisible = false },
            onSettings = onOpenSettings,
            onOpenPlayground = onOpenPlayground,
            onLaunchApp = { app -> overlayVisible = false; onLaunchApp(app) }
        )

        AlertOverlay(
            message = alertMessage,
            onDismiss = { AlertOverlayState.dismiss() }
        )

        cameraOverlay?.let { pair ->
            CameraOverlay(pair = pair, onDismiss = { cameraOverlay = null })
        }

        AutoReturnOverlay(state = autoReturnState, onCancel = { autoReturnTimer.onInteraction() })
    }
    }
}

@Composable
private fun MediaPlayerPanel(
    media: PlayingMedia,
    secondaryMedia: List<PlayingMedia>,
    prefs: Prefs,
    mediaSessions: List<PlayingMedia>,
    onSelectSession: (String?) -> Unit,
    onDismiss: () -> Unit,
    onSecondaryPlayPause: (PlayingMedia) -> Unit,
) {
    val callService = LocalCallService.current
    MediaPlayerView(
        media = media,
        secondaryMedia = secondaryMedia,
        haToken = prefs.haToken,
        onPlayPause = {
            callService("media_player", "media_play_pause", media.entityId)
        },
        onPrevious = {
            callService("media_player", "media_previous_track", media.entityId)
        },
        onNext = {
            callService("media_player", "media_next_track", media.entityId)
        },
        onVolumeChange = { entityId, volumeFraction ->
            callService(
                "media_player",
                "volume_set",
                entityId,
                mapOf("volume_level" to volumeFraction)
            )
        },
        onSecondaryPlayPause = onSecondaryPlayPause,
        onSecondaryPrevious = { session ->
            session.players.forEach { player ->
                callService("media_player", "media_previous_track", player.entityId)
            }
        },
        onSecondaryNext = { session ->
            session.players.forEach { player ->
                callService("media_player", "media_next_track", player.entityId)
            }
        },
        onSelectSecondary = { session -> onSelectSession(session.entityId) },
        onSwipePlayer = { direction ->
            val currentIndex = mediaSessions.indexOfFirst { it.entityId == media.entityId }
            if (currentIndex >= 0 && mediaSessions.size > 1) {
                val nextIndex = (currentIndex + direction + mediaSessions.size) % mediaSessions.size
                onSelectSession(mediaSessions[nextIndex].entityId)
            }
        },
        onJoinPlayer = { entityId ->
            callService(
                "media_player",
                "join",
                media.entityId,
                mapOf("group_members" to listOf(entityId)),
            )
        },
        onUnjoinPlayer = { entityId ->
            callService("media_player", "unjoin", entityId)
        },
        onDismiss = onDismiss
    )
}
