package com.gima.gve.domain.history

import com.gima.gve.domain.engine.TimelineEngine
import com.gima.gve.domain.engine.TimelineError
import com.gima.gve.domain.engine.TimelineOperationResult
import com.gima.gve.domain.model.CropRect
import com.gima.gve.domain.model.MediaAsset
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.model.TrackType

/**
 * Reversible command contract for timeline mutations.
 * Each command records its exact pre/post project snapshots inside [HistoryEntry]
 * so undo/redo is O(1) and strictly deterministic.
 */
sealed interface TimelineCommand {
    val label: String
    val coalesceKey: String? get() = null

    fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject>
}

data class RenameProjectCommand(
    val newName: String
) : TimelineCommand {
    override val label: String = "Rename project"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.renameProject(current, newName = newName)
}

data class AddTrackCommand(
    val type: TrackType,
    val name: String? = null,
    val trackId: String
) : TimelineCommand {
    override val label: String = "Add ${type.name.lowercase()} track"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.addTrack(current, type = type, name = name, trackId = trackId)
}

data class RemoveTrackCommand(
    val trackId: String
) : TimelineCommand {
    override val label: String = "Remove track"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.removeTrack(current, trackId = trackId)
}

data class SetTrackMutedCommand(
    val trackId: String,
    val muted: Boolean
) : TimelineCommand {
    override val label: String = if (muted) "Mute track" else "Unmute track"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.setTrackMuted(current, trackId = trackId, muted = muted)
}

data class SetTrackLockedCommand(
    val trackId: String,
    val locked: Boolean
) : TimelineCommand {
    override val label: String = if (locked) "Lock track" else "Unlock track"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.setTrackLocked(current, trackId = trackId, locked = locked)
}

data class SetTrackVisibleCommand(
    val trackId: String,
    val visible: Boolean
) : TimelineCommand {
    override val label: String = if (visible) "Show track" else "Hide track"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.setTrackVisible(current, trackId = trackId, visible = visible)
}

data class AddClipCommand(
    val clip: MediaClip,
    val asset: MediaAsset? = null
) : TimelineCommand {
    override val label: String = "Add clip ${clip.label.ifBlank { clip.id }}"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.addClip(current, clip = clip, asset = asset)
}

data class DuplicateClipCommand(
    val clipId: String,
    val newClipId: String
) : TimelineCommand {
    override val label: String = "Duplicate clip"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.duplicateClip(current, clipId = clipId, newClipId = newClipId)
}

data class TrimClipHeadCommand(
    val clipId: String,
    val newTimelineStartMs: Long,
    override val coalesceKey: String? = "trim-head-$clipId"
) : TimelineCommand {
    override val label: String = "Trim clip head"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.trimClipHead(current, clipId = clipId, newTimelineStartMs = newTimelineStartMs)
}

data class TrimClipTailCommand(
    val clipId: String,
    val newTimelineEndMs: Long,
    override val coalesceKey: String? = "trim-tail-$clipId"
) : TimelineCommand {
    override val label: String = "Trim clip tail"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.trimClipTail(current, clipId = clipId, newTimelineEndMs = newTimelineEndMs)
}

data class SplitClipCommand(
    val clipId: String,
    val splitPositionMs: Long,
    val rightClipId: String
) : TimelineCommand {
    override val label: String = "Split clip"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        when (val result = engine.splitClipAtPlayhead(current, clipId, splitPositionMs, rightClipId)) {
            is TimelineOperationResult.Success -> TimelineOperationResult.Success(result.value.project)
            is TimelineOperationResult.Failure -> result
        }
}

data class MoveClipCommand(
    val clipId: String,
    val targetTrackId: String,
    val newTimelineStartMs: Long,
    override val coalesceKey: String? = "move-$clipId"
) : TimelineCommand {
    override val label: String = "Move clip"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.moveClip(current, clipId = clipId, targetTrackId = targetTrackId, newTimelineStartMs = newTimelineStartMs)
}

data class DeleteClipCommand(
    val clipId: String,
    val ripple: Boolean = false
) : TimelineCommand {
    override val label: String = if (ripple) "Ripple delete clip" else "Delete clip"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.deleteClip(current, clipId = clipId, ripple = ripple)
}

