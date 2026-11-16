package com.gima.gve.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gima.gve.data.media.MediaThumbnailCache
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.model.TimelineTrack
import com.gima.gve.domain.model.TrackType
import com.gima.gve.ui.theme.GveColors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun TimelineCanvas(
    project: TimelineProject,
    selectedClipId: String?,
    playheadMs: Long,
    zoomPxPerSec: Float,
    thumbnailCache: MediaThumbnailCache,
    onSelectClip: (String?) -> Unit,
    onSeek: (Long) -> Unit,
    onMoveClip: (clipId: String, trackId: String, newStartMs: Long, isDragging: Boolean) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onAddTrack: (TrackType) -> Unit,
    onToggleTrackMute: (String) -> Unit,
    onToggleTrackLock: (String) -> Unit,
    onToggleTrackVisible: (String) -> Unit,
    onOpenImportForTrack: (String) -> Unit
) {
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    var showAddTrackMenu by remember { mutableStateOf(false) }

    val totalDurationMs = project.totalDurationMs.coerceAtLeast(8_000L)
    val spanMs = totalDurationMs + 4_000L
    val pxPerSecDp = zoomPxPerSec.dp
    val pxPerSecPx = with(density) { pxPerSecDp.toPx() }
    val timelineWidthDp = ((spanMs / 1000f) * zoomPxPerSec).dp.coerceAtLeast(360.dp)

    val sortedTracks = remember(project.tracks) {
        project.tracks.sortedBy { it.orderIndex }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GveColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp))
    ) {
        // Timeline header controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Timeline · Long-press clip to drag",
                fontSize = 11.sp,
                color = GveColors.TextMuted
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onZoomOut, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom out timeline",
                        tint = GveColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "${zoomPxPerSec.roundToInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = GveColors.TextMuted
                )
                IconButton(onClick = onZoomIn, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom in timeline",
                        tint = GveColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box {
                    TextButton(onClick = { showAddTrackMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = GveColors.AccentSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Track", fontSize = 12.sp, color = GveColors.AccentSecondary)
                    }
                    DropdownMenu(
                        expanded = showAddTrackMenu,
                        onDismissRequest = { showAddTrackMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Video Track") },
                            onClick = {
                                showAddTrackMenu = false
                                onAddTrack(TrackType.VIDEO)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Audio Track") },
                            onClick = {
                                showAddTrackMenu = false
                                onAddTrack(TrackType.AUDIO)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Overlay Track") },
                            onClick = {
                                showAddTrackMenu = false
                                onAddTrack(TrackType.OVERLAY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Text Track") },
                            onClick = {
                                showAddTrackMenu = false
                                onAddTrack(TrackType.TEXT)
                            }
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed Left Track Headers
            Column(
                modifier = Modifier
                    .width(92.dp)
                    .background(GveColors.Canvas)
                    .border(1.dp, GveColors.Border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("TRACKS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = GveColors.TextMuted)
                }

                sortedTracks.forEach { track ->
                    TrackHeaderCell(
                        track = track,
                        onToggleMute = { onToggleTrackMute(track.id) },
                        onToggleLock = { onToggleTrackLock(track.id) },
                        onToggleVisible = { onToggleTrackVisible(track.id) },
                        onAddClip = { onOpenImportForTrack(track.id) }
                    )
                }
            }

            // Scrollable Multi-Track Lane Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
            ) {
                Column(modifier = Modifier.width(timelineWidthDp)) {
                    // Time Ruler
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(Color(0xFF11131A))
                            .pointerInput(zoomPxPerSec, totalDurationMs) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val ms = ((offset.x / pxPerSecPx) * 1000f).roundToLong()
                                        onSeek(ms.coerceIn(0L, project.totalDurationMs))
                                    },
                                    onDrag = { change, _ ->
                                        val ms = ((change.position.x / pxPerSecPx) * 1000f).roundToLong()
                                        onSeek(ms.coerceIn(0L, project.totalDurationMs))
                                    }
                                )
                            }
                    ) {
                        val stepMs = if (zoomPxPerSec >= 100f) 1_000L else 2_000L
                        var t = 0L
                        while (t <= spanMs) {
                            val xDp = ((t / 1000f) * zoomPxPerSec).dp
                            Text(
                                text = "${t / 1000}s",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = GveColors.TextMuted,
                                modifier = Modifier
                                    .offset(x = xDp)
                                    .padding(start = 3.dp, top = 4.dp)
                            )
                            t += stepMs
                        }
                    }

                    // Track Lanes
                    sortedTracks.forEach { track ->
                        val trackClips = project.clipsForTrack(track.id)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(if (track.isLocked) Color(0xFF0C0E13) else GveColors.Canvas)
                                .border(0.5.dp, Color(0xFF1F2430))
                                .clickable { onSelectClip(null) }
                        ) {
                            trackClips.forEach { clip ->
                                ClipBlock(
                                    clip = clip,
                                    trackLocked = track.isLocked,
                                    isSelected = clip.id == selectedClipId,
                                    zoomPxPerSec = zoomPxPerSec,
                                    pxPerSecPx = pxPerSecPx,
                                    thumbnailCache = thumbnailCache,
                                    onSelect = { onSelectClip(clip.id) },
                                    onDragMove = { newStartMs, isDragging ->
                                        onMoveClip(clip.id, track.id, newStartMs, isDragging)
                                    }
                                )
                            }
                        }
                    }
                }

                // Vertical Playhead Overlay
                val playheadXDp = ((playheadMs / 1000f) * zoomPxPerSec).dp
                Box(
                    modifier = Modifier
                        .offset(x = playheadXDp)
                        .width(2.dp)
                        .height((26 + sortedTracks.size * 60).dp)
                        .background(GveColors.AccentSecondary)
                )
            }
        }
    }
}

@Composable
private fun TrackHeaderCell(
    track: TimelineTrack,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleVisible: () -> Unit,
    onAddClip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .border(0.5.dp, Color(0xFF1F2430))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GveColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add clip to ${track.name}",
                tint = GveColors.AccentSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onAddClip)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (track.type != TrackType.AUDIO) {
                Icon(
                    imageVector = if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle visibility",
                    tint = if (track.isVisible) GveColors.TextMuted else GveColors.Danger,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onToggleVisible)
                )
            }
            if (track.type == TrackType.AUDIO || track.type == TrackType.VIDEO || track.type == TrackType.OVERLAY) {
                Icon(
                    imageVector = if (track.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Toggle mute",
                    tint = if (track.isMuted) GveColors.Danger else GveColors.TextMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onToggleMute)
                )
            }
            Icon(
                imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Toggle lock",
                tint = if (track.isLocked) GveColors.AudioAmber else GveColors.TextMuted,
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onToggleLock)
            )
        }
    }
}

