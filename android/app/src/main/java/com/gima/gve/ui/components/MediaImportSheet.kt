package com.gima.gve.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gima.gve.data.media.MediaThumbnailCache
import com.gima.gve.domain.model.MediaAsset
import com.gima.gve.domain.model.MediaType
import com.gima.gve.ui.theme.GveColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaImportSheet(
    projectMedia: List<MediaAsset>,
    recentMedia: List<MediaAsset>,
    thumbnailCache: MediaThumbnailCache,
    onDismiss: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickImage: () -> Unit,
    onSelectAsset: (MediaAsset) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }
    val displayedAssets = if (activeTab == 0) projectMedia else recentMedia

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GveColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Media Library",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = GveColors.TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickVideo,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GveColors.AccentPrimary)
                ) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Video", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onPickAudio,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Audio", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onPickImage,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Image", fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { activeTab = 0 }) {
                    Text(
                        text = "Project Media (${projectMedia.size})",
                        fontSize = 12.sp,
                        color = if (activeTab == 0) GveColors.AccentSecondary else GveColors.TextMuted
                    )
                }
                OutlinedButton(onClick = { activeTab = 1 }) {
                    Text(
                        text = "Recent Media (${recentMedia.size})",
                        fontSize = 12.sp,
                        color = if (activeTab == 1) GveColors.AccentSecondary else GveColors.TextMuted
                    )
                }
            }

            if (displayedAssets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(GveColors.Canvas, RoundedCornerShape(10.dp))
                        .border(1.dp, GveColors.Border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No imported assets yet. Use Video, Audio, or Image above.",
                        fontSize = 12.sp,
                        color = GveColors.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedAssets, key = { "${it.id}-${it.uri}" }) { asset ->
                        MediaAssetRow(
                            asset = asset,
                            thumbnailCache = thumbnailCache,
                            onClick = {
                                onSelectAsset(asset)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaAssetRow(
    asset: MediaAsset,
    thumbnailCache: MediaThumbnailCache,
    onClick: () -> Unit
) {
    var thumb by remember(asset.uri) {
        mutableStateOf<Bitmap?>(thumbnailCache.getCachedThumbnail(asset.uri, 0L))
    }

    LaunchedEffect(asset.uri, asset.mediaType) {
        if (asset.mediaType == MediaType.VIDEO || asset.mediaType == MediaType.IMAGE) {
            thumb = thumbnailCache.loadThumbnail(asset.uri, asset.mediaType, 0L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GveColors.SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, GveColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(GveColors.Canvas),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = when (asset.mediaType) {
                        MediaType.VIDEO -> Icons.Default.VideoFile
                        MediaType.AUDIO -> Icons.Default.AudioFile
                        MediaType.IMAGE -> Icons.Default.Image
                        MediaType.TEXT -> Icons.Default.Image
                    },
                    contentDescription = null,
                    tint = GveColors.AccentSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = GveColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${asset.mediaType.name} · ${asset.totalDurationMs / 1000}s" +
                    if (asset.width != null && asset.height != null) " · ${asset.width}x${asset.height}" else "",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = GveColors.TextMuted
            )
        }
    }
}
