package com.ctr04.touchpad.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ctr04.touchpad.R
import com.ctr04.touchpad.common.utils.TouchpadEventBus
import com.ctr04.touchpad.common.utils.getKeyboardLayout
import com.ctr04.touchpad.domain.entities.remoteInput.MouseAction
import com.ctr04.touchpad.domain.entities.remoteInput.keyboard.KeyboardLanguage
import com.ctr04.touchpad.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.ctr04.touchpad.domain.entities.settings.RemoteSettings
import com.ctr04.touchpad.presentation.viewmodel.RemoteViewModel
import com.ctr04.touchpad.ui.components.AppScaffold
import com.ctr04.touchpad.ui.components.FadeAnimatedContent
import com.ctr04.touchpad.ui.components.KeyboardAction
import com.ctr04.touchpad.ui.components.RemoteAction
import com.ctr04.touchpad.ui.components.SettingsAction
import com.ctr04.touchpad.ui.theme.surfaceElevationMedium
import com.ctr04.touchpad.ui.views.keyboard.AdvancedKeyboard
import com.ctr04.touchpad.ui.views.keyboard.AdvancedKeyboardModalBottomSheet
import com.ctr04.touchpad.ui.views.keyboard.VirtualKeyboardModalBottomSheet
import com.ctr04.touchpad.ui.views.mouse.MousePadLayout
import org.koin.androidx.compose.koinViewModel

@Composable
fun TouchpadScreen(
    closeApp: () -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    remoteViewModel: RemoteViewModel = koinViewModel()
) {
    val configuration = LocalConfiguration.current

    val remoteSettings by remoteViewModel
        .remoteSettingsFlow.collectAsStateWithLifecycle(RemoteSettings())

    // Keyboard
    var showKeyboard: Boolean by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = true, onBack = closeApp)

    StatelessTouchpadScreen(
        isLandscapeMode = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        topBarActions = {
            TopBarActions(
                navigateToSettings = navigateToSettings,
                useAdvancedKeyboardIntegrated = remoteSettings.useAdvancedKeyboard && remoteSettings.useAdvancedKeyboardIntegrated,
                showKeyboard = showKeyboard,
                onShowKeyboardChanged = { showKeyboard = it },
            )
        },
        remoteLayout = {
            TouchpadLayout(
                showAdvancedKeyboard = remoteSettings.useAdvancedKeyboard && remoteSettings.useAdvancedKeyboardIntegrated && showKeyboard,
                keyboardLanguage = remoteSettings.keyboardLanguage,
                sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport
            )
        },
        navigationLayout = {
            NavigationLayout(
                remoteSettings = remoteSettings,
                sendMouseKeyReport = remoteViewModel.sendMouseReport,
            )
        },
        overlayView = {
            // Dialog
            when {
                showKeyboard && (!remoteSettings.useAdvancedKeyboard || !remoteSettings.useAdvancedKeyboardIntegrated) -> {
                    KeyboardModalBottomSheet(
                        useAdvancedKeyboard = remoteSettings.useAdvancedKeyboard,
                        keyboardLanguage = remoteSettings.keyboardLanguage,
                        mustClearInputField = remoteSettings.mustClearInputField,
                        sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport,
                        sendTextReport = remoteViewModel.sendTextReport,
                        onShowKeyboardChanged = { showKeyboard = it }
                    )
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatelessTouchpadScreen(
    isLandscapeMode: Boolean,
    topBarActions: @Composable (RowScope.() -> Unit),
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    overlayView: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName by TouchpadEventBus.displayName.collectAsStateWithLifecycle()

    AppScaffold(
        title = displayName,
        modifier = modifier,
        scrollBehavior = null,
        topBarActions = topBarActions
    ) { innerPadding ->

        if(isLandscapeMode) {
            TouchpadLandscapeView(
                remoteLayout = remoteLayout,
                navigationLayout = navigationLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            TouchpadPortraitView(
                remoteLayout = remoteLayout,
                navigationLayout = navigationLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }

        // Dialog / ModalBottomSheet
        overlayView()
    }
}

@Composable
private fun TouchpadLandscapeView(
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var rowSize by remember { mutableStateOf(Size.Zero) }

    Row(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            rowSize = layoutCoordinates.size.toSize() },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = with(LocalDensity.current) { (1f * rowSize.width).toDp() })
                .align(Alignment.CenterVertically)
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    top = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                ),
        ) {
            navigationLayout()
        }

        Box(
            modifier = Modifier.align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            remoteLayout()
        }
    }
}

@Composable
private fun TouchpadPortraitView(
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .heightIn(
                    max = with(LocalConfiguration.current) {
                        if (screenHeightDp >= screenWidthDp * 1.9f) // Si la hauteur de l'appareil est suffisamment haute par rapport à sa largeur (ratio ~ 1/2)
                            screenWidthDp.dp // On peut se permettre de prendre pour hauteur la largeur de l'écran
                        else // Sinon
                            (screenHeightDp * 0.50).dp // On prend 50% de la hauteur de l'écran
                    }
                )
                .align(Alignment.CenterHorizontally),
        ) {
            remoteLayout()
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    end = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                ),
            contentAlignment = Alignment.Center
        ) {
            navigationLayout()
        }
    }
}

