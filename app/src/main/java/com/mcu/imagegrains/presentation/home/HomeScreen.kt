package com.mcu.imagegrains.presentation.home

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.mcu.imagegrains.R
import com.mcu.imagegrains.presentation.PhotoDisplayScreen
import com.mcu.imagegrains.utils.ImageUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    navigateToPhotoDisplay: (String) -> Unit,
    navigateToCamera: () -> Unit,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        // Handle back press here
        onBackPress()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    // Permissions
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Photo selection launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isProcessing = true
                try {
                    // Check if image needs compression
                    val processedUri = ImageUtils.compressImageTo5MP(context, selectedUri)
                    if (processedUri == null) {
                        Toast.makeText(context, "Image compression failed", Toast.LENGTH_SHORT).show()
                    }
                    val finalUri = processedUri ?: selectedUri

                    // Navigate to display screen
                    //navController.navigate("photo_display/${Uri.encode(finalUri.toString())}")
                    navigateToPhotoDisplay("${Uri.encode(finalUri.toString())}")
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Handle error - show toast or snackbar
                    Toast.makeText(context, "Error processing image", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            // Image was captured successfully
            // Navigate to camera screen or handle the captured image
            //navController.navigate("camera")
            navigateToCamera()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "Capture Grain Image",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Select or capture an image to analyze",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column (
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(color = Color(41, 163, 41), shape = RoundedCornerShape(10.dp))
                .padding(16.dp)
                .clickable {
                    // Navigate to the capture image layer
                    if (cameraPermissionState.status.isGranted) {
                        //navController.navigate("camera")
                        navigateToCamera()
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                modifier = Modifier.size(80.dp),
                painter = painterResource(R.drawable.photo_camera),
                contentDescription = "Capture Image",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Take Photo",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Column (
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(color = Color(0, 170, 255), shape = RoundedCornerShape(10.dp))
                .padding(16.dp)
                .clickable(
                    enabled = !isProcessing,
                ) {
                    // Navigate to the capture image layer
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    modifier = Modifier.size(80.dp),
                    painter = painterResource(R.drawable.upload_file),
                    contentDescription = "Capture Image",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isProcessing) "Processing..." else "Upload Photo",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📋 Tips:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Photos will be optimized to 2000×2000 pixels\n" +
                            "• Large images are automatically compressed to 5MP\n" +
                            "• Ensure good lighting for best results",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    HomeScreen({},{}, {})
}