package com.gima.gve.rendering.media3

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TimelineConstants
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.model.TimelineTrack
import com.gima.gve.domain.model.TrackType

data class SegmentPlaybackMapping(
    val clipId: String,
    val mediaItemIndex: Int,
    val timelineStartMs: Long,
    val timelineEndMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val sourceUri: String,
    val volume: Float,
    val speed: Float,
    val isMuted: Boolean,
    val mediaType: MediaType
)

data class ExoPlayerPlaylistMapping(
    val primaryTrackId: String?,
    val mediaItems: List<MediaItem>,
    val segments: List<SegmentPlaybackMapping>,
    val hasAdditionalVideoTracks: Boolean
) {
    val signature: String
        get() = segments.joinToString("|") {
            "${it.clipId}:${it.sourceUri}:${it.sourceInMs}:${it.sourceOutMs}:${it.speed}:${it.volume}:${it.isMuted}"
        }
}

/**
 * Translates non-destructive [TimelineProject] domain segments into AndroidX Media3 (1.8.0)
 * [MediaItem] clipping descriptors and Transformer [Composition] sequences.
 *
 * Capability boundary:
 * - [toPrimaryExoPlayerPlaylist] maps the primary visible video track ([MediaType.VIDEO] clips)
 *   for sequential real-time playback via [androidx.media3.exoplayer.ExoPlayer].
 * - Simultaneous live multi-track video compositing (V1 + Overlay + Text) and hardware
 *   export are scheduled for Phase 4 Transformer/GL pipeline wiring.
 */
object Media3TimelineMapper {

    fun toMediaItem(clip: MediaClip): MediaItem {
        require(TimelineConstants.isValidSourceUri(clip.sourceUri)) {
            "Invalid source URI for clip '${clip.id}': ${clip.sourceUri}"
        }
        val builder = MediaItem.Builder()
            .setMediaId(clip.id)
            .setUri(clip.sourceUri)

        if (clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.AUDIO) {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.sourceInMs)
                .setEndPositionMs(clip.sourceOutMs)
                .build()
            builder.setClippingConfiguration(clipping)
        }

        return builder.build()
    }

    fun toPrimaryExoPlayerPlaylist(project: TimelineProject): ExoPlayerPlaylistMapping {
        val visibleVideoTracks = project.tracks
            .filter { it.type == TrackType.VIDEO && it.isVisible }
            .sortedBy { it.orderIndex }

        val primaryTrack = visibleVideoTracks.firstOrNull()
        val hasAdditional = project.tracks.any { track ->
            track.id != primaryTrack?.id &&
                (track.type == TrackType.VIDEO || track.type == TrackType.OVERLAY || track.type == TrackType.TEXT) &&
                track.isVisible &&
                project.clipsForTrack(track.id).isNotEmpty()
        }

        if (primaryTrack == null) {
            return ExoPlayerPlaylistMapping(
                primaryTrackId = null,
                mediaItems = emptyList(),
                segments = emptyList(),
                hasAdditionalVideoTracks = hasAdditional
            )
        }

        val clips = project.clipsForTrack(primaryTrack.id)
            .filter { it.mediaType == MediaType.VIDEO }

        val items = ArrayList<MediaItem>(clips.size)
        val mappings = ArrayList<SegmentPlaybackMapping>(clips.size)

        clips.forEachIndexed { index, clip ->
            items.add(toMediaItem(clip))
            mappings.add(
                SegmentPlaybackMapping(
                    clipId = clip.id,
                    mediaItemIndex = index,
                    timelineStartMs = clip.timelineStartMs,
                    timelineEndMs = clip.timelineEndMs,
                    sourceInMs = clip.sourceInMs,
                    sourceOutMs = clip.sourceOutMs,
                    sourceUri = clip.sourceUri,
                    volume = clip.volume,
                    speed = clip.speed,
                    isMuted = clip.isMuted || primaryTrack.isMuted,
                    mediaType = clip.mediaType
                )
            )
        }

        return ExoPlayerPlaylistMapping(
            primaryTrackId = primaryTrack.id,
            mediaItems = items,
            segments = mappings,
            hasAdditionalVideoTracks = hasAdditional
        )
    }

    @OptIn(UnstableApi::class)
    fun toEditedMediaItem(clip: MediaClip, trackMuted: Boolean, projectFps: Int): EditedMediaItem {
        val mediaItem = toMediaItem(clip)
        val builder = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(clip.isMuted || trackMuted || clip.mediaType == MediaType.IMAGE)

        if (clip.mediaType == MediaType.IMAGE) {
            builder.setDurationUs(clip.durationMs * 1_000L)
            builder.setFrameRate(projectFps)
        } else {
            builder.setDurationUs(clip.sourceDurationMs * 1_000L)
        }
        return builder.build()
    }

    /**
     * Builds a Media3 1.8.0 [Composition] for video/image/audio tracks with gap padding (`addGap`).
     * Text tracks are excluded until Phase 4 GL text overlay rendering is added.
     */
    @OptIn(UnstableApi::class)
    fun toComposition(project: TimelineProject): Composition? {
        val activeTracks = project.tracks
            .sortedBy { it.orderIndex }
            .filter { track ->
                track.type != TrackType.TEXT &&
                    project.clipsForTrack(track.id).isNotEmpty() &&
                    (track.type == TrackType.AUDIO || track.isVisible)
            }

        if (activeTracks.isEmpty()) return null

        val sequences = activeTracks.map { track: TimelineTrack ->
            // Media3 1.8.0: track types are inferred from items. Revisit for Phase 4 export (leading gaps, forced audio track) before enabling MP4 export.
            val builder = EditedMediaItemSequence.Builder()
            var cursorMs = 0L

            project.clipsForTrack(track.id).forEach { clip ->
                val gapMs = clip.timelineStartMs - cursorMs
                if (gapMs > 0L) {
                    builder.addGap(gapMs * 1_000L)
                }
                builder.addItem(
                    toEditedMediaItem(
                        clip = clip,
                        trackMuted = track.isMuted,
                        projectFps = project.settings.fps
                    )
                )
                cursorMs = clip.timelineEndMs
            }

            builder.build()
        }

        return Composition.Builder(sequences).build()
    }
}
