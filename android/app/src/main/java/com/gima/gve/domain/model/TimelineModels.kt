package com.gima.gve.domain.model

import kotlinx.serialization.Serializable

/**
 * Core non-destructive timeline domain models for GVE Editor.
 *
 * All time values are expressed in milliseconds ([Long]) for exact, deterministic
 * arithmetic without floating-point drift and 1:1 compatibility with AndroidX Media3
 * [androidx.media3.common.MediaItem.ClippingConfiguration].
 */
object TimelineConstants {
    const val SCHEMA_VERSION: Int = 1
    const val MIN_CLIP_DURATION_MS: Long = 100L
    const val DEFAULT_STILL_DURATION_MS: Long = 4_000L
    const val MAX_STILL_DURATION_MS: Long = 60_000L
    const val DEFAULT_FPS: Int = 30
    const val DEFAULT_WIDTH: Int = 1920
    const val DEFAULT_HEIGHT: Int = 1080
    const val MIN_SPEED: Float = 0.25f
    const val MAX_SPEED: Float = 4.0f

    private val ALLOWED_URI_SCHEMES = setOf("content", "file", "android.resource", "gve")

    fun isValidSourceUri(uri: String): Boolean {
        val trimmed = uri.trim()
        if (trimmed.isBlank()) return false
        val schemeIndex = trimmed.indexOf("://")
        if (schemeIndex <= 0) return false
        val scheme = trimmed.substring(0, schemeIndex).lowercase()
        val remainder = trimmed.substring(schemeIndex + 3)
        return scheme in ALLOWED_URI_SCHEMES && remainder.isNotBlank()
    }

    fun isTrackCompatible(mediaType: MediaType, trackType: TrackType): Boolean = when (trackType) {
        TrackType.VIDEO -> mediaType == MediaType.VIDEO || mediaType == MediaType.IMAGE
        TrackType.OVERLAY -> mediaType == MediaType.VIDEO || mediaType == MediaType.IMAGE
        TrackType.AUDIO -> mediaType == MediaType.AUDIO
        TrackType.TEXT -> mediaType == MediaType.TEXT
    }
}

@Serializable
enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT,
    OVERLAY
}

@Serializable
enum class MediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    TEXT
}

@Serializable
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    init {
        require(left in 0f..1f && right in 0f..1f && left < right) {
            "Crop horizontal bounds invalid: [$left, $right]"
        }
        require(top in 0f..1f && bottom in 0f..1f && top < bottom) {
            "Crop vertical bounds invalid: [$top, $bottom]"
        }
    }
}

@Serializable
data class ProjectSettings(
    val width: Int = TimelineConstants.DEFAULT_WIDTH,
    val height: Int = TimelineConstants.DEFAULT_HEIGHT,
    val fps: Int = TimelineConstants.DEFAULT_FPS
) {
    init {
        require(width > 0) { "Project width must be positive, got $width" }
        require(height > 0) { "Project height must be positive, got $height" }
        require(fps in 1..240) { "Project fps must be in 1..240, got $fps" }
    }
}

/**
 * Immutable descriptor of a source media file imported into the project.
 * Original media is never modified on disk; clips reference [uri] non-destructively.
 */
@Serializable
data class MediaAsset(
    val id: String,
    val uri: String,
    val displayName: String,
    val mediaType: MediaType,
    val totalDurationMs: Long,
    val width: Int? = null,
    val height: Int? = null,
    val hasAudioStream: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "MediaAsset id must not be blank" }
        require(TimelineConstants.isValidSourceUri(uri)) {
            "MediaAsset uri '$uri' must use a valid content://, file://, android.resource://, or gve:// URI"
        }
        require(totalDurationMs >= TimelineConstants.MIN_CLIP_DURATION_MS) {
            "MediaAsset totalDurationMs ($totalDurationMs) must be >= ${TimelineConstants.MIN_CLIP_DURATION_MS}"
        }
    }
}

/**
 * Core segment representing a non-destructive slice of a source media asset placed on a track.
 *
 * Invariants:
 * - [timelineStartMs] >= 0
 * - [sourceInMs] >= 0
 * - [sourceOutMs] <= [sourceDurationMs]
 * - [sourceOutMs] > [sourceInMs]
 * - [durationMs] >= [TimelineConstants.MIN_CLIP_DURATION_MS]
 * - When [speed] == 1.0f, [durationMs] == ([sourceOutMs] - [sourceInMs])
 */
