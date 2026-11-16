package com.gima.gve.ui.state

import android.net.Uri
import com.gima.gve.data.media.RecentMediaStore
import com.gima.gve.data.media.SafMediaInspector
import com.gima.gve.domain.engine.TimelineEngine
import com.gima.gve.domain.engine.TimelineOperationResult
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.repository.ProjectRepository
import com.gima.gve.domain.repository.ProjectSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeUiState(
    val recentProjects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * State holder for the GVE Editor Home screen (New Project, Import Video, Recent Projects,
 * Rename, Delete, and Recovery).
 */
class HomeStateHolder(
    private val repository: ProjectRepository,
    private val mediaInspector: SafMediaInspector? = null,
    private val recentMediaStore: RecentMediaStore? = null,
    private val scope: CoroutineScope,
    private val engine: TimelineEngine = TimelineEngine()
) {
    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshRecentProjects()
    }

    fun refreshRecentProjects() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.listRecentProjects().fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(recentProjects = list, isLoading = false, errorMessage = null) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Failed to load recent projects."
                        )
                    }
                }
            )
        }
    }

    fun createNewProject(name: String, onCreated: (TimelineProject) -> Unit) {
        scope.launch {
            val project = engine.createEmptyProject(name.ifBlank { "Untitled Project" })
            repository.saveProject(project).fold(
                onSuccess = {
                    refreshRecentProjects()
                    onCreated(project)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.message ?: "Failed to create project.")
                    }
                }
            )
        }
    }

    fun importVideoIntoNewProject(uri: Uri, onCreated: (TimelineProject) -> Unit) {
        val inspector = mediaInspector ?: run {
            _uiState.update { it.copy(errorMessage = "Media inspector is unavailable.") }
            return
        }
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            inspector.inspectUri(uri, forcedType = MediaType.VIDEO).fold(
                onSuccess = { asset ->
                    recentMediaStore?.recordMedia(asset)
                    val projectTitle = asset.displayName.substringBeforeLast('.').ifBlank { "Imported Edit" }
                    val empty = engine.createEmptyProject(projectTitle)
                    val clip = MediaClip(
                        id = "clip-${UUID.randomUUID().toString().take(8)}",
                        sourceUri = asset.uri,
                        trackId = "track-v1",
                        timelineStartMs = 0L,
                        durationMs = asset.totalDurationMs,
                        sourceInMs = 0L,
                        sourceOutMs = asset.totalDurationMs,
                        sourceDurationMs = asset.totalDurationMs,
                        mediaType = MediaType.VIDEO,
                        label = asset.displayName
                    )
                    when (val addResult = engine.addClip(empty, clip, asset = asset)) {
                        is TimelineOperationResult.Success -> {
                            repository.saveProject(addResult.value).fold(
                                onSuccess = {
                                    refreshRecentProjects()
                                    onCreated(addResult.value)
                                },
                                onFailure = { err ->
                                    _uiState.update {
                                        it.copy(isLoading = false, errorMessage = err.message ?: "Save failed.")
                                    }
                                }
                            )
                        }
                        is TimelineOperationResult.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = addResult.error.message)
                            }
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Unable to import selected video.")
                    }
                }
            )
        }
    }

    fun renameProject(projectId: String, newName: String) {
        scope.launch {
            repository.loadProject(projectId).fold(
                onSuccess = { existing ->
                    when (val renamed = engine.renameProject(existing, newName)) {
                        is TimelineOperationResult.Success -> {
                            repository.saveProject(renamed.value).fold(
                                onSuccess = { refreshRecentProjects() },
                                onFailure = { err ->
                                    _uiState.update {
                                        it.copy(errorMessage = err.message ?: "Unable to save renamed project.")
                                    }
                                }
                            )
                        }
                        is TimelineOperationResult.Failure -> {
                            _uiState.update { it.copy(errorMessage = renamed.error.message) }
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Unable to load project for rename.") }
                }
            )
        }
    }

    fun openProject(projectId: String, onOpened: (TimelineProject) -> Unit) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.loadProject(projectId).fold(
                onSuccess = { project ->
                    _uiState.update { it.copy(isLoading = false) }
                    onOpened(project)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Unable to load project '$projectId'."
                        )
                    }
                }
            )
        }
    }

    fun deleteProject(projectId: String) {
        scope.launch {
            repository.deleteProject(projectId).fold(
                onSuccess = { refreshRecentProjects() },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to delete project.") }
                }
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
