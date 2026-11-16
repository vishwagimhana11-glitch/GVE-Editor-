package com.gima.gve.rendering.media3

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.gima.gve.domain.model.TimelineProject

enum class PreviewQuality(val label: String, val maxWidth: Int, val maxHeight: Int) {
    LOW_360P("360p", 640, 360),
    BALANCED_720P("720p", 1280, 720),
    HIGH_1080P("1080p", 1920, 1080)
}

enum class PreviewScaleMode(val label: String) {
    FIT("Fit"),
    FILL("Fill")
}

/**
 * Lifecycle-aware [ExoPlayer] wrapper for sequential playback of the primary video track.
 *
 * Maps timeline positions (`0..totalDurationMs`) to clipped [MediaItem] segments and offsets,
 * applies [PreviewQuality] max-video-size constraints, playback speed, and clip volume.
 */
class ExoPlayerPreviewController(
    context: Context,
    private val onPlaybackError: (String) -> Unit = {}
) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private var currentMapping: ExoPlayerPlaylistMapping = ExoPlayerPlaylistMapping(
        primaryTrackId = null,
        mediaItems = emptyList(),
        segments = emptyList(),
        hasAdditionalVideoTracks = false
    )
    private var lastSignature: String = ""

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            onPlaybackError(error.message ?: "Media3 playback error (${error.errorCodeName})")
        }
    }

    init {
        player.addListener(listener)
        setPreviewQuality(PreviewQuality.BALANCED_720P)
    }

    fun setPreviewQuality(quality: PreviewQuality) {
        val params = player.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(quality.maxWidth, quality.maxHeight)
            .build()
        player.trackSelectionParameters = params
    }

    fun syncProject(project: TimelineProject): ExoPlayerPlaylistMapping {
        val mapping = Media3TimelineMapper.toPrimaryExoPlayerPlaylist(project)
        val sig = mapping.signature
        currentMapping = mapping

        if (sig != lastSignature) {
            lastSignature = sig
            val wasPlaying = player.isPlaying
            if (mapping.mediaItems.isEmpty()) {
                player.stop()
                player.clearMediaItems()
            } else {
                player.setMediaItems(mapping.mediaItems, /* resetPosition = */ true)
                player.prepare()
                if (wasPlaying) {
                    player.play()
                }
            }
        }
        return mapping
    }

    fun isPlayheadInsideVideoSegment(timelineMs: Long): Boolean =
        currentMapping.segments.any { seg ->
            timelineMs >= seg.timelineStartMs && timelineMs < seg.timelineEndMs
        }

    fun seekToTimelineMs(timelineMs: Long) {
        val segments = currentMapping.segments
        if (segments.isEmpty()) return

        val activeSegment = segments.firstOrNull { seg ->
            timelineMs >= seg.timelineStartMs && timelineMs < seg.timelineEndMs
        }

        if (activeSegment != null) {
            val timelineOffsetMs = (timelineMs - activeSegment.timelineStartMs).coerceAtLeast(0L)
            val sourceOffsetMs = (timelineOffsetMs * activeSegment.speed).toLong()
            applySegmentAudioAndSpeed(activeSegment)
            player.seekTo(activeSegment.mediaItemIndex, sourceOffsetMs)
        } else {
            if (player.isPlaying) {
                player.pause()
            }
            val nextSegment = segments.firstOrNull { it.timelineStartMs >= timelineMs }
            if (nextSegment != null) {
                player.seekTo(nextSegment.mediaItemIndex, 0L)
            } else {
                val last = segments.last()
                player.seekTo(last.mediaItemIndex, (last.sourceOutMs - last.sourceInMs).coerceAtLeast(0L))
            }
        }
    }

    fun currentTimelinePositionMs(): Long? {
        val segments = currentMapping.segments
        val idx = player.currentMediaItemIndex
        if (idx !in segments.indices) return null
        val segment = segments[idx]
        applySegmentAudioAndSpeed(segment)
        val scaledOffsetMs = (player.currentPosition / segment.speed).toLong()
        return (segment.timelineStartMs + scaledOffsetMs)
            .coerceIn(segment.timelineStartMs, segment.timelineEndMs)
    }

    fun play(atTimelineMs: Long) {
        if (currentMapping.mediaItems.isEmpty()) return
        seekToTimelineMs(atTimelineMs)
        if (isPlayheadInsideVideoSegment(atTimelineMs)) {
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    private fun applySegmentAudioAndSpeed(segment: SegmentPlaybackMapping) {
        player.volume = if (segment.isMuted) 0f else segment.volume.coerceIn(0f, 1f)
        if (player.playbackParameters.speed != segment.speed) {
            player.setPlaybackSpeed(segment.speed)
        }
    }
}
