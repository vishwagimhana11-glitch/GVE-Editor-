package com.gima.gve.ui.state

import android.net.Uri
import com.gima.gve.data.media.RecentMediaStore
import com.gima.gve.data.media.SafMediaInspector
import com.gima.gve.data.repository.AutosaveCoordinator
import com.gima.gve.data.repository.AutosaveStatus
import com.gima.gve.data.serialization.ProjectJsonSerializer
import com.gima.gve.domain.engine.TimelineEngine
import com.gima.gve.domain.engine.TimelineOperationResult
import com.gima.gve.domain.history.AddClipCommand
import com.gima.gve.domain.history.AddTrackCommand
import com.gima.gve.domain.history.DeleteClipCommand
import com.gima.gve.domain.history.DuplicateClipCommand
import com.gima.gve.domain.history.EditHistoryManager
import com.gima.gve.domain.history.HistorySnapshot
import com.gima.gve.domain.history.MoveClipCommand
import com.gima.gve.domain.history.RemoveTrackCommand
import com.gima.gve.domain.history.RenameProjectCommand
import com.gima.gve.domain.history.SetTrackLockedCommand
import com.gima.gve.domain.history.SetTrackMutedCommand
import com.gima.gve.domain.history.SetTrackVisibleCommand
import com.gima.gve.domain.history.SplitClipCommand
import com.gima.gve.domain.history.TimelineCommand
import com.gima.gve.domain.history.TrimClipHeadCommand
import com.gima.gve.domain.history.TrimClipTailCommand
import com.gima.gve.domain.history.UpdateClipSpeedCommand
import com.gima.gve.domain.history.UpdateClipTransformCommand
import com.gima.gve.domain.history.UpdateClipVolumeCommand
import com.gima.gve.domain.history.UpdateTextClipCommand
import com.gima.gve.domain.model.CropRect
import com.gima.gve.domain.model.MediaAsset
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TimelineConstants
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.model.TrackType
import com.gima.gve.domain.repository.ProjectRepository
import com.gima.gve.rendering.media3.PreviewQuality
import com.gima.gve.rendering.media3.PreviewScaleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class EditorToolTab(val label: String, val isSupported: Boolean) {
    EDIT("Edit", true),
    AUDIO("Audio", true),
    TEXT("Text", true),
    STICKERS("Stickers", false),
    EFFECTS("Effects", false),
    FILTERS("Filters", false),
    OVERLAY("Overlay", true),
    TRANSITIONS("Transitions", false),
    SPEED("Speed", true),
    CROP("Crop", true),
    VOLUME("Volume", true)
}

data class EditorUiState(
    val project: TimelineProject,
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L,
    val activeToolTab: EditorToolTab? = null,
    val timelineZoomPxPerSec: Float = 80f,
    val previewQuality: PreviewQuality = PreviewQuality.BALANCED_720P,
    val previewScaleMode: PreviewScaleMode = PreviewScaleMode.FIT,
    val isFullscreenPreview: Boolean = false,
    val isMediaImportOpen: Boolean = false,
    val isExportSheetOpen: Boolean = false,
    val recentMedia: List<MediaAsset> = emptyList(),
    val isImportingMedia: Boolean = false,
    val history: HistorySnapshot = HistorySnapshot(
        canUndo = false,
        canRedo = false,
        undoLabel = null,
        redoLabel = null,
        undoCount = 0,
        redoCount = 0
    ),
    val autosaveStatus: AutosaveStatus = AutosaveStatus.Idle,
    val lastErrorMessage: String? = null,
    val serializedJsonPreview: String = ""
) {
    val selectedClip: MediaClip?
        get() = selectedClipId?.let { project.findClip(it) }
}

/**
 * Unidirectional state holder connecting [TimelineEngine], [EditHistoryManager],
 * [AutosaveCoordinator], [SafMediaInspector], and [RecentMediaStore] to Jetpack Compose screens.
 */