data class UpdateClipVolumeCommand(
    val clipId: String,
    val volume: Float,
    val isMuted: Boolean,
    override val coalesceKey: String? = "volume-$clipId"
) : TimelineCommand {
    override val label: String = "Adjust volume"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.updateClipVolume(current, clipId = clipId, volume = volume, isMuted = isMuted)
}

data class UpdateClipSpeedCommand(
    val clipId: String,
    val speed: Float
) : TimelineCommand {
    override val label: String = "Adjust speed"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.updateClipSpeed(current, clipId = clipId, newSpeed = speed)
}

data class UpdateClipTransformCommand(
    val clipId: String,
    val rotationDegrees: Int,
    val cropRect: CropRect
) : TimelineCommand {
    override val label: String = "Crop / rotate clip"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.updateClipTransform(current, clipId = clipId, rotationDegrees = rotationDegrees, cropRect = cropRect)
}

data class UpdateTextClipCommand(
    val clipId: String,
    val textContent: String
) : TimelineCommand {
    override val label: String = "Edit text"

    override fun execute(engine: TimelineEngine, current: TimelineProject): TimelineOperationResult<TimelineProject> =
        engine.updateTextClip(current, clipId = clipId, textContent = textContent)
}

data class HistoryEntry(
    val label: String,
    val coalesceKey: String?,
    val before: TimelineProject,
    val after: TimelineProject,
    val recordedAtEpochMs: Long
)

data class HistorySnapshot(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val undoLabel: String?,
    val redoLabel: String?,
    val undoCount: Int,
    val redoCount: Int
)

/**
 * Bounded Undo/Redo history manager for [TimelineProject] edits.
 * Supports coalescing continuous drag interactions (trim/move/volume) into a single undo entry
 * when [allowCoalesce] is true.
 */
class EditHistoryManager(
    private val engine: TimelineEngine = TimelineEngine(),
    private val maxHistorySize: Int = 100,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    init {
        require(maxHistorySize > 0) { "maxHistorySize must be > 0, got $maxHistorySize" }
    }

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    fun applyCommand(
        currentProject: TimelineProject,
        command: TimelineCommand,
        allowCoalesce: Boolean = false
    ): TimelineOperationResult<TimelineProject> {
        val result = command.execute(engine, currentProject)
        if (result is TimelineOperationResult.Failure) {
            return result
        }

        val nextProject = (result as TimelineOperationResult.Success).value
        if (nextProject == currentProject) {
            return TimelineOperationResult.Success(currentProject)
        }

        redoStack.clear()

        val lastEntry = undoStack.lastOrNull()
        val canCoalesce = allowCoalesce &&
            command.coalesceKey != null &&
            lastEntry != null &&
            lastEntry.coalesceKey == command.coalesceKey

        if (canCoalesce && lastEntry != null) {
            undoStack.removeLast()
            undoStack.addLast(
                lastEntry.copy(
                    after = nextProject,
                    recordedAtEpochMs = clockMs()
                )
            )
        } else {
            if (undoStack.size >= maxHistorySize) {
                undoStack.removeFirst()
            }
            undoStack.addLast(
                HistoryEntry(
                    label = command.label,
                    coalesceKey = if (allowCoalesce) command.coalesceKey else null,
                    before = currentProject,
                    after = nextProject,
                    recordedAtEpochMs = clockMs()
                )
            )
        }

        return TimelineOperationResult.Success(nextProject)
    }

    fun undo(): TimelineProject? {
        val entry = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(entry)
        return entry.before
    }

    fun redo(): TimelineProject? {
        val entry = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(entry)
        return entry.after
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun snapshot(): HistorySnapshot = HistorySnapshot(
        canUndo = undoStack.isNotEmpty(),
        canRedo = redoStack.isNotEmpty(),
        undoLabel = undoStack.lastOrNull()?.label,
        redoLabel = redoStack.lastOrNull()?.label,
        undoCount = undoStack.size,
        redoCount = redoStack.size
    )

    fun historyEntries(): List<HistoryEntry> = undoStack.toList()
}

data class HistoryError(val error: TimelineError)