@Composable
private fun TouchpadLayout(
    showAdvancedKeyboard: Boolean,
    keyboardLanguage: KeyboardLanguage,
    sendKeyboardKeyReport: (ByteArray) -> Unit
) {
    FadeAnimatedContent(targetState = showAdvancedKeyboard) {
        if (it) {
            AdvancedKeyboard(
                keyboardLanguage = keyboardLanguage,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_large)),
                keyElevation = surfaceElevationMedium()
            )
        }
    }
}

@Composable
private fun NavigationLayout(
    remoteSettings: RemoteSettings,
    sendMouseKeyReport: (input: MouseAction, x: Float, y: Float, wheel: Float) -> Unit,
) {
    MousePadLayout(
        mouseSpeed = remoteSettings.mouseSpeed,
        shouldInvertMouseScrollingDirection = remoteSettings.shouldInvertMouseScrollingDirection,
        useGyroscope = remoteSettings.useGyroscope,
        sendMouseInput = sendMouseKeyReport,
        modifier = Modifier
    )
}

@Composable
private fun KeyboardModalBottomSheet(
    useAdvancedKeyboard: Boolean,
    keyboardLanguage: KeyboardLanguage,
    mustClearInputField: Boolean,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, VirtualKeyboardLayout, Boolean) -> Unit,
    onShowKeyboardChanged: (Boolean) -> Unit,
) {
    if (useAdvancedKeyboard) {
        AdvancedKeyboardModalBottomSheet(
            keyboardLanguage = keyboardLanguage,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            onShowKeyboardBottomSheetChanged = onShowKeyboardChanged
        )
    } else {
        var virtualKeyboardLayout: VirtualKeyboardLayout by remember {
            mutableStateOf(getKeyboardLayout(keyboardLanguage))
        }

        LaunchedEffect(keyboardLanguage) {
            virtualKeyboardLayout = getKeyboardLayout(keyboardLanguage)
        }

        VirtualKeyboardModalBottomSheet(
            mustClearInputField = mustClearInputField,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            sendTextReport = { text: String, shouldSendEnter: Boolean ->
                sendTextReport(text, virtualKeyboardLayout, shouldSendEnter)
            },
            onShowKeyboardBottomSheetChanged = onShowKeyboardChanged
        )
    }
}

@Composable
private fun TopBarActions(
    navigateToSettings: () -> Unit,
    useAdvancedKeyboardIntegrated: Boolean,
    showKeyboard: Boolean,
    onShowKeyboardChanged: (Boolean) -> Unit,
) {

    if(useAdvancedKeyboardIntegrated) {
        FadeAnimatedContent(targetState = showKeyboard) {
            when (it) {
                true -> {
                    RemoteAction(
                        showRemote = {
                            onShowKeyboardChanged(false)
                        }
                    )
                }

                false -> {
                    KeyboardAction(
                        showKeyboard = {
                            onShowKeyboardChanged(true)
                        }
                    )
                }
            }
        }
    } else {
        KeyboardAction(
            showKeyboard = {
                onShowKeyboardChanged(!showKeyboard)
            }
        )
    }

    SettingsAction(
        navigateToSettings = navigateToSettings
    )
}