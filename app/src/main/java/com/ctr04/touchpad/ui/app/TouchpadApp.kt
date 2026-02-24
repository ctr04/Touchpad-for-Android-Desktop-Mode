package com.ctr04.touchpad.ui.app

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ctr04.touchpad.data.service.MyTouchpadService
import com.ctr04.touchpad.common.extensions.getActivity
import com.ctr04.touchpad.common.utils.isAccessibilityServiceEnabled
import com.ctr04.touchpad.domain.entities.settings.AppearanceSettings
import com.ctr04.touchpad.presentation.viewmodel.AppScopeViewModel
import com.ctr04.touchpad.ui.navigation.AppNavDestination
import com.ctr04.touchpad.ui.navigation.AppNavHost
import com.ctr04.touchpad.ui.navigation.navigateTo
import com.ctr04.touchpad.ui.screens.AccessibilityPermissionsScreen
import com.ctr04.touchpad.ui.screens.TouchpadScreen
import com.ctr04.touchpad.ui.screens.SettingsScreen
import com.ctr04.touchpad.ui.screens.ThirdLibrariesScreen
import com.ctr04.touchpad.ui.theme.BtRemoteTheme
import com.ctr04.touchpad.ui.theme.surfaceElevationLow
import org.koin.androidx.compose.koinViewModel

@Composable
fun BtRemoteApp(
    navController: NavHostController = rememberNavController(),
    appScopeViewModel: AppScopeViewModel = koinViewModel(),
    navigateToSettings: () -> Unit = {
        navController.navigateTo(AppNavDestination.SettingsDestination.route)
    },
    context: Context = LocalContext.current
) {
    val appearance by appScopeViewModel.appearanceSettingsFlow
        .collectAsStateWithLifecycle(AppearanceSettings())

    var isServiceEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context, MyTouchpadService::class.java))
    }

    LifecycleResumeEffect(Unit) {
        isServiceEnabled = isAccessibilityServiceEnabled(context, MyTouchpadService::class.java)
        onPauseOrDispose {}
    }

    val startDest = if (isServiceEnabled) AppNavDestination.RemoteDestination.route
    else AppNavDestination.AccessibilityPermissionsDestination.route

    BtRemoteTheme(
        appearance = appearance
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            tonalElevation = surfaceElevationLow()
        ) {

            // ---- NavHost ----


            AppNavHost(
                navController = navController,

                startDestination = startDest,

                accessibilityPermissionsScreen = {
                    AccessibilityPermissionsScreen(
                        onPermissionsGranted = {
                            navController.navigate(AppNavDestination.RemoteDestination.route) {
                                popUpTo(0) {
                                    this.saveState = false
                                }
                                launchSingleTop = true
                            }
                        },
                        navigateToSettings = navigateToSettings,
                        modifier = Modifier
                    )
                },

                settingsScreen = {
                    SettingsScreen(
                        navigateUp = { navController.navigateUp() },
                        navigateToThirdLibrariesScreen = {
                            navController.navigateTo(AppNavDestination.ThirdLibrariesDestination.route)
                        },
                        modifier = Modifier
                    )
                },

                thirdLibrariesScreen = {
                    ThirdLibrariesScreen(
                        navigateUp = { navController.navigateUp() },
                        modifier = Modifier
                    )
                },

                remoteScreen = {
                    TouchpadScreen(
                        closeApp = { context.getActivity()?.moveTaskToBack(true) },
                        navigateToSettings = navigateToSettings,
                        modifier = Modifier
                    )
                },
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.displayCutout.exclude(
                        WindowInsets.systemBars
                    )
                )
            )
        }
    }
}