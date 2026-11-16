package com.gima.gve.domain.engine

import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TrackType

sealed interface TimelineError {
    val message: String

    data class ClipNotFound(val clipId: String) : TimelineError {
        override val message: String = "Clip '$clipId' was not found on the timeline."
    }

    data class TrackNotFound(val trackId: String) : TimelineError {
        override val message: String = "Track '$trackId' was not found in the project."
    }

    data class DuplicateClipId(val clipId: String) : TimelineError {
        override val message: String = "A clip with ID '$clipId' already exists on the timeline."
    }

    data class DuplicateTrackId(val trackId: String) : TimelineError {
        override val message: String = "A track with ID '$trackId' already exists in the project."
    }

    data class InvalidSourceUri(val uri: String) : TimelineError {
        override val message: String = "Source URI '$uri' is missing or uses an unsupported scheme."
    }

    data class InvalidMediaDuration(val durationMs: Long) : TimelineError {
        override val message: String = "Media duration ${durationMs}ms is shorter than the minimum allowed clip duration."
    }

    data class TrackLocked(val trackId: String) : TimelineError {
        override val message: String = "Track '$trackId' is locked and cannot be edited."
    }

    data class IncompatibleTrackType(
        val mediaType: MediaType,
        val trackType: TrackType
    ) : TimelineError {
        override val message: String =
            "Cannot place $mediaType clip onto $trackType track."
    }

    data class TrackOverlap(
        val trackId: String,
        val requestedStartMs: Long,
        val requestedEndMs: Long,
        val conflictingClipId: String
    ) : TimelineError {
        override val message: String =
            "Range [$requestedStartMs, $requestedEndMs) overlaps clip '$conflictingClipId' on track '$trackId'."
    }

    data class InvalidTrimBounds(override val message: String) : TimelineError

    data class InvalidOperation(override val message: String) : TimelineError

    data class InvalidSplitPosition(
        val clipId: String,
        val splitPositionMs: Long,
        val minValidMs: Long,
        val maxValidMs: Long
    ) : TimelineError {
        override val message: String =
            "Split position ${splitPositionMs}ms is outside valid range [$minValidMs, $maxValidMs] for clip '$clipId'."
    }

    data class CannotRemoveLastTrack(val trackType: TrackType) : TimelineError {
        override val message: String =
            "Cannot remove the last remaining $trackType track."
    }
}

sealed interface TimelineOperationResult<out T> {
    data class Success<T>(val value: T) : TimelineOperationResult<T>
    data class Failure(val error: TimelineError) : TimelineOperationResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value
}