class EditorStateHolder(
    initialProject: TimelineProject,
    private val scope: CoroutineScope,
    repository: ProjectRepository,
    private val mediaInspector: SafMediaInspector? = null,
    private val recentMediaStore: RecentMediaStore? = null,
    private val engine: TimelineEngine = TimelineEngine(),
    private val historyManager: EditHistoryManager = EditHistoryManager(engine),
    private val serializer: ProjectJsonSerializer = ProjectJsonSerializer(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    private val autosaveCoordinator = AutosaveCoordinator(repository, scope)

    private val _uiState = MutableStateFlow(
        EditorUiState(
            project = initialProject,
            selectedClipId = initialProject.clips.firstOrNull()?.id,
            history = historyManager.snapshot(),
            serializedJsonPreview = serializer.encode(initialProject)
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val autosaveObserverJob: Job

    init {
        autosaveCoordinator.observe(_uiState.map { it.project })
        autosaveObserverJob = scope.launch {
            autosaveCoordinator.status.collect { status ->
                _uiState.update { it.copy(autosaveStatus = status) }
            }
        }
        recentMediaStore?.let { store ->
            scope.launch {
                val recent = store.loadRecentMedia()
                _uiState.update { it.copy(recentMedia = recent) }
            }
        }
    }

    fun selectClip(clipId: String?) {
        _uiState.update { state ->
            val validId = clipId?.takeIf { state.project.findClip(it) != null }
            val nextTool = if (validId != null && state.activeToolTab == null) {
                EditorToolTab.EDIT
            } else {
                state.activeToolTab
            }
            state.copy(
                selectedClipId = validId,
                activeToolTab = nextTool,
                lastErrorMessage = null
            )
        }
    }

    fun seekTo(positionMs: Long) {
        val maxDuration = _uiState.value.project.totalDurationMs.coerceAtLeast(0L)
        _uiState.update {
            it.copy(playheadMs = positionMs.coerceIn(0L, maxDuration))
        }
    }

    fun selectToolTab(tab: EditorToolTab) {
        _uiState.update { state ->
            val toggled = if (state.activeToolTab == tab) null else tab
            state.copy(activeToolTab = toggled, lastErrorMessage = null)
        }
    }

    fun closeToolPanel() {
        _uiState.update { it.copy(activeToolTab = null) }
    }

    fun setTimelineZoom(pxPerSec: Float) {
        _uiState.update {
            it.copy(timelineZoomPxPerSec = pxPerSec.coerceIn(32f, 200f))
        }
    }

    fun zoomIn() {
        setTimelineZoom(_uiState.value.timelineZoomPxPerSec + 20f)
    }

    fun zoomOut() {
        setTimelineZoom(_uiState.value.timelineZoomPxPerSec - 20f)
    }

    fun setPreviewQuality(quality: PreviewQuality) {
        _uiState.update { it.copy(previewQuality = quality) }
    }

    fun togglePreviewScaleMode() {
        _uiState.update { state ->
            val next = if (state.previewScaleMode == PreviewScaleMode.FIT) {
                PreviewScaleMode.FILL
            } else {
                PreviewScaleMode.FIT
            }
            state.copy(previewScaleMode = next)
        }
    }

    fun setFullscreenPreview(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreenPreview = fullscreen) }
    }

    fun setMediaImportOpen(open: Boolean) {
        _uiState.update { it.copy(isMediaImportOpen = open) }
    }

    fun setExportSheetOpen(open: Boolean) {
        _uiState.update { it.copy(isExportSheetOpen = open) }
    }

    fun renameProject(newName: String) {
        executeCommand(RenameProjectCommand(newName))
    }

    fun importMediaUri(
        uri: Uri,
        forcedType: MediaType? = null,
        preferredTrackId: String? = null
    ) {
        val inspector = mediaInspector ?: run {
            _uiState.update { it.copy(lastErrorMessage = "Media inspector is unavailable.") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(isImportingMedia = true, lastErrorMessage = null) }
            inspector.inspectUri(uri, forcedType = forcedType).fold(
                onSuccess = { asset ->
                    val updatedRecent = recentMediaStore?.recordMedia(asset)
                    _uiState.update { state ->
                        state.copy(
                            isImportingMedia = false,
                            isMediaImportOpen = false,
                            recentMedia = updatedRecent ?: state.recentMedia
                        )
                    }
                    placeAssetOnTimeline(asset, preferredTrackId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isImportingMedia = false,
                            lastErrorMessage = err.message ?: "Failed to inspect selected media file."
                        )
                    }
                }
            )
        }
    }

    fun placeAssetOnTimeline(asset: MediaAsset, preferredTrackId: String? = null) {
        var workingProject = _uiState.value.project
        val preferredTrack = preferredTrackId?.let { workingProject.findTrack(it) }
            ?.takeIf { TimelineConstants.isTrackCompatible(asset.mediaType, it.type) && !it.isLocked }

        val targetTrack = preferredTrack ?: workingProject.tracks
            .sortedBy { it.orderIndex }
            .firstOrNull { TimelineConstants.isTrackCompatible(asset.mediaType, it.type) && !it.isLocked }
            ?: run {
                val neededType = when (asset.mediaType) {
                    MediaType.VIDEO, MediaType.IMAGE -> TrackType.VIDEO
                    MediaType.AUDIO -> TrackType.AUDIO
                    MediaType.TEXT -> TrackType.TEXT
                }
                val newTrackId = "track-${idGenerator().take(6)}"
                executeCommand(AddTrackCommand(type = neededType, trackId = newTrackId))
                workingProject = _uiState.value.project
                workingProject.findTrack(newTrackId)
            }

        if (targetTrack == null) {
            _uiState.update { it.copy(lastErrorMessage = "No compatible unlocked track available.") }
            return
        }

        val trackEndMs = workingProject.clipsForTrack(targetTrack.id)
            .maxOfOrNull { it.timelineEndMs } ?: 0L
        val clipDurationMs = if (asset.mediaType == MediaType.IMAGE) {
            TimelineConstants.DEFAULT_STILL_DURATION_MS
        } else {
            asset.totalDurationMs
        }

        val clipId = "clip-${idGenerator().take(8)}"
        val clip = MediaClip(
            id = clipId,
            sourceUri = asset.uri,
            trackId = targetTrack.id,
            timelineStartMs = trackEndMs,
            durationMs = clipDurationMs,
            sourceInMs = 0L,
            sourceOutMs = clipDurationMs,
            sourceDurationMs = asset.totalDurationMs,
            mediaType = asset.mediaType,
            label = asset.displayName
        )

        executeCommand(AddClipCommand(clip = clip, asset = asset))
        if (_uiState.value.project.findClip(clip.id) != null) {
            _uiState.update { it.copy(selectedClipId = clip.id) }
        }
    }

    fun addTextClip(textContent: String, durationMs: Long = 3_000L) {
        var project = _uiState.value.project
        var textTrack = project.tracks
            .sortedBy { it.orderIndex }
            .firstOrNull { it.type == TrackType.TEXT && !it.isLocked }

        if (textTrack == null) {
            val newTrackId = "track-t-${idGenerator().take(5)}"
            executeCommand(AddTrackCommand(type = TrackType.TEXT, trackId = newTrackId))
            project = _uiState.value.project
            textTrack = project.findTrack(newTrackId)
        }
        if (textTrack == null) return

        val cleaned = textContent.trim().ifBlank { "New Title" }
        val startMs = _uiState.value.playheadMs
        val trackClips = project.clipsForTrack(textTrack.id)
        val nonOverlappingStart = if (trackClips.any { startMs < it.timelineEndMs && startMs + durationMs > it.timelineStartMs }) {
            trackClips.maxOfOrNull { it.timelineEndMs } ?: 0L
        } else {
            startMs
        }

        val clipId = "clip-txt-${idGenerator().take(6)}"
        val clip = MediaClip(
            id = clipId,
            sourceUri = "gve://text/$clipId",
            trackId = textTrack.id,
            timelineStartMs = nonOverlappingStart,
            durationMs = durationMs,
            sourceInMs = 0L,
            sourceOutMs = durationMs,
            sourceDurationMs = TimelineConstants.MAX_STILL_DURATION_MS,
            mediaType = MediaType.TEXT,
            label = cleaned.take(28),
            textContent = cleaned
        )
        executeCommand(AddClipCommand(clip = clip))
        if (_uiState.value.project.findClip(clip.id) != null) {
            _uiState.update { it.copy(selectedClipId = clip.id) }
        }
    }

    fun updateSelectedTextClip(textContent: String) {
        val clipId = _uiState.value.selectedClipId ?: return
        executeCommand(UpdateTextClipCommand(clipId = clipId, textContent = textContent))
    }

    fun addTrack(type: TrackType, name: String? = null) {
        executeCommand(AddTrackCommand(type = type, name = name, trackId = "track-${idGenerator().take(6)}"))
    }

    fun removeTrack(trackId: String) {
        executeCommand(RemoveTrackCommand(trackId = trackId))
    }

    fun toggleTrackMute(trackId: String) {
        val track = _uiState.value.project.findTrack(trackId) ?: return
        executeCommand(SetTrackMutedCommand(trackId = trackId, muted = !track.isMuted))
    }

    fun toggleTrackLock(trackId: String) {
        val track = _uiState.value.project.findTrack(trackId) ?: return
        executeCommand(SetTrackLockedCommand(trackId = trackId, locked = !track.isLocked))
    }

    fun toggleTrackVisible(trackId: String) {
        val track = _uiState.value.project.findTrack(trackId) ?: return
        executeCommand(SetTrackVisibleCommand(trackId = trackId, visible = !track.isVisible))
    }

    fun trimSelectedClipHead(deltaMs: Long, isDragging: Boolean = false) {
        val clipId = _uiState.value.selectedClipId ?: return
        val clip = _uiState.value.project.findClip(clipId) ?: return
        executeCommand(
            command = TrimClipHeadCommand(clipId = clipId, newTimelineStartMs = clip.timelineStartMs + deltaMs),
            allowCoalesce = isDragging
        )
    }

    fun trimSelectedClipTail(deltaMs: Long, isDragging: Boolean = false) {
        val clipId = _uiState.value.selectedClipId ?: return
        val clip = _uiState.value.project.findClip(clipId) ?: return
        executeCommand(
            command = TrimClipTailCommand(clipId = clipId, newTimelineEndMs = clip.timelineEndMs + deltaMs),
            allowCoalesce = isDragging
        )
    }

    fun splitSelectedClipAtPlayhead() {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: return
        val rightClipId = "clip-${idGenerator().take(8)}"
        executeCommand(
            SplitClipCommand(
                clipId = clipId,
                splitPositionMs = state.playheadMs,
                rightClipId = rightClipId
            )
        )
        if (_uiState.value.project.findClip(rightClipId) != null) {
            _uiState.update { it.copy(selectedClipId = rightClipId) }
        }
    }

    fun duplicateSelectedClip() {
        val clipId = _uiState.value.selectedClipId ?: return
        val newClipId = "clip-${idGenerator().take(8)}"
        executeCommand(DuplicateClipCommand(clipId = clipId, newClipId = newClipId))
        if (_uiState.value.project.findClip(newClipId) != null) {
            _uiState.update { it.copy(selectedClipId = newClipId) }
        }
    }

    fun moveSelectedClipBy(deltaMs: Long, isDragging: Boolean = false) {
        val clipId = _uiState.value.selectedClipId ?: return
        val clip = _uiState.value.project.findClip(clipId) ?: return
        executeCommand(
            command = MoveClipCommand(
                clipId = clipId,
                targetTrackId = clip.trackId,
                newTimelineStartMs = (clip.timelineStartMs + deltaMs).coerceAtLeast(0L)
            ),
            allowCoalesce = isDragging
        )
    }

    fun moveClip(clipId: String, targetTrackId: String, newStartMs: Long, isDragging: Boolean = false) {
        executeCommand(
            command = MoveClipCommand(clipId, targetTrackId, newStartMs),
            allowCoalesce = isDragging
        )
    }

    fun deleteSelectedClip(ripple: Boolean = false) {
        val clipId = _uiState.value.selectedClipId ?: return
        executeCommand(DeleteClipCommand(clipId = clipId, ripple = ripple))
        _uiState.update { it.copy(selectedClipId = null) }
    }

    fun setSelectedClipVolume(volume: Float, isMuted: Boolean, isDragging: Boolean = false) {
        val clipId = _uiState.value.selectedClipId ?: return
        executeCommand(
            command = UpdateClipVolumeCommand(clipId = clipId, volume = volume, isMuted = isMuted),
            allowCoalesce = isDragging
        )
    }

    fun setSelectedClipSpeed(speed: Float) {
        val clipId = _uiState.value.selectedClipId ?: return
        executeCommand(UpdateClipSpeedCommand(clipId = clipId, speed = speed))
    }

    fun setSelectedClipTransform(rotationDegrees: Int, cropRect: CropRect) {
        val clipId = _uiState.value.selectedClipId ?: return
        executeCommand(
            UpdateClipTransformCommand(
                clipId = clipId,
                rotationDegrees = rotationDegrees,
                cropRect = cropRect
            )
        )
    }

    fun undo() {
        val previous = historyManager.undo() ?: return
        applyRestoredProject(previous)
    }

    fun redo() {
        val next = historyManager.redo() ?: return
        applyRestoredProject(next)
    }

    fun saveNow() {
        scope.launch {
            val result = autosaveCoordinator.persistNow(_uiState.value.project)
            result.onFailure { err ->
                _uiState.update { it.copy(lastErrorMessage = err.message ?: "Failed to save project.") }
            }
        }
    }

    suspend fun flushAndClose(): Result<Unit> {
        autosaveCoordinator.stop()
        autosaveObserverJob.cancel()
        return autosaveCoordinator.persistNow(_uiState.value.project)
    }

    fun dismissError() {
        _uiState.update { it.copy(lastErrorMessage = null) }
    }

    private fun applyRestoredProject(restored: TimelineProject) {
        _uiState.update { state ->
            val validSelected = state.selectedClipId?.takeIf { restored.findClip(it) != null }
            val clampedPlayhead = state.playheadMs.coerceIn(0L, restored.totalDurationMs.coerceAtLeast(0L))
            state.copy(
                project = restored,
                selectedClipId = validSelected,
                playheadMs = clampedPlayhead,
                history = historyManager.snapshot(),
                lastErrorMessage = null,
                serializedJsonPreview = serializer.encode(restored)
            )
        }
    }

    private fun executeCommand(command: TimelineCommand, allowCoalesce: Boolean = false) {
        val current = _uiState.value.project
        when (val result = historyManager.applyCommand(current, command, allowCoalesce)) {
            is TimelineOperationResult.Success -> {
                val updated = result.value
                _uiState.update { state ->
                    val validSelected = state.selectedClipId?.takeIf { updated.findClip(it) != null }
                    val clampedPlayhead = state.playheadMs.coerceIn(0L, updated.totalDurationMs.coerceAtLeast(0L))
                    state.copy(
                        project = updated,
                        selectedClipId = validSelected,
                        playheadMs = clampedPlayhead,
                        history = historyManager.snapshot(),
                        lastErrorMessage = null,
                        serializedJsonPreview = serializer.encode(updated)
                    )
                }
            }
            is TimelineOperationResult.Failure -> {
                _uiState.update { state ->
                    state.copy(lastErrorMessage = result.error.message)
                }
            }
        }
    }
}
