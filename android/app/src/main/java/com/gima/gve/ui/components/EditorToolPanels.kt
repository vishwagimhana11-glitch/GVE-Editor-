package com.gima.gve.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gima.gve.domain.model.CropRect
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TrackType
import com.gima.gve.ui.state.EditorToolTab
import com.gima.gve.ui.theme.GveColors
import kotlin.math.roundToInt

@Composable
fun EditorToolSection(
    activeTab: EditorToolTab?,
    selectedClip: MediaClip?,
    onSelectTab: (EditorToolTab) -> Unit,
    onClosePanel: () -> Unit,
    onSplit: () -> Unit,
    onTrimHead: (Long) -> Unit,
    onTrimTail: (Long) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (ripple: Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float, Boolean, Boolean) -> Unit,
    onTransformChange: (Int, CropRect) -> Unit,
    onAddTextClip: (String) -> Unit,
    onUpdateTextClip: (String) -> Unit,
    onOpenAudioImport: () -> Unit,
    onOpenOverlayImport: () -> Unit,
    onAddOverlayTrack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = activeTab != null) {
            activeTab?.let { tab ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(GveColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tab.isSupported) tab.label else "${tab.label} · Coming Soon",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (tab.isSupported) GveColors.TextPrimary else GveColors.TextMuted
                        )
                        IconButton(onClick = onClosePanel, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close panel",
                                tint = GveColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    when (tab) {
                        EditorToolTab.EDIT -> EditClipPanel(
                            clip = selectedClip,
                            onSplit = onSplit,
                            onTrimHead = onTrimHead,
                            onTrimTail = onTrimTail,
                            onDuplicate = onDuplicate,
                            onDelete = onDelete
                        )
                        EditorToolTab.AUDIO -> AudioToolPanel(
                            clip = selectedClip,
                            onOpenAudioImport = onOpenAudioImport,
                            onVolumeChange = onVolumeChange
                        )
                        EditorToolTab.TEXT -> TextToolPanel(
                            clip = selectedClip,
                            onAddTextClip = onAddTextClip,
                            onUpdateTextClip = onUpdateTextClip
                        )
                        EditorToolTab.OVERLAY -> OverlayToolPanel(
                            onOpenOverlayImport = onOpenOverlayImport,
                            onAddOverlayTrack = onAddOverlayTrack
                        )
                        EditorToolTab.SPEED -> SpeedToolPanel(
                            clip = selectedClip,
                            onSpeedChange = onSpeedChange
                        )
                        EditorToolTab.CROP -> CropRotatePanel(
                            clip = selectedClip,
                            onTransformChange = onTransformChange
                        )
                        EditorToolTab.VOLUME -> VolumeToolPanel(
                            clip = selectedClip,
                            onVolumeChange = onVolumeChange
                        )
                        EditorToolTab.STICKERS,
                        EditorToolTab.EFFECTS,
                        EditorToolTab.FILTERS,
                        EditorToolTab.TRANSITIONS -> ComingSoonPanel(tab.label)
                    }
                }
            }
        }

        // Bottom 11-Tool Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(GveColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, GveColors.Border, RoundedCornerShape(12.dp)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(EditorToolTab.entries) { tab ->
                val isSelected = tab == activeTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .background(
                            if (isSelected) GveColors.AccentPrimary.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = iconForTool(tab),
                        contentDescription = tab.label,
                        tint = when {
                            isSelected -> GveColors.AccentSecondary
                            tab.isSupported -> GveColors.TextPrimary
                            else -> GveColors.TextMuted.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        color = when {
                            isSelected -> GveColors.AccentSecondary
                            tab.isSupported -> GveColors.TextMuted
                            else -> GveColors.TextMuted.copy(alpha = 0.55f)
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun EditClipPanel(
    clip: MediaClip?,
    onSplit: () -> Unit,
    onTrimHead: (Long) -> Unit,
    onTrimTail: (Long) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (Boolean) -> Unit
) {
    if (clip == null) {
        Text(
            text = "Select a clip on the timeline to trim, split, duplicate, or inspect source bounds.",
            fontSize = 12.sp,
            color = GveColors.TextMuted
        )
        return
    }

    Text(
        text = "${clip.label} · ${clip.durationMs}ms · Src ${clip.sourceInMs}..${clip.sourceOutMs}/${clip.sourceDurationMs}ms",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = GveColors.AccentSecondary
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(
                onClick = onSplit,
                colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Split", fontSize = 12.sp)
            }
        }
        item {
            OutlinedButton(onClick = { onTrimHead(-250L) }) { Text("Head -250ms", fontSize = 12.sp) }
        }
        item {
            OutlinedButton(onClick = { onTrimHead(250L) }) { Text("Head +250ms", fontSize = 12.sp) }
        }
        item {
            OutlinedButton(onClick = { onTrimTail(-250L) }) { Text("Tail -250ms", fontSize = 12.sp) }
        }
        item {
            OutlinedButton(onClick = { onTrimTail(250L) }) { Text("Tail +250ms", fontSize = 12.sp) }
        }
        item {
            OutlinedButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Duplicate", fontSize = 12.sp)
            }
        }
        item {
            OutlinedButton(onClick = { onDelete(false) }) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete", fontSize = 12.sp)
            }
        }
        item {
            OutlinedButton(onClick = { onDelete(true) }) { Text("Ripple Delete", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun AudioToolPanel(
    clip: MediaClip?,
    onOpenAudioImport: () -> Unit,
    onVolumeChange: (Float, Boolean, Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onOpenAudioImport,
            colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Import Audio", fontSize = 12.sp)
        }
        if (clip != null) {
            OutlinedButton(onClick = { onVolumeChange(clip.volume, !clip.isMuted, false) }) {
                Icon(
                    imageVector = if (clip.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (clip.isMuted) "Unmute Clip" else "Mute Clip", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TextToolPanel(
    clip: MediaClip?,
    onAddTextClip: (String) -> Unit,
    onUpdateTextClip: (String) -> Unit
) {
    val isEditingText = clip?.mediaType == MediaType.TEXT
    var draft by remember(clip?.id, clip?.textContent) {
        mutableStateOf(if (isEditingText) clip?.textContent.orEmpty() else "")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Enter title text", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                if (isEditingText) {
                    onUpdateTextClip(draft)
                } else {
                    onAddTextClip(draft)
                    draft = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
        ) {
            Text(if (isEditingText) "Update" else "Add Text", fontSize = 12.sp)
        }
    }
}

@Composable
private fun OverlayToolPanel(
    onOpenOverlayImport: () -> Unit,
    onAddOverlayTrack: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onOpenOverlayImport,
            colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
        ) {
            Text("Import Overlay Media", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onAddOverlayTrack) {
            Text("+ Overlay Track", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SpeedToolPanel(
    clip: MediaClip?,
    onSpeedChange: (Float) -> Unit
) {
    if (clip == null) {
        Text("Select a clip to adjust playback speed.", fontSize = 12.sp, color = GveColors.TextMuted)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { factor ->
            OutlinedButton(onClick = { onSpeedChange(factor) }) {
                Text(
                    text = "${factor}x",
                    color = if (clip.speed == factor) GveColors.AccentSecondary else GveColors.TextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
    Text(
        text = "Current speed: ${clip.speed}x · Timeline duration: ${clip.durationMs}ms",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = GveColors.TextMuted
    )
}

@Composable
private fun CropRotatePanel(
    clip: MediaClip?,
    onTransformChange: (Int, CropRect) -> Unit
) {
    if (clip == null) {
        Text("Select a clip to rotate or adjust crop bounds.", fontSize = 12.sp, color = GveColors.TextMuted)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onTransformChange((clip.rotationDegrees + 90) % 360, clip.cropRect) },
            colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
        ) {
            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Rotate 90° (${clip.rotationDegrees}°)", fontSize = 12.sp)
        }
        OutlinedButton(onClick = { onTransformChange(clip.rotationDegrees, CropRect(0f, 0f, 1f, 1f)) }) {
            Text("Full", fontSize = 12.sp)
        }
        OutlinedButton(onClick = { onTransformChange(clip.rotationDegrees, CropRect(0.1f, 0.1f, 0.9f, 0.9f)) }) {
            Text("Center 80%", fontSize = 12.sp)
        }
    }
}

@Composable
private fun VolumeToolPanel(
    clip: MediaClip?,
    onVolumeChange: (Float, Boolean, Boolean) -> Unit
) {
    if (clip == null) {
        Text("Select a clip to adjust audio gain.", fontSize = 12.sp, color = GveColors.TextMuted)
        return
    }
    val latestClip by rememberUpdatedState(clip)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "${(clip.volume * 100).roundToInt()}%",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = GveColors.TextPrimary,
            modifier = Modifier.width(44.dp)
        )
        Slider(
            value = clip.volume,
            onValueChange = { vol -> onVolumeChange(vol, latestClip.isMuted, true) },
            onValueChangeFinished = { onVolumeChange(latestClip.volume, latestClip.isMuted, false) },
            valueRange = 0f..2f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = GveColors.AccentPrimary,
                activeTrackColor = GveColors.AccentPrimary
            )
        )
        OutlinedButton(onClick = { onVolumeChange(clip.volume, !clip.isMuted, false) }) {
            Text(if (clip.isMuted) "Unmute" else "Mute", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ComingSoonPanel(toolName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$toolName requires the Phase 4 OpenGL shader & compositing pipeline and is not yet active.",
            fontSize = 12.sp,
            color = GveColors.TextMuted
        )
        OutlinedButton(onClick = {}, enabled = false) {
            Text("$toolName (Coming Soon)", fontSize = 12.sp)
        }
    }
}

private fun iconForTool(tab: EditorToolTab): ImageVector = when (tab) {
    EditorToolTab.EDIT -> Icons.Default.Tune
    EditorToolTab.AUDIO -> Icons.Default.MusicNote
    EditorToolTab.TEXT -> Icons.Default.TextFields
    EditorToolTab.STICKERS -> Icons.Default.EmojiEmotions
    EditorToolTab.EFFECTS -> Icons.Default.AutoAwesome
    EditorToolTab.FILTERS -> Icons.Default.FilterVintage
    EditorToolTab.OVERLAY -> Icons.Default.Layers
    EditorToolTab.TRANSITIONS -> Icons.Default.SwapHoriz
    EditorToolTab.SPEED -> Icons.Default.Speed
    EditorToolTab.CROP -> Icons.Default.Crop
    EditorToolTab.VOLUME -> Icons.Default.VolumeUp
}
