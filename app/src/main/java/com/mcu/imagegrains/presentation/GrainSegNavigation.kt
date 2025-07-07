package com.mcu.imagegrains.presentation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mcu.imagegrains.presentation.camera.CameraScreen
import com.mcu.imagegrains.presentation.home.HomeScreen
import com.mcu.imagegrains.presentation.photo_display.PhotoDisplayScreen
import com.mcu.imagegrains.presentation.splash.SplashScreen

@Composable
fun GrainSegmentationNavigation(navController: NavHostController, activity: Activity) {
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
                photoUriString = photoUri
            )
        }
    }
}