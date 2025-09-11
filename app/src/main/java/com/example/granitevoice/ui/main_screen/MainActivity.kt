package com.example.granitevoice.ui.main_screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.granitevoice.R
import com.example.granitevoice.data.local.preferences.GranitePreferences
import com.example.granitevoice.ui.components.VoiceResultCard
import com.example.granitevoice.ui.configuration.ConfigurationScreen
import com.example.granitevoice.ui.configuration.ConfigurationViewModel
import com.example.granitevoice.ui.theme.GraniteVoiceTheme
import com.example.granitevoice.ui.tutorial.TutorialScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt

private fun calculateMaxRadius(
    screenWidth: Float,
    screenHeight: Float,
    fabX: Float,
    fabY: Float
): Float {
    val distanceToTopLeft = sqrt(fabX * fabX + fabY * fabY)
    val distanceToTopRight = sqrt((screenWidth - fabX).pow(2) + fabY.pow(2))
    val distanceToBottomLeft = sqrt(fabX.pow(2) + (screenHeight - fabY).pow(2))
    val distanceToBottomRight = sqrt((screenWidth - fabX).pow(2) + (screenHeight - fabY).pow(2))

    return maxOf(
        distanceToTopLeft,
        distanceToTopRight,
        distanceToBottomLeft,
        distanceToBottomRight
    ) * 1.2f
}

private fun Float.pow(i: Int): Float = this * this

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val syncViewModel: SyncViewModel by viewModels()

    @Inject
    lateinit var granitePreferences: GranitePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GraniteVoiceTheme {
                MainAppContent(
                    mainViewModel = viewModel,
                    syncViewModel = syncViewModel,
                    granitePreferences = granitePreferences
                )
            }
        }
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            // Handle permission denial if needed, e.g., show a snackbar.
        }
    }
}

/**
 * Main app content that handles navigation between configuration and main screens
 */
@Composable
fun MainAppContent(
    mainViewModel: MainViewModel,
    syncViewModel: SyncViewModel,
    granitePreferences: GranitePreferences
) {
    val isConfigValid = granitePreferences.isValidConfiguration()
    android.util.Log.d("MainActivity", "Configuration valid: $isConfigValid")
    android.util.Log.d(
        "MainActivity",
        "Config summary: ${granitePreferences.getConfigurationSummary()}"
    )

    // Start with config screen if not configured, otherwise start with main screen
    var showConfiguration by remember { mutableStateOf(!isConfigValid) }

    android.util.Log.d("MainActivity", "showConfiguration state: $showConfiguration")

    if (showConfiguration) {
        val configViewModel: ConfigurationViewModel = hiltViewModel()

        ConfigurationScreen(
            onConfigurationComplete = {
                android.util.Log.d("MainActivity", "Configuration completed, hiding config screen")
                showConfiguration = false
            },
            onSkipConfiguration = {
                android.util.Log.d("MainActivity", "Configuration skipped, hiding config screen")
                showConfiguration = false
            },
            viewModel = configViewModel
        )
    } else {
        MainScreen(
            viewModel = mainViewModel,
            syncViewModel = syncViewModel,
            onShowConfiguration = {
                android.util.Log.d("MainActivity", "Settings button clicked, showing config screen")
                showConfiguration = true
            }
        )
    }
}

/**
 * The main screen of the application, which observes state from the [MainViewModel]
 * and handles side effects like status bar color changes and snackbar messages.
 * This composable is the stateful container for the UI.
 *
 * @param viewModel The view model that provides the UI state and handles business logic.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    syncViewModel: SyncViewModel,
    onShowConfiguration: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val localSnackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val context = view.context

    val blue = Color(0xFF00a5cc)
    val grey = Color(0xFF272727)
    val theme = Color(0xFF2D2D2E)

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = theme.toArgb()
            window.navigationBarColor = when (uiState) {
                is MainViewModel.UiState.Listening, is MainViewModel.UiState.Processing -> blue.toArgb()
                else -> grey.toArgb()
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            localSnackbarHostState.showSnackbar(it)
            viewModel.snackbarShown()
        }
    }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            localSnackbarHostState.showSnackbar(it)
            syncViewModel.snackbarShown()
        }
    }

    MainScreenContent(
        uiState = uiState,
        onStartRecording = viewModel::startRecording,
        onStopRecording = viewModel::stopRecording,
        onCancelRecording = viewModel::cancelRecording,
        snackbarHostState = localSnackbarHostState,
        mainViewModel = viewModel,
        onShowConfiguration = onShowConfiguration
    )
}

/**
 * The core, stateless content of the main screen. It displays the UI based on the provided
 * [uiState] and delegates user actions to the provided lambda functions.
 *
 * @param uiState The current state of the UI, which determines what is displayed.
 * @param onStartRecording Lambda to be invoked when the user starts a recording.
 * @param onStopRecording Lambda to be invoked when the user stops a recording.
 * @param onCancelRecording Lambda to be invoked when the user cancels a recording.
 * @param snackbarHostState The state for managing snackbar visibility and content.
 */
