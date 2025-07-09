package com.mcu.imagegrains.presentation

import kotlinx.serialization.Serializable

@Serializable
object SplashScreen

@Serializable
object HomeScreen

@Serializable
object CameraScreen

@Serializable
data class PhotoDisplayScreen(val photoUri: String)

@Serializable
object InstanceSegmentationScreen

@Serializable
object SemanticSegmentationScreen

@Serializable
object ResultOverviewScreen

@Serializable
object ScaleCalibrationScreen