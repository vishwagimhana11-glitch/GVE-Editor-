package com.gima.gve.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.repository.ProjectSummary
import com.gima.gve.ui.state.HomeStateHolder
import com.gima.gve.ui.theme.GveBrandMark
import com.gima.gve.ui.theme.GveColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    stateHolder: HomeStateHolder,
    onOpenProject: (TimelineProject) -> Unit
) {
    val state by stateHolder.uiState.collectAsStateWithLifecycle()
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var pendingRename by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ProjectSummary?>(null) }

    val importVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stateHolder.importVideoIntoNewProject(uri, onCreated = onOpenProject)
        }
    }

    Scaffold(
        containerColor = GveColors.Canvas
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GveBrandMark(size = 34.dp)
                    Column {
                        Text(
                            text = "GVE Editor",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = GveColors.TextPrimary
                        )
                        Text(
                            text = "Non-destructive multi-track video studio",
                            fontSize = 12.sp,
                            color = GveColors.TextMuted
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3F1D1D), RoundedCornerShape(10.dp))
                        .border(1.dp, GveColors.Danger, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        color = Color(0xFFFCA5A5),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { stateHolder.dismissError() }) {
                        Text("Dismiss", color = Color(0xFFFCA5A5))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        newProjectName = ""
                        showNewProjectDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GveColors.AccentPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New Project", fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { importVideoLauncher.launch(arrayOf("video/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = GveColors.AccentSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Import Video", fontWeight = FontWeight.SemiBold, color = GveColors.TextPrimary)
                }
            }

            Text(
                text = "Edits are saved automatically to private project files; original device videos remain untouched.",
                fontSize = 11.sp,
                color = GveColors.TextMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Projects",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GveColors.TextPrimary
                )
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GveColors.AccentSecondary
                    )
                }
            }

            if (!state.isLoading && state.recentProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GveColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = GveColors.TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "No projects yet",
                            fontWeight = FontWeight.SemiBold,
                            color = GveColors.TextPrimary,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Create a blank project or import a video file to begin editing.",
                            color = GveColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.recentProjects, key = { it.projectId }) { summary ->
                        RecentProjectRow(
                            summary = summary,
                            onClick = {
                                stateHolder.openProject(summary.projectId, onOpened = onOpenProject)
                            },
                            onRenameClick = {
                                renameDraft = summary.name
                                pendingRename = summary
                            },
                            onDeleteClick = { pendingDelete = summary }
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            containerColor = GveColors.SurfaceElevated,
            title = { Text("Create Project", color = GveColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Project name") },
                    placeholder = { Text("Untitled Project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewProjectDialog = false
                        stateHolder.createNewProject(newProjectName, onCreated = onOpenProject)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
                ) {
                    Text("Create", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel", color = GveColors.TextMuted)
                }
            }
        )
    }

    pendingRename?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            containerColor = GveColors.SurfaceElevated,
            title = { Text("Rename Project", color = GveColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        stateHolder.renameProject(target.projectId, renameDraft)
                        pendingRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text("Cancel", color = GveColors.TextMuted)
                }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = GveColors.SurfaceElevated,
            title = { Text("Delete Project?", color = GveColors.TextPrimary) },
            text = {
                Text(
                    text = "Remove '${target.name}' from local storage? Original source videos on your device will not be affected.",
                    color = GveColors.TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        stateHolder.deleteProject(target.projectId)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GveColors.Danger)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = GveColors.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun RecentProjectRow(
    summary: ProjectSummary,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formattedDate = remember(summary.updatedAtEpochMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(summary.updatedAtEpochMs))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GveColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = GveColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatDurationMs(summary.totalDurationMs)} · ${summary.clipCount} clips · $formattedDate",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = GveColors.TextMuted
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRenameClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename project",
                    tint = GveColors.TextMuted
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete project",
                    tint = GveColors.TextMuted
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
