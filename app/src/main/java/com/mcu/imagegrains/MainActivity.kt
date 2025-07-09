package com.mcu.imagegrains

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.mcu.imagegrains.presentation.GrainSegmentationNavigation
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import com.mcu.imagegrains.ui.theme.ImageGrainsTheme

class MainActivity : ComponentActivity() {
    private val sharedViewModel: SharedSegmentationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedViewModel.initializeModels(this)

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ImageGrainsTheme {
                val navController = rememberNavController()
                GrainSegmentationNavigation(navController = navController, this, sharedViewModel)
            }
        }
    }
}