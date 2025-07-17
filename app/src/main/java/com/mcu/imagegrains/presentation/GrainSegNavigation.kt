package com.mcu.imagegrains.presentation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mcu.imagegrains.presentation.camera.CameraScreen
import com.mcu.imagegrains.presentation.home.HomeScreen
import com.mcu.imagegrains.presentation.instance_seg.InstanceSegmentationScreen
import com.mcu.imagegrains.presentation.multiple_sessions.MultiSessionComparisonScreen
import com.mcu.imagegrains.presentation.photo_display.PhotoDisplayScreen
import com.mcu.imagegrains.presentation.result_overview.ResultsOverviewScreen
import com.mcu.imagegrains.presentation.scale_calibration.ScaleCalibrationScreen
import com.mcu.imagegrains.presentation.semantic_seg.SemanticSegmentationResultScreen
import com.mcu.imagegrains.presentation.session_detail.SessionDetailScreen
import com.mcu.imagegrains.presentation.session_list.SessionsListScreen
import com.mcu.imagegrains.presentation.splash.SplashScreen

@Composable
fun GrainSegmentationNavigation(
    navController: NavHostController,
    activity: Activity,
    sharedSegmentationViewModel: SharedSegmentationViewModel
) {
    NavHost(
        navController = navController,
        startDestination = SplashScreen
    ) {
        composable<SplashScreen> {
            SplashScreen{
                navController.navigate(HomeScreen)
            }
        }

        composable<HomeScreen> {
            HomeScreen(
                navigateToPhotoDisplay = { photoUri ->
                    navController.navigate(PhotoDisplayScreen(photoUri))
                                         },
                navigateToCamera = { navController.navigate(CameraScreen) },
                navigateToSessionList = { navController.navigate(SessionsListScreen) },
                onBackPress = { activity.finish() }
            )
        }

        composable<CameraScreen> {
            CameraScreen(
                onBackClicked = { navController.popBackStack() },
                navigateToPhotoDisplay = { photoUri ->
                    navController.navigate(PhotoDisplayScreen(photoUri))
                }
            )
        }

        composable<PhotoDisplayScreen> { backStackEntry ->
            val photoUri = backStackEntry.toRoute<PhotoDisplayScreen>().photoUri
            PhotoDisplayScreen(
                onBackClicked = { navController.popBackStack()},
                photoUriString = photoUri,
                sharedViewModel = sharedSegmentationViewModel,
                navigateToSegmentation = {
                    // Navigate to segmentation screen
                    navController.navigate(SemanticSegmentationScreen)
                }
            )
        }

        composable<SemanticSegmentationScreen>{
            SemanticSegmentationResultScreen(
                goBack = {
                    sharedSegmentationViewModel.clearResults()
                    navController.popBackStack()
                         },
                navigateToScaleCalibration = {
                    // Navigate to scale calibration screen
                    navController.navigate(ScaleCalibrationScreen)
                },
                sharedViewModel = sharedSegmentationViewModel
            )
        }

        composable<ScaleCalibrationScreen>{
            ScaleCalibrationScreen(
                goBack = { navController.popBackStack() },
                navigateToInstanceSegmentation = {
                    // Navigate to instance segmentation screen
                    navController.navigate(InstanceSegmentationScreen)
                },
                sharedViewModel = sharedSegmentationViewModel
            )
        }

        composable<InstanceSegmentationScreen>{
            InstanceSegmentationScreen(
                goBack = { navController.popBackStack() },
                navigateToScaleCalibration = {
                    // Navigate to scale calibration screen
                    navController.navigate(ScaleCalibrationScreen)
                },
                navigateToResultOverview = {
                    // Navigate to result overview screen
                    navController.navigate(ResultOverviewScreen)
                },
                sharedViewModel = sharedSegmentationViewModel
            )
        }

        composable<ResultOverviewScreen>{
            ResultsOverviewScreen(
                goBack = { navController.popBackStack() },
                goToHome = { navController.navigate(HomeScreen) {
                    popUpTo(HomeScreen) { inclusive = true }
                } },
                sharedViewModel = sharedSegmentationViewModel
            )
        }

        composable<SessionsListScreen>{
            SessionsListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSessionDetail = { sessionId ->
                    navController.navigate(SessionDetailScreen(sessionId))
                },
                onNavigateToMultiSessionComparison = { sessionIds ->
                    navController.navigate(MultiSessionComparisonScreen(sessionIds.joinToString(",")))
                }
            )
        }

        composable<SessionDetailScreen>{ backStackEntry ->
            val sessionId = backStackEntry.toRoute<SessionDetailScreen>().sessionId
            SessionDetailScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<MultiSessionComparisonScreen>{ backStackEntry ->
            val sessionIds = backStackEntry.toRoute<MultiSessionComparisonScreen>().sessionIds.split(",")
            MultiSessionComparisonScreen(
                sessionIds = sessionIds,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}