@Serializable
data class MediaClip(
    val id: String,
    val sourceUri: String,
    val trackId: String,
    val timelineStartMs: Long,
    val durationMs: Long,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val sourceDurationMs: Long,
    val mediaType: MediaType,
    val label: String = "",
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val speed: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val cropRect: CropRect = CropRect(),
    val textContent: String? = null
) {
    val timelineEndMs: Long
        get() = timelineStartMs + durationMs

    val sourceSpanMs: Long
        get() = sourceOutMs - sourceInMs

    init {
        require(id.isNotBlank()) { "MediaClip id must not be blank" }
        require(TimelineConstants.isValidSourceUri(sourceUri)) {
            "MediaClip sourceUri '$sourceUri' must use a valid content://, file://, android.resource://, or gve:// URI"
        }
        require(trackId.isNotBlank()) { "MediaClip trackId must not be blank" }
        require(timelineStartMs >= 0L) {
            "timelineStartMs ($timelineStartMs) must be >= 0"
        }
        require(sourceInMs >= 0L) {
            "sourceInMs ($sourceInMs) must be >= 0"
        }
        require(sourceDurationMs >= TimelineConstants.MIN_CLIP_DURATION_MS) {
            "sourceDurationMs ($sourceDurationMs) must be >= ${TimelineConstants.MIN_CLIP_DURATION_MS}"
        }
        require(sourceOutMs <= sourceDurationMs) {
            "sourceOutMs ($sourceOutMs) must be <= sourceDurationMs ($sourceDurationMs)"
        }
        require(sourceOutMs > sourceInMs) {
            "sourceOutMs ($sourceOutMs) must be > sourceInMs ($sourceInMs)"
        }
        require(speed in TimelineConstants.MIN_SPEED..TimelineConstants.MAX_SPEED) {
            "speed ($speed) must be within ${TimelineConstants.MIN_SPEED}..${TimelineConstants.MAX_SPEED}"
        }
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees ($rotationDegrees) must be 0, 90, 180, or 270"
        }
        require(durationMs >= TimelineConstants.MIN_CLIP_DURATION_MS) {
            "durationMs ($durationMs) must be >= ${TimelineConstants.MIN_CLIP_DURATION_MS}"
        }
        if (speed == 1.0f) {
            require(durationMs == sourceOutMs - sourceInMs) {
                "durationMs ($durationMs) must equal sourceOutMs - sourceInMs (${sourceOutMs - sourceInMs}) at 1.0x speed"
            }
        }
        require(volume in 0.0f..2.0f) {
            "volume ($volume) must be within 0.0..2.0"
        }
    }
}

@Serializable
data class TimelineTrack(
    val id: String,
    val name: String,
    val type: TrackType,
    val orderIndex: Int,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "TimelineTrack id must not be blank" }
        require(orderIndex >= 0) { "TimelineTrack orderIndex ($orderIndex) must be >= 0" }
    }
}

@Serializable
data class TimelineProject(
    val schemaVersion: Int = TimelineConstants.SCHEMA_VERSION,
    val projectId: String,
    val name: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val settings: ProjectSettings = ProjectSettings(),
    val tracks: List<TimelineTrack> = emptyList(),
    val clips: List<MediaClip> = emptyList(),
    val mediaAssets: List<MediaAsset> = emptyList()
) {
    init {
        require(projectId.isNotBlank()) { "TimelineProject projectId must not be blank" }
        require(schemaVersion >= 1) { "schemaVersion must be >= 1" }
    }

    val totalDurationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L

    fun clipsForTrack(trackId: String): List<MediaClip> =
        clips.filter { it.trackId == trackId }.sortedBy { it.timelineStartMs }

    fun findClip(clipId: String): MediaClip? =
        clips.firstOrNull { it.id == clipId }

    fun findTrack(trackId: String): TimelineTrack? =
        tracks.firstOrNull { it.id == trackId }

    /**
     * Validates structural integrity of the project:
     * - Unique track, clip, and asset IDs
     * - Every clip references an existing compatible track
     * - Clips on the SAME track do not overlap (cross-track overlaps between
     *   separate video, overlay, text, and audio tracks are explicitly permitted)
     */
    fun validateIntegrity() {
        val seenTracks = HashSet<String>(tracks.size)
        tracks.forEach { track ->
            require(seenTracks.add(track.id)) {
                "Duplicate track ID '${track.id}' found in project."
            }
        }

        val seenAssets = HashSet<String>(mediaAssets.size)
        mediaAssets.forEach { asset ->
            require(seenAssets.add(asset.id)) {
                "Duplicate media asset ID '${asset.id}' found in project."
            }
        }

        val seenClips = HashSet<String>(clips.size)
        val tracksById = tracks.associateBy { it.id }
        clips.forEach { clip ->
            require(seenClips.add(clip.id)) {
                "Duplicate clip ID '${clip.id}' found in project."
            }
            val track = tracksById[clip.trackId]
                ?: throw IllegalArgumentException("Clip '${clip.id}' references missing track '${clip.trackId}'.")
            require(TimelineConstants.isTrackCompatible(clip.mediaType, track.type)) {
                "Clip '${clip.id}' (${clip.mediaType}) is incompatible with track '${track.id}' (${track.type})."
            }
        }

        tracks.forEach { track ->
            val sorted = clipsForTrack(track.id)
            for (i in 0 until sorted.lastIndex) {
                val current = sorted[i]
                val next = sorted[i + 1]
                require(current.timelineEndMs <= next.timelineStartMs) {
                    "Overlapping clips '${current.id}' [${current.timelineStartMs}, ${current.timelineEndMs}) and '${next.id}' [${next.timelineStartMs}, ${next.timelineEndMs}) on track '${track.id}'."
                }
            }
        }
    }
}
