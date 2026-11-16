package com.gima.gve.domain.engine

import com.gima.gve.domain.model.CropRect
import com.gima.gve.domain.model.MediaAsset
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.TimelineConstants
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.model.TimelineTrack
import com.gima.gve.domain.model.TrackType
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Pure, deterministic editing engine operating on immutable [TimelineProject] snapshots.
 *
 * Every mutation returns a new [TimelineProject] (or a typed [TimelineError]), never
 * mutating input state or underlying source media files.
 *
 * Overlap validation is strictly scoped per-track: clips on the same track cannot overlap,
 * whereas clips across separate video, overlay, text, and audio tracks are free to overlap.
 */
class TimelineEngine(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    fun createEmptyProject(
        name: String,
        projectId: String = idGenerator()
    ): TimelineProject {
        val now = clockMs()
        val mainVideoTrack = TimelineTrack(
            id = "track-v1",
            name = "V1 Main",
            type = TrackType.VIDEO,
            orderIndex = 0
        )
        val mainAudioTrack = TimelineTrack(
            id = "track-a1",
            name = "A1 Audio",
            type = TrackType.AUDIO,
            orderIndex = 1
        )
        return TimelineProject(
            projectId = projectId,
            name = name.ifBlank { "Untitled Project" },
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            tracks = listOf(mainVideoTrack, mainAudioTrack),
            clips = emptyList(),
            mediaAssets = emptyList()
        )
    }

    fun renameProject(
        project: TimelineProject,
        newName: String
    ): TimelineOperationResult<TimelineProject> {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return TimelineOperationResult.Failure(
                TimelineError.InvalidOperation("Project name cannot be empty.")
            )
        }
        return TimelineOperationResult.Success(
            project.copy(
                name = trimmed,
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun addTrack(
        project: TimelineProject,
        type: TrackType,
        name: String? = null,
        trackId: String = idGenerator()
    ): TimelineOperationResult<TimelineProject> {
        if (trackId.isBlank() || project.findTrack(trackId) != null) {
            return TimelineOperationResult.Failure(TimelineError.DuplicateTrackId(trackId))
        }

        val sameTypeCount = project.tracks.count { it.type == type }
        val defaultName = when (type) {
            TrackType.VIDEO -> "V${sameTypeCount + 1}"
            TrackType.AUDIO -> "A${sameTypeCount + 1}"
            TrackType.TEXT -> "T${sameTypeCount + 1}"
            TrackType.OVERLAY -> "OV${sameTypeCount + 1}"
        }
        val nextOrder = (project.tracks.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val newTrack = TimelineTrack(
            id = trackId,
            name = name?.takeIf { it.isNotBlank() } ?: defaultName,
            type = type,
            orderIndex = nextOrder
        )
        return TimelineOperationResult.Success(
            project.copy(
                tracks = project.tracks + newTrack,
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun removeTrack(
        project: TimelineProject,
        trackId: String
    ): TimelineOperationResult<TimelineProject> {
        val track = project.findTrack(trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(trackId))

        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(trackId))
        }

        if (track.type == TrackType.VIDEO || track.type == TrackType.AUDIO) {
            val countOfType = project.tracks.count { it.type == track.type }
            if (countOfType <= 1) {
                return TimelineOperationResult.Failure(TimelineError.CannotRemoveLastTrack(track.type))
            }
        }

        val remainingTracks = project.tracks
            .filterNot { it.id == trackId }
            .sortedBy { it.orderIndex }
            .mapIndexed { index, item -> item.copy(orderIndex = index) }

        val remainingClips = project.clips.filterNot { it.trackId == trackId }

        return TimelineOperationResult.Success(
            project.copy(
                tracks = remainingTracks,
                clips = remainingClips,
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun setTrackMuted(
        project: TimelineProject,
        trackId: String,
        muted: Boolean
    ): TimelineOperationResult<TimelineProject> {
        project.findTrack(trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(trackId))

        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) track.copy(isMuted = muted) else track
        }
        return TimelineOperationResult.Success(
            project.copy(tracks = updatedTracks, updatedAtEpochMs = clockMs())
        )
    }

    fun setTrackLocked(
        project: TimelineProject,
        trackId: String,
        locked: Boolean
    ): TimelineOperationResult<TimelineProject> {
        project.findTrack(trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(trackId))

        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) track.copy(isLocked = locked) else track
        }
        return TimelineOperationResult.Success(
            project.copy(tracks = updatedTracks, updatedAtEpochMs = clockMs())
        )
    }

    fun setTrackVisible(
        project: TimelineProject,
        trackId: String,
        visible: Boolean
    ): TimelineOperationResult<TimelineProject> {
        project.findTrack(trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(trackId))

        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) track.copy(isVisible = visible) else track
        }
        return TimelineOperationResult.Success(
            project.copy(tracks = updatedTracks, updatedAtEpochMs = clockMs())
        )
    }

    fun addClip(
        project: TimelineProject,
        clip: MediaClip,
        asset: MediaAsset? = null
    ): TimelineOperationResult<TimelineProject> {
        if (project.findClip(clip.id) != null) {
            return TimelineOperationResult.Failure(TimelineError.DuplicateClipId(clip.id))
        }

        if (!TimelineConstants.isValidSourceUri(clip.sourceUri)) {
            return TimelineOperationResult.Failure(TimelineError.InvalidSourceUri(clip.sourceUri))
        }

        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))

        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        if (!TimelineConstants.isTrackCompatible(clip.mediaType, track.type)) {
            return TimelineOperationResult.Failure(
                TimelineError.IncompatibleTrackType(clip.mediaType, track.type)
            )
        }

        val conflict = findOverlappingClip(
            clips = project.clipsForTrack(track.id),
            startMs = clip.timelineStartMs,
            endMs = clip.timelineEndMs,
            excludeClipId = null
        )
        if (conflict != null) {
            return TimelineOperationResult.Failure(
                TimelineError.TrackOverlap(
                    trackId = track.id,
                    requestedStartMs = clip.timelineStartMs,
                    requestedEndMs = clip.timelineEndMs,
                    conflictingClipId = conflict.id
                )
            )
        }

        val nextAssets = if (asset != null && project.mediaAssets.none { it.id == asset.id }) {
            project.mediaAssets + asset
        } else {
            project.mediaAssets
        }

        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips + clip,
                mediaAssets = nextAssets,
                updatedAtEpochMs = clockMs()
            )
        )
    }

    /**
     * Duplicates [clipId] on the same track, placing the copy immediately after the original
     * if space is available, or at the end of the track to avoid overlap.
     */
    fun duplicateClip(
        project: TimelineProject,
        clipId: String,
        newClipId: String = idGenerator()
    ): TimelineOperationResult<TimelineProject> {
        if (newClipId.isBlank() || project.findClip(newClipId) != null) {
            return TimelineOperationResult.Failure(TimelineError.DuplicateClipId(newClipId))
        }
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))
        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val trackClips = project.clipsForTrack(track.id)
        val candidateStart = clip.timelineEndMs
        val conflict = findOverlappingClip(
            clips = trackClips,
            startMs = candidateStart,
            endMs = candidateStart + clip.durationMs,
            excludeClipId = null
        )
        val targetStart = if (conflict == null) {
            candidateStart
        } else {
            trackClips.maxOfOrNull { it.timelineEndMs } ?: 0L
        }

        val duplicated = clip.copy(
            id = newClipId,
            timelineStartMs = targetStart,
            label = if (clip.label.endsWith(" (Copy)")) clip.label else "${clip.label} (Copy)"
        )

        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips + duplicated,
                updatedAtEpochMs = clockMs()
            )
        )
    }

    /**
     * Trims the left (head) edge of a clip to [newTimelineStartMs].
     */
    fun trimClipHead(
        project: TimelineProject,
        clipId: String,
        newTimelineStartMs: Long,
        clampToNeighbors: Boolean = true
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))

        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val trackClips = project.clipsForTrack(track.id)
        val previousClip = trackClips.lastOrNull { it.id != clip.id && it.timelineEndMs <= clip.timelineStartMs }

        val availableEarlierTimelineMs = (clip.sourceInMs / clip.speed).roundToLong()
        val minBySource = clip.timelineStartMs - availableEarlierTimelineMs
        val minByTimeline = 0L
        val minByNeighbor = previousClip?.timelineEndMs ?: 0L
        val minAllowedStart = if (clampToNeighbors) {
            maxOf(minBySource, minByTimeline, minByNeighbor)
        } else {
            maxOf(minBySource, minByTimeline)
        }

        val maxAllowedStart = clip.timelineEndMs - TimelineConstants.MIN_CLIP_DURATION_MS

        if (minAllowedStart > maxAllowedStart) {
            return TimelineOperationResult.Failure(
                TimelineError.InvalidTrimBounds("Clip '$clipId' cannot be trimmed further from the head.")
            )
        }

        val effectiveStartMs = if (clampToNeighbors) {
            newTimelineStartMs.coerceIn(minAllowedStart, maxAllowedStart)
        } else {
            if (newTimelineStartMs < minByTimeline || newTimelineStartMs < minBySource || newTimelineStartMs > maxAllowedStart) {
                return TimelineOperationResult.Failure(
                    TimelineError.InvalidTrimBounds(
                        "Requested head start ${newTimelineStartMs}ms violates bounds [$minAllowedStart, $maxAllowedStart]."
                    )
                )
            }
            if (previousClip != null && newTimelineStartMs < previousClip.timelineEndMs) {
                return TimelineOperationResult.Failure(
                    TimelineError.TrackOverlap(
                        trackId = track.id,
                        requestedStartMs = newTimelineStartMs,
                        requestedEndMs = clip.timelineEndMs,
                        conflictingClipId = previousClip.id
                    )
                )
            }
            newTimelineStartMs
        }

        val timelineDeltaMs = effectiveStartMs - clip.timelineStartMs
        val sourceDeltaMs = (timelineDeltaMs * clip.speed).roundToLong()
        val newSourceInMs = (clip.sourceInMs + sourceDeltaMs).coerceIn(0L, clip.sourceOutMs - 1L)
        val newDurationMs = clip.timelineEndMs - effectiveStartMs

        val updatedClip = clip.copy(
            timelineStartMs = effectiveStartMs,
            sourceInMs = newSourceInMs,
            durationMs = newDurationMs
        )

        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) updatedClip else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    /**
     * Trims the right (tail) edge of a clip to [newTimelineEndMs].
     */
    fun trimClipTail(
        project: TimelineProject,
        clipId: String,
        newTimelineEndMs: Long,
        clampToNeighbors: Boolean = true
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))

        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val trackClips = project.clipsForTrack(track.id)
        val nextClip = trackClips.firstOrNull { it.id != clip.id && it.timelineStartMs >= clip.timelineEndMs }

        val minAllowedEnd = clip.timelineStartMs + TimelineConstants.MIN_CLIP_DURATION_MS
        val maxDurationFromSourceMs = ((clip.sourceDurationMs - clip.sourceInMs) / clip.speed).roundToLong()
        val maxBySource = clip.timelineStartMs + maxDurationFromSourceMs
        val maxAllowedEnd = if (clampToNeighbors && nextClip != null) {
            minOf(maxBySource, nextClip.timelineStartMs)
        } else {
            maxBySource
        }

        if (minAllowedEnd > maxAllowedEnd) {
            return TimelineOperationResult.Failure(
                TimelineError.InvalidTrimBounds("Clip '$clipId' cannot be trimmed further from the tail.")
            )
        }

        val effectiveEndMs = if (clampToNeighbors) {
            newTimelineEndMs.coerceIn(minAllowedEnd, maxAllowedEnd)
        } else {
            if (newTimelineEndMs < minAllowedEnd || newTimelineEndMs > maxBySource) {
                return TimelineOperationResult.Failure(
                    TimelineError.InvalidTrimBounds(
                        "Requested tail end ${newTimelineEndMs}ms violates bounds [$minAllowedEnd, $maxBySource]."
                    )
                )
            }
            if (nextClip != null && newTimelineEndMs > nextClip.timelineStartMs) {
                return TimelineOperationResult.Failure(
                    TimelineError.TrackOverlap(
                        trackId = track.id,
                        requestedStartMs = clip.timelineStartMs,
                        requestedEndMs = newTimelineEndMs,
                        conflictingClipId = nextClip.id
                    )
                )
            }
            newTimelineEndMs
        }

        val newDurationMs = effectiveEndMs - clip.timelineStartMs
        val newSourceOutMs = (clip.sourceInMs + (newDurationMs * clip.speed).roundToLong())
            .coerceIn(clip.sourceInMs + 1L, clip.sourceDurationMs)

        val updatedClip = clip.copy(
            sourceOutMs = newSourceOutMs,
            durationMs = newDurationMs
        )

        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) updatedClip else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    /**
     * Splits [clipId] cleanly into two non-destructive clips at [splitPositionMs].
     */
    fun splitClipAtPlayhead(
        project: TimelineProject,
        clipId: String,
        splitPositionMs: Long,
        rightClipId: String = idGenerator()
    ): TimelineOperationResult<SplitOutcome> {
        if (rightClipId.isBlank() || project.findClip(rightClipId) != null) {
            return TimelineOperationResult.Failure(TimelineError.DuplicateClipId(rightClipId))
        }

        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))

        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val minValid = clip.timelineStartMs + TimelineConstants.MIN_CLIP_DURATION_MS
        val maxValid = clip.timelineEndMs - TimelineConstants.MIN_CLIP_DURATION_MS

        if (splitPositionMs < minValid || splitPositionMs > maxValid) {
            return TimelineOperationResult.Failure(
                TimelineError.InvalidSplitPosition(
                    clipId = clipId,
                    splitPositionMs = splitPositionMs,
                    minValidMs = minValid,
                    maxValidMs = maxValid
                )
            )
        }

        val leftDurationMs = splitPositionMs - clip.timelineStartMs
        val rightDurationMs = clip.durationMs - leftDurationMs
        val sourceOffsetMs = if (clip.speed == 1.0f) {
            leftDurationMs
        } else {
            (leftDurationMs * clip.speed).roundToLong().coerceIn(1L, clip.sourceSpanMs - 1L)
        }
        val sourceSplitPointMs = clip.sourceInMs + sourceOffsetMs

        val leftClip = clip.copy(
            durationMs = leftDurationMs,
            sourceOutMs = sourceSplitPointMs
        )

        val rightClip = clip.copy(
            id = rightClipId,
            timelineStartMs = splitPositionMs,
            durationMs = rightDurationMs,
            sourceInMs = sourceSplitPointMs,
            sourceOutMs = clip.sourceOutMs
        )

        val updatedClips = project.clips.flatMap { current ->
            if (current.id == clipId) listOf(leftClip, rightClip) else listOf(current)
        }

        return TimelineOperationResult.Success(
            SplitOutcome(
                project = project.copy(
                    clips = updatedClips,
                    updatedAtEpochMs = clockMs()
                ),
                leftClip = leftClip,
                rightClip = rightClip
            )
        )
    }

    fun moveClip(
        project: TimelineProject,
        clipId: String,
        targetTrackId: String,
        newTimelineStartMs: Long
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val sourceTrack = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))
        val targetTrack = project.findTrack(targetTrackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(targetTrackId))

        if (sourceTrack.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(sourceTrack.id))
        }
        if (targetTrack.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(targetTrack.id))
        }
        if (!TimelineConstants.isTrackCompatible(clip.mediaType, targetTrack.type)) {
            return TimelineOperationResult.Failure(
                TimelineError.IncompatibleTrackType(clip.mediaType, targetTrack.type)
            )
        }

        val clampedStartMs = newTimelineStartMs.coerceAtLeast(0L)
        val requestedEndMs = clampedStartMs + clip.durationMs

        val conflict = findOverlappingClip(
            clips = project.clipsForTrack(targetTrackId),
            startMs = clampedStartMs,
            endMs = requestedEndMs,
            excludeClipId = clip.id
        )
        if (conflict != null) {
            return TimelineOperationResult.Failure(
                TimelineError.TrackOverlap(
                    trackId = targetTrackId,
                    requestedStartMs = clampedStartMs,
                    requestedEndMs = requestedEndMs,
                    conflictingClipId = conflict.id
                )
            )
        }

        val movedClip = clip.copy(
            trackId = targetTrackId,
            timelineStartMs = clampedStartMs
        )

        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) movedClip else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun deleteClip(
        project: TimelineProject,
        clipId: String,
        ripple: Boolean = false
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))

        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val updatedClips = project.clips
            .filterNot { it.id == clipId }
            .map { other ->
                if (ripple && other.trackId == clip.trackId && other.timelineStartMs >= clip.timelineEndMs) {
                    other.copy(timelineStartMs = (other.timelineStartMs - clip.durationMs).coerceAtLeast(0L))
                } else {
                    other
                }
            }

        return TimelineOperationResult.Success(
            project.copy(
                clips = updatedClips,
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun updateClipVolume(
        project: TimelineProject,
        clipId: String,
        volume: Float,
        isMuted: Boolean
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))
        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val clampedVolume = volume.coerceIn(0f, 2f)
        val updated = clip.copy(volume = clampedVolume, isMuted = isMuted)
        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) updated else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun updateClipSpeed(
        project: TimelineProject,
        clipId: String,
        newSpeed: Float
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))
        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val clampedSpeed = newSpeed.coerceIn(TimelineConstants.MIN_SPEED, TimelineConstants.MAX_SPEED)
        val newDurationMs = (clip.sourceSpanMs / clampedSpeed).roundToLong()
            .coerceAtLeast(TimelineConstants.MIN_CLIP_DURATION_MS)
        val newEndMs = clip.timelineStartMs + newDurationMs

        val conflict = findOverlappingClip(
            clips = project.clipsForTrack(track.id),
            startMs = clip.timelineStartMs,
            endMs = newEndMs,
            excludeClipId = clip.id
        )
        if (conflict != null) {
            return TimelineOperationResult.Failure(
                TimelineError.TrackOverlap(
                    trackId = track.id,
                    requestedStartMs = clip.timelineStartMs,
                    requestedEndMs = newEndMs,
                    conflictingClipId = conflict.id
                )
            )
        }

        val updated = clip.copy(
            speed = clampedSpeed,
            durationMs = newDurationMs
        )
        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) updated else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun updateClipTransform(
        project: TimelineProject,
        clipId: String,
        rotationDegrees: Int,
        cropRect: CropRect
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))
        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }

        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val snappedRotation = when (normalizedRotation) {
            90, 180, 270 -> normalizedRotation
            else -> 0
        }

        val updated = clip.copy(
            rotationDegrees = snappedRotation,
            cropRect = cropRect
        )
        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) updated else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    fun updateTextClip(
        project: TimelineProject,
        clipId: String,
        textContent: String
    ): TimelineOperationResult<TimelineProject> {
        val clip = project.findClip(clipId)
            ?: return TimelineOperationResult.Failure(TimelineError.ClipNotFound(clipId))
        val track = project.findTrack(clip.trackId)
            ?: return TimelineOperationResult.Failure(TimelineError.TrackNotFound(clip.trackId))
        if (track.isLocked) {
            return TimelineOperationResult.Failure(TimelineError.TrackLocked(track.id))
        }
        val cleaned = textContent.trim().ifBlank { "Title" }
        val updated = clip.copy(
            textContent = cleaned,
            label = cleaned.take(28)
        )
        return TimelineOperationResult.Success(
            project.copy(
                clips = project.clips.map { if (it.id == clipId) updated else it },
                updatedAtEpochMs = clockMs()
            )
        )
    }

    private fun findOverlappingClip(
        clips: List<MediaClip>,
        startMs: Long,
        endMs: Long,
        excludeClipId: String?
    ): MediaClip? = clips.firstOrNull { candidate ->
        candidate.id != excludeClipId &&
            startMs < candidate.timelineEndMs &&
            endMs > candidate.timelineStartMs
    }
}

data class SplitOutcome(
    val project: TimelineProject,
    val leftClip: MediaClip,
    val rightClip: MediaClip
)
