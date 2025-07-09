package com.mcu.imagegrains.presentation.photo_display

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mcu.imagegrains.presentation.SharedSegmentationViewModel
import com.mcu.imagegrains.utils.ImageProcessingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDisplayScreen(
    onBackClicked: () -> Unit,
    photoUriString: String,
    sharedViewModel: SharedSegmentationViewModel,
    navigateToSegmentation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photoUri = remember { Uri.decode(photoUriString).toUri() }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        loadImageFromUri(context, photoUri) { bitmap, error ->
            if (bitmap != null) {
                selectedBitmap = bitmap
                // SET THE ORIGINAL IMAGE IN SHARED VIEW MODEL
                sharedViewModel.setOriginalImage(photoUri, bitmap)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Captured Photo") },
            navigationIcon = {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Captured photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Image size: ${selectedBitmap!!.width} × ${selectedBitmap!!.height}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBackClicked,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retake")
            }

            Button(
                onClick = {
                    // navController.navigate("segmentation/${Uri.encode(photoUri.toString())}")
                    navigateToSegmentation()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Analyze Grains")
            }
        }
    }
}

/**
 * Helper function to load image from URI
 */
private fun loadImageFromUri(
    context: android.content.Context,
    uri: Uri,
    callback: (Bitmap?, String?) -> Unit
) {
    try {
        val bitmap = ImageProcessingUtils.loadBitmapFromUri(context, uri)
        if (bitmap != null) {
            callback(bitmap, null)
        } else {
            callback(null, "Failed to load image from URI")
        }
    } catch (e: Exception) {
        callback(null, "Error loading image: ${e.message}")
    }
}

/*
@Preview
@Composable
private fun PhotoDisplayScreenPreview() {
    PhotoDisplayScreen(
        {},
        photoUriString = "",
    )
}*/