@Composable
fun MainScreenContent(
    uiState: MainViewModel.UiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    snackbarHostState: SnackbarHostState,
    mainViewModel: MainViewModel,
    onShowConfiguration: () -> Unit
) {
    val grey = Color(0xFF272727)
    val blue = Color(0xFF00a5cc)
    val theme = Color(0xFF2D2D2E)
    var helpVisible by remember { mutableStateOf(false) }
    val commandResult by mainViewModel.commandResult.collectAsState()

    var fabX by remember { mutableFloatStateOf(0f) }
    var fabY by remember { mutableFloatStateOf(0f) }
    var circleRadius by remember { mutableFloatStateOf(28f) }
    var circleVisible by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val maxRadius = remember(fabX, fabY) {
        if (fabX != 0f && fabY != 0f) calculateMaxRadius(
            screenWidth,
            screenHeight,
            fabX,
            fabY
        ) else 28f
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            MainViewModel.UiState.Listening, MainViewModel.UiState.Processing -> {
                circleVisible = true
                circleRadius = maxRadius
            }
            else -> { // Idle
                circleRadius = 28f
            }
        }
    }

    val animatedRadius by animateFloatAsState(
        targetValue = circleRadius,
        animationSpec = tween(
            durationMillis = if (circleRadius > 28f) 600 else 300
        ),
        finishedListener = { radius ->
            if (radius == 28f && uiState !is MainViewModel.UiState.Listening && uiState !is MainViewModel.UiState.Processing) {
                circleVisible = false
            }
        },
        label = "RadiusAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(grey)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(-1f)
        ) {
            if (circleVisible && fabX != 0f && fabY != 0f) {
                drawCircle(
                    color = blue,
                    radius = animatedRadius,
                    center = Offset(fabX, fabY),
                    style = Fill
                )
            }
        }

        VoiceResultCard(
            result = commandResult,
            isVisible = commandResult != null,
            onDismiss = mainViewModel::clearResult,
            onSelectAlternative = mainViewModel::retryWithAlternative,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        )

        AnimatedVisibility(
            visible = uiState == MainViewModel.UiState.Processing,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                "Processing",
                color = Color(0xFF2D2D2E),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        AnimatedVisibility(
            visible = uiState == MainViewModel.UiState.Listening,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text("Listening", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 50.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState is MainViewModel.UiState.Idle) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentSize(Alignment.BottomStart)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(elevation = 8.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(theme, CircleShape)
                                .clickable { helpVisible = !helpVisible },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.icon_help),
                                contentDescription = "Help",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (uiState is MainViewModel.UiState.Idle || uiState is MainViewModel.UiState.Listening) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        VoiceNoteButton(
                            onStartRecording = onStartRecording,
                            onStopRecording = onStopRecording,
                            onCancelRecording = onCancelRecording,
                            setFabPosition = { x, y -> fabX = x; fabY = y },
                            modifier = Modifier
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (uiState is MainViewModel.UiState.Idle) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentSize(Alignment.BottomEnd)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(elevation = 8.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(theme, CircleShape)
                                .clickable { onShowConfiguration() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            AnimatedVisibility(
                visible = helpVisible,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                TutorialScreen(onDismiss = { helpVisible = false })
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            )
        }
    }
}

/**
 * A custom composable button that handles the gestures for starting, stopping, and cancelling
 * a voice recording. It provides visual feedback to the user during these interactions.
 *
 * @param onStartRecording Lambda to be invoked when a press gesture is detected.
 * @param onStopRecording Lambda to be invoked when the press is released.
 * @param onCancelRecording Lambda to be invoked when a drag-up gesture is detected.
 * @param setFabPosition Lambda to report the button's screen coordinates for animations.
 * @param modifier The modifier to be applied to the button.
 */
@Composable
fun VoiceNoteButton(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    setFabPosition: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val iconSize by animateDpAsState(targetValue = if (isPressed) 32.dp else 24.dp, label = "")
    var wasCancelled by remember { mutableStateOf(false) }

    // Reverted gesture modifier logic
    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    wasCancelled = false
                    onStartRecording()
                    dragY = 0f
                    try {
                        val success = tryAwaitRelease()
                        if (success) {
                            if (!wasCancelled) {
                                onStopRecording()
                            }
                        } else {
                            // The press was interrupted, e.g., by another gesture.
                            onCancelRecording()
                        }
                    } finally {
                        isPressed = false
                    }
                },
            )
        }
        .pointerInput(isPressed) {
            if (isPressed) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        dragY += dragAmount.y
                        if (dragY < -200) {
                            wasCancelled = true
                            onCancelRecording()
                        }
                        change.consume()
                    },
                    onDragEnd = { isPressed = false },
                    onDragCancel = { isPressed = false }
                )
            }
        }

    val shouldCancel = dragY < -200

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = isPressed,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Text(
                "Slide to cancel",
                color = if (shouldCancel) Color.Red else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Box(
            modifier = Modifier
                .then(gestureModifier)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(color = Color(0xFF00a5cc))
                .padding(16.dp)
                .onGloballyPositioned { coordinates ->
                    val x = coordinates.positionInRoot().x + coordinates.size.width / 2f
                    val y = coordinates.positionInRoot().y + coordinates.size.height / 2f
                    setFabPosition(x, y)
                }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.icon_voice_white),
                contentDescription = "Record",
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}