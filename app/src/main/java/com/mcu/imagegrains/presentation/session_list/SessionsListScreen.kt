package com.mcu.imagegrains.presentation.session_list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcu.imagegrains.R
import com.mcu.imagegrains.data.local.GrainDatabase
import com.mcu.imagegrains.data.local.GrainSession
import com.mcu.imagegrains.domain.repository.GrainRepository
import com.mcu.imagegrains.utils.ImageUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSessionDetail: (String) -> Unit,
    onNavigateToMultiSessionComparison: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val database = remember { GrainDatabase.getDatabase(context) }
    val repository = remember { GrainRepository(database.grainSessionDao()) }
    val scope = rememberCoroutineScope()

    val sessions by repository.getAllSessions().collectAsState(initial = emptyList())
    var selectedSessions by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .systemBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                IconButton(
                    onClick = {
                        selectedSessions = emptySet()
                        isSelectionMode = false
                    }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel Selection",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${selectedSessions.size} selected",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    if (selectedSessions.size > 1) {
                        IconButton(
                            onClick = {
                                onNavigateToMultiSessionComparison(selectedSessions.toList())
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Compare",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Saved Sessions",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp
                    ),
                    modifier = Modifier.weight(0.7f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Select All button (only show if there are sessions)
                if (sessions.isNotEmpty()) {
                    Checkbox(
                        checked = selectedSessions.size == sessions.size,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                isSelectionMode = true
                                selectedSessions = sessions.map { it.id }.toSet()
                            }
                        }
                    )
                    /*IconButton(
                        onClick = {
                            isSelectionMode = true
                            selectedSessions = sessions.map { it.id }.toSet()
                        }
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }*/
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sessions List
        if (sessions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_database_off),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No saved sessions",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Analyze some grain images to see them here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn {
                items(sessions) { session ->
                    SessionListItem(
                        session = session,
                        isSelected = selectedSessions.contains(session.id),
                        isSelectionMode = isSelectionMode,
                        onSelectionModeToggle = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) selectedSessions = emptySet()
                        },
                        onSelectionChange = { isSelected ->
                            selectedSessions = if (isSelected) {
                                selectedSessions + session.id
                            } else {
                                selectedSessions - session.id
                            }
                        },
                        onClick = {
                            if (isSelectionMode) {
                                // Toggle selection
                                selectedSessions = if (selectedSessions.contains(session.id)) {
                                    selectedSessions - session.id
                                } else {
                                    selectedSessions + session.id
                                }
                            } else {
                                // Navigate to detail
                                onNavigateToSessionDetail(session.id)
                            }
                        },
                        repository = repository
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Sessions") },
            text = {
                Text("Are you sure you want to delete ${selectedSessions.size} session(s)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                repository.deleteSessions(selectedSessions.toList())
                                selectedSessions = emptySet()
                                isSelectionMode = false
                                showDeleteDialog = false
                            } catch (e: Exception) {
                                // Handle error
                                println("❌ Failed to delete sessions: ${e.message}")
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionListItem(
    session: GrainSession,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectionModeToggle: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    repository: GrainRepository
) {
    val statistics = remember(session) {
        try {
            repository.parseStatistics(session)
        } catch (e: Exception) {
            null
        }
    }

    val imageBitmap = remember(session.imagePath) {
        ImageUtils.loadImageFromPath(session.imagePath)?.asImageBitmap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (!isSelectionMode) {
                        onSelectionModeToggle()
                        onSelectionChange(true)
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox (only show in selection mode)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectionChange,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            // Thumbnail image
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                imageBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Session thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hide_image),
                        contentDescription = "No image",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                        .format(Date(session.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                statistics?.let { stats ->
                    val scaleCalibration = repository.parseScaleCalibration(session)
                    Text(
                        text = "${stats.count} grains • D50: ${"%.2f".format(stats.d50)} ${scaleCalibration.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Arrow icon (only show when not in selection mode)
            if (!isSelectionMode) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}