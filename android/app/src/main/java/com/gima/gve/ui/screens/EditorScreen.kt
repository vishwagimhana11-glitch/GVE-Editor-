package com.gima.gve.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gima.gve.data.media.MediaThumbnailCache
import com.gima.gve.data.repository.AutosaveStatus
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TrackType
import com.gima.gve.rendering.media3.ExoPlayerPreviewController
import com.gima.gve.rendering.media3.Media3TimelineMapper
import com.gima.gve.rendering.media3.PreviewQuality
import com.gima.gve.rendering.media3.PreviewScaleMode
import com.gima.gve.ui.components.EditorToolSection
import com.gima.gve.ui.components.MediaImportSheet
import com.gima.gve.ui.components.TimelineCanvas
import com.gima.gve.ui.state.EditorStateHolder
import com.gima.gve.ui.state.EditorToolTab
import com.gima.gve.ui.theme.GveBrandMark
import com.gima.gve.ui.theme.GveColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    stateHolder: EditorStateHolder,
    thumbnailCache: MediaThumbnailCache,
    onExitToHome: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by stateHolder.uiState.collectAsStateWithLifecycle()

    var isPlaying by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var targetImportTrackId by remember { mutableStateOf<String?>(null) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }

    val previewController = remember(context) {
        ExoPlayerPreviewController(
            context = context,
            onPlaybackError = { err ->
                playbackError = err
                isPlaying = false
            }
        )
    }

    val playlistMapping = remember(state.project) {
        Media3TimelineMapper.toPrimaryExoPlayerPlaylist(state.project)
    }

    LaunchedEffect(previewController, state.project) {
        previewController.syncProject(state.project, restoreTimelineMs = state.playheadMs)
    }

    LaunchedEffect(state.previewQuality) {
        previewController.setPreviewQuality(state.previewQuality)
    }

    // Release ExoPlayer and cached thumbnails deterministically when leaving EditorScreen.
    DisposableEffect(previewController, thumbnailCache) {
        onDispose {
            previewController.release()
            thumbnailCache.clear()
        }
    }

    // Pause ExoPlayer and trigger save automatically on background/stop lifecycle events.
    DisposableEffect(lifecycleOwner, previewController, stateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                previewController.pause()
                isPlaying = false
                stateHolder.saveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Poll ExoPlayer position into playheadMs while playback is active (including buffering).
    LaunchedEffect(isPlaying, previewController) {
        while (isActive && isPlaying) {
            if (!previewController.isPlaybackActive) {
                isPlaying = false
                break
            }
            previewController.currentTimelinePositionMs()?.let { pos ->
                stateHolder.seekTo(pos)
            }
            delay(33L)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stateHolder.importMediaUri(uri, forcedType = MediaType.VIDEO, preferredTrackId = targetImportTrackId)
        }
        targetImportTrackId = null
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stateHolder.importMediaUri(uri, forcedType = MediaType.AUDIO, preferredTrackId = targetImportTrackId)
        }
        targetImportTrackId = null
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stateHolder.importMediaUri(uri, forcedType = MediaType.IMAGE, preferredTrackId = targetImportTrackId)
        }
        targetImportTrackId = null
    }

    val handleBack: () -> Unit = {
        if (state.isFullscreenPreview) {
            stateHolder.setFullscreenPreview(false)
        } else {
            previewController.pause()
            isPlaying = false
            scope.launch {
                stateHolder.flushAndClose()
                onExitToHome()
            }
        }
    }

    BackHandler(onBack = handleBack)

    val totalDurationMs = state.project.totalDurationMs.coerceAtLeast(1_000L)
    val isPlayheadOnVideo = remember(playlistMapping, state.playheadMs) {
        playlistMapping.segments.any { seg ->
            state.playheadMs >= seg.timelineStartMs && state.playheadMs < seg.timelineEndMs
        }
    }
    val activeTextClip = remember(state.project, state.playheadMs) {
        state.project.clips.firstOrNull { clip ->
            val track = state.project.findTrack(clip.trackId)
            clip.mediaType == MediaType.TEXT &&
                track?.isVisible == true &&
                state.playheadMs >= clip.timelineStartMs &&
                state.playheadMs <= clip.timelineEndMs
        }
    }

    val autosaveLabel = when (state.autosaveStatus) {
        AutosaveStatus.Idle -> "Ready"
        AutosaveStatus.Saving -> "Saving..."
        is AutosaveStatus.Saved -> "Saved"
        is AutosaveStatus.Failed -> "Save error"
    }

    val togglePlayback: () -> Unit = {
        if (isPlaying) {
            previewController.pause()
            isPlaying = false
        } else {
            val startPos = if (state.playheadMs >= state.project.totalDurationMs && playlistMapping.segments.isNotEmpty()) {
                0L.also { stateHolder.seekTo(0L) }
            } else {
                state.playheadMs
            }
            previewController.play(startPos)
            isPlaying = previewController.isPlaybackActive
        }
    }

    val seekTimeline: (Long) -> Unit = { ms ->
        isPlaying = false
        previewController.pause()
        stateHolder.seekTo(ms)
        previewController.seekToTimelineMs(ms)
    }

    Scaffold(
        containerColor = GveColors.Canvas
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // A. Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GveColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = handleBack, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = GveColors.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    GveBrandMark(size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                renameDraft = state.project.name
                                showRenameDialog = true
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.project.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GveColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename project",
                                tint = GveColors.TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "$autosaveLabel · ${state.project.clips.size} clips",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (state.autosaveStatus is AutosaveStatus.Failed) GveColors.Danger else GveColors.TextMuted
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { stateHolder.undo() },
                        enabled = state.history.canUndo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (state.history.canUndo) GveColors.TextPrimary else GveColors.Border,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { stateHolder.redo() },
                        enabled = state.history.canRedo,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (state.history.canRedo) GveColors.TextPrimary else GveColors.Border,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { stateHolder.saveNow() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save now",
                            tint = GveColors.AccentSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Box {
                        Text(
                            text = state.previewQuality.label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GveColors.TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GveColors.SurfaceElevated)
                                .border(1.dp, GveColors.Border, RoundedCornerShape(6.dp))
                                .clickable { showQualityMenu = true }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        )
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false }
                        ) {
                            PreviewQuality.entries.forEach { quality ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${quality.label} (${quality.maxWidth}x${quality.maxHeight})",
                                            color = if (quality == state.previewQuality) GveColors.AccentSecondary else GveColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        showQualityMenu = false
                                        stateHolder.setPreviewQuality(quality)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { stateHolder.setExportSheetOpen(true) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GveColors.AccentPrimary,
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Error banner
            val combinedError = state.lastErrorMessage ?: playbackError
            combinedError?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3F1D1D), RoundedCornerShape(8.dp))
                        .border(1.dp, GveColors.Danger, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            stateHolder.dismissError()
                            playbackError = null
                        }
                    ) {
                        Text("Dismiss", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                }
            }

            // Scrollable middle workspace (Preview + Timeline) leaving Bottom Tools pinned cleanly
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // B. Central Video Preview
                PreviewSurfaceCard(
                    hasMedia = playlistMapping.mediaItems.isNotEmpty(),
                    showVideoFrame = playlistMapping.mediaItems.isNotEmpty() && isPlayheadOnVideo,
                    isImportingMedia = state.isImportingMedia,
                    activeTextOverlay = activeTextClip?.textContent,
                    scaleMode = state.previewScaleMode,
                    isPlaying = isPlaying,
                    playheadMs = state.playheadMs,
                    totalDurationMs = state.project.totalDurationMs,
                    previewController = previewController,
                    onTogglePlay = togglePlayback,
                    onToggleScaleMode = { stateHolder.togglePreviewScaleMode() },
                    onToggleFullscreen = { stateHolder.setFullscreenPreview(true) },
                    onOpenMediaImport = {
                        targetImportTrackId = null
                        stateHolder.setMediaImportOpen(true)
                    }
                )

                // Playhead scrub bar
                Slider(
                    value = state.playheadMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                    onValueChange = { seekTimeline(it.toLong()) },
                    valueRange = 0f..totalDurationMs.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = GveColors.AccentSecondary,
                        activeTrackColor = GveColors.AccentPrimary,
                        inactiveTrackColor = GveColors.Border
                    )
                )

                if (playlistMapping.hasAdditionalVideoTracks) {
                    Text(
                        text = "Preview plays the primary visible video track via ExoPlayer. Live multi-track GL video compositing is scheduled for Phase 4.",
                        fontSize = 11.sp,
                        color = GveColors.TextMuted
                    )
                }

                // C. Multi-Track Editing Timeline
                TimelineCanvas(
                    project = state.project,
                    selectedClipId = state.selectedClipId,
                    playheadMs = state.playheadMs,
                    zoomPxPerSec = state.timelineZoomPxPerSec,
                    thumbnailCache = thumbnailCache,
                    onSelectClip = { stateHolder.selectClip(it) },
                    onSeek = seekTimeline,
                    onMoveClip = { clipId, trackId, startMs, isDragging ->
                        stateHolder.moveClip(clipId, trackId, startMs, isDragging)
                    },
                    onZoomIn = { stateHolder.zoomIn() },
                    onZoomOut = { stateHolder.zoomOut() },
                    onAddTrack = { stateHolder.addTrack(it) },
                    onToggleTrackMute = { stateHolder.toggleTrackMute(it) },
                    onToggleTrackLock = { stateHolder.toggleTrackLock(it) },
                    onToggleTrackVisible = { stateHolder.toggleTrackVisible(it) },
                    onOpenImportForTrack = { trackId ->
                        targetImportTrackId = trackId
                        val track = state.project.findTrack(trackId)
                        when (track?.type) {
                            TrackType.AUDIO -> audioPickerLauncher.launch(arrayOf("audio/*"))
                            TrackType.TEXT -> stateHolder.selectToolTab(EditorToolTab.TEXT)
                            TrackType.OVERLAY -> stateHolder.setMediaImportOpen(true)
                            else -> stateHolder.setMediaImportOpen(true)
                        }
                    }
                )
            }

            // D. Bottom Editing Tools + Collapsible Panel
            EditorToolSection(
                activeTab = state.activeToolTab,
                selectedClip = state.selectedClip,
                onSelectTab = { stateHolder.selectToolTab(it) },
                onClosePanel = { stateHolder.closeToolPanel() },
                onSplit = { stateHolder.splitSelectedClipAtPlayhead() },
                onTrimHead = { delta -> stateHolder.trimSelectedClipHead(delta) },
                onTrimTail = { delta -> stateHolder.trimSelectedClipTail(delta) },
                onDuplicate = { stateHolder.duplicateSelectedClip() },
                onDelete = { ripple -> stateHolder.deleteSelectedClip(ripple) },
                onSpeedChange = { speed -> stateHolder.setSelectedClipSpeed(speed) },
                onVolumeChange = { vol, muted, dragging -> stateHolder.setSelectedClipVolume(vol, muted, dragging) },
                onTransformChange = { rot, crop -> stateHolder.setSelectedClipTransform(rot, crop) },
                onAddTextClip = { text -> stateHolder.addTextClip(text) },
                onUpdateTextClip = { text -> stateHolder.updateSelectedTextClip(text) },
                onOpenAudioImport = {
                    targetImportTrackId = null
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                },
                onOpenOverlayImport = {
                    val overlayTrack = state.project.tracks.firstOrNull { it.type == TrackType.OVERLAY }
                    targetImportTrackId = overlayTrack?.id
                    stateHolder.setMediaImportOpen(true)
                },
                onAddOverlayTrack = { stateHolder.addTrack(TrackType.OVERLAY) }
            )
        }
    }

    if (state.isMediaImportOpen) {
        MediaImportSheet(
            projectMedia = state.project.mediaAssets,
            recentMedia = state.recentMedia,
            thumbnailCache = thumbnailCache,
            onDismiss = { stateHolder.setMediaImportOpen(false) },
            onPickVideo = { videoPickerLauncher.launch(arrayOf("video/*")) },
            onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
            onPickImage = { imagePickerLauncher.launch(arrayOf("image/*")) },
            onSelectAsset = { asset ->
                stateHolder.placeAssetOnTimeline(asset, preferredTrackId = targetImportTrackId)
                targetImportTrackId = null
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
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
                        stateHolder.renameProject(renameDraft)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = GveColors.TextMuted)
                }
            }
        )
    }

    if (state.isExportSheetOpen) {
        val composition = remember(state.project) {
            Media3TimelineMapper.toComposition(state.project)
        }
        AlertDialog(
            onDismissRequest = { stateHolder.setExportSheetOpen(false) },
            containerColor = GveColors.SurfaceElevated,
            title = { Text("Export Readiness", color = GveColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Project: ${state.project.name} (${state.project.settings.width}x${state.project.settings.height} @ ${state.project.settings.fps}fps)",
                        fontSize = 13.sp,
                        color = GveColors.TextPrimary
                    )
                    Text(
                        text = if (composition != null) {
                            "Media3 1.8.0 Composition ready: ${composition.sequences.size} active track sequence(s), total duration ${formatTimecode(state.project.totalDurationMs)}."
                        } else {
                            "Timeline has no exportable video, image, or audio clips yet."
                        },
                        fontSize = 12.sp,
                        color = GveColors.AccentSecondary
                    )
                    Text(
                        text = "Hardware Media3 Transformer encoding, MP4 file output, and multi-track GL/text rendering are scheduled for Phase 4. Original source media files remain unmodified.",
                        fontSize = 12.sp,
                        color = GveColors.TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { stateHolder.setExportSheetOpen(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    if (state.isFullscreenPreview) {
        Dialog(
            onDismissRequest = { stateHolder.setFullscreenPreview(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.project.name} · ${state.previewQuality.label}",
                        color = GveColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { stateHolder.togglePreviewScaleMode() }) {
                            Text(state.previewScaleMode.label, color = GveColors.AccentSecondary)
                        }
                        IconButton(onClick = { stateHolder.setFullscreenPreview(false) }) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Exit fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlistMapping.mediaItems.isNotEmpty() && isPlayheadOnVideo) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    player = previewController.player
                                }
                            },
                            update = { view ->
                                if (view.player !== previewController.player) {
                                    view.player = previewController.player
                                }
                                view.resizeMode = if (state.previewScaleMode == PreviewScaleMode.FIT) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                } else {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    activeTextClip?.textContent?.let { txt ->
                        Text(
                            text = txt,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = state.playheadMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                        onValueChange = { seekTimeline(it.toLong()) },
                        valueRange = 0f..totalDurationMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = GveColors.AccentSecondary,
                            activeTrackColor = GveColors.AccentPrimary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = togglePlayback,
                            enabled = playlistMapping.mediaItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                        Text(
                            text = "${formatTimecode(state.playheadMs)} / ${formatTimecode(state.project.totalDurationMs)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = GveColors.AccentSecondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewSurfaceCard(
    hasMedia: Boolean,
    showVideoFrame: Boolean,
    isImportingMedia: Boolean,
    activeTextOverlay: String?,
    scaleMode: PreviewScaleMode,
    isPlaying: Boolean,
    playheadMs: Long,
    totalDurationMs: Long,
    previewController: ExoPlayerPreviewController,
    onTogglePlay: () -> Unit,
    onToggleScaleMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenMediaImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GveColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (showVideoFrame) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = previewController.player
                        }
                    },
                    update = { view ->
                        if (view.player !== previewController.player) {
                            view.player = previewController.player
                        }
                        view.resizeMode = if (scaleMode == PreviewScaleMode.FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!hasMedia && activeTextOverlay == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No video clips on timeline",
                        color = GveColors.TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenMediaImport) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import Media", fontSize = 12.sp)
                    }
                }
            }

            if (activeTextOverlay != null) {
                Text(
                    text = activeTextOverlay,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (isImportingMedia) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GveColors.AccentSecondary)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onTogglePlay,
                    enabled = hasMedia,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GveColors.AccentPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPlaying) "Pause" else "Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onOpenMediaImport,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Media", fontSize = 12.sp)
                }
            }

            Text(
                text = "${formatTimecode(playheadMs)} / ${formatTimecode(totalDurationMs)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = GveColors.AccentSecondary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleScaleMode)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Toggle Fit/Fill",
                        tint = GveColors.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = scaleMode.label,
                        fontSize = 11.sp,
                        color = GveColors.TextMuted
                    )
                }
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen preview",
                        tint = GveColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimecode(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val minutes = clamped / 60_000L
    val seconds = (clamped % 60_000L) / 1000L
    val millis = clamped % 1000L
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
}