@Composable
private fun ClipBlock(
    clip: MediaClip,
    trackLocked: Boolean,
    isSelected: Boolean,
    zoomPxPerSec: Float,
    pxPerSecPx: Float,
    thumbnailCache: MediaThumbnailCache,
    onSelect: () -> Unit,
    onDragMove: (newStartMs: Long, isDragging: Boolean) -> Unit
) {
    val startDp = ((clip.timelineStartMs / 1000f) * zoomPxPerSec).dp
    val widthDp = max(28f, (clip.durationMs / 1000f) * zoomPxPerSec).dp

    var thumbBitmap by remember(clip.sourceUri, clip.sourceInMs) {
        mutableStateOf<Bitmap?>(thumbnailCache.getCachedThumbnail(clip.sourceUri, clip.sourceInMs))
    }
    var waveformPeaks by remember(clip.sourceUri) {
        mutableStateOf<List<Float>>(thumbnailCache.getCachedWaveform(clip.sourceUri).orEmpty())
    }

    LaunchedEffect(clip.sourceUri, clip.sourceInMs, clip.mediaType) {
        if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.IMAGE) {
            thumbBitmap = thumbnailCache.loadThumbnail(clip.sourceUri, clip.mediaType, clip.sourceInMs)
        } else if (clip.mediaType == MediaType.AUDIO) {
            waveformPeaks = thumbnailCache.loadWaveform(clip.sourceUri, barCount = 28)
        }
    }

    var dragAccumulatedPx by remember(clip.id) { mutableFloatStateOf(0f) }
    var dragOriginStartMs by remember(clip.id) { androidx.compose.runtime.mutableLongStateOf(clip.timelineStartMs) }

    val fillColor = when (clip.mediaType) {
        MediaType.VIDEO -> GveColors.TrackVideo
        MediaType.IMAGE -> GveColors.TrackOverlay
        MediaType.AUDIO -> GveColors.TrackAudio
        MediaType.TEXT -> GveColors.TrackText
    }
    val borderColor = if (isSelected) GveColors.AccentPrimary else GveColors.Border

    Box(
        modifier = Modifier
            .offset { IntOffset(startDp.roundToPx(), 0) }
            .padding(vertical = 5.dp)
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(fillColor)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .pointerInput(clip.id, trackLocked, pxPerSecPx) {
                if (trackLocked) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onSelect()
                        dragAccumulatedPx = 0f
                        dragOriginStartMs = clip.timelineStartMs
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatedPx += dragAmount.x
                        val deltaMs = ((dragAccumulatedPx / pxPerSecPx) * 1000f).roundToLong()
                        onDragMove((dragOriginStartMs + deltaMs).coerceAtLeast(0L), true)
                    },
                    onDragEnd = {
                        val deltaMs = ((dragAccumulatedPx / pxPerSecPx) * 1000f).roundToLong()
                        onDragMove((dragOriginStartMs + deltaMs).coerceAtLeast(0L), false)
                    }
                )
            }
    ) {
        if (thumbBitmap != null) {
            Image(
                bitmap = thumbBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier.fillMaxSize()
            )
        } else if (clip.mediaType == MediaType.AUDIO && waveformPeaks.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp)) {
                val barWidth = (size.width / waveformPeaks.size).coerceAtLeast(2f)
                waveformPeaks.forEachIndexed { index, amp ->
                    val barH = size.height * amp
                    val x = index * barWidth
                    val top = (size.height - barH) / 2f
                    drawLine(
                        color = GveColors.AudioAmber.copy(alpha = 0.48f),
                        start = Offset(x, top),
                        end = Offset(x, top + barH),
                        strokeWidth = (barWidth * 0.6f).coerceAtLeast(1.5f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = clip.textContent ?: clip.label.ifBlank { clip.id },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${clip.durationMs / 1000f}s" + if (clip.speed != 1.0f) " · ${clip.speed}x" else "",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}
