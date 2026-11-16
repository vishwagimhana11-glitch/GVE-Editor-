package com.gima.gve.data.repository

import com.gima.gve.data.serialization.ProjectJsonSerializer
import com.gima.gve.domain.model.TimelineProject
import com.gima.gve.domain.repository.ProjectRepository
import com.gima.gve.domain.repository.ProjectSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists [TimelineProject] snapshots to app-private storage using a [Mutex] and atomic
 * temp-file replacement without pre-deleting the existing target file.
 */
class LocalFileProjectRepository(
    private val projectsDir: File,
    private val serializer: ProjectJsonSerializer = ProjectJsonSerializer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ProjectRepository {

    private val ioMutex = Mutex()

    override suspend fun saveProject(project: TimelineProject): Result<Unit> = withContext(ioDispatcher) {
        ioMutex.withLock {
            runCatching {
                if (!projectsDir.exists() && !projectsDir.mkdirs()) {
                    throw IllegalStateException("Unable to create projects directory: ${projectsDir.absolutePath}")
                }
                val safeId = sanitizeFileSegment(project.projectId)
                val targetFile = File(projectsDir, "$safeId.gve.json")
                val tempFile = File(projectsDir, "$safeId.gve.json.tmp")

                val encoded = serializer.encode(project)
                tempFile.writeText(encoded, Charsets.UTF_8)

                // Attempt atomic rename first without deleting targetFile ahead of time.
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                Unit
            }
        }
    }

    override suspend fun loadProject(projectId: String): Result<TimelineProject> = withContext(ioDispatcher) {
        ioMutex.withLock {
            runCatching {
                val safeId = sanitizeFileSegment(projectId)
                val file = File(projectsDir, "$safeId.gve.json")
                require(file.exists()) { "Project file not found for id '$projectId'." }
                val content = file.readText(Charsets.UTF_8)
                serializer.decode(content).getOrThrow()
            }
        }
    }

    override suspend fun listRecentProjects(): Result<List<ProjectSummary>> = withContext(ioDispatcher) {
        ioMutex.withLock {
            runCatching {
                if (!projectsDir.exists()) return@runCatching emptyList()
                val files = projectsDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".gve.json")
                } ?: emptyArray()

                files.mapNotNull { file ->
                    runCatching {
                        val raw = file.readText(Charsets.UTF_8)
                        serializer.decode(raw).getOrNull()?.let { project ->
                            ProjectSummary(
                                projectId = project.projectId,
                                name = project.name,
                                updatedAtEpochMs = project.updatedAtEpochMs,
                                totalDurationMs = project.totalDurationMs,
                                clipCount = project.clips.size
                            )
                        }
                    }.getOrNull()
                }.sortedByDescending { it.updatedAtEpochMs }
            }
        }
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> = withContext(ioDispatcher) {
        ioMutex.withLock {
            runCatching {
                val safeId = sanitizeFileSegment(projectId)
                val file = File(projectsDir, "$safeId.gve.json")
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException("Unable to delete project file: ${file.absolutePath}")
                }
                val tempFile = File(projectsDir, "$safeId.gve.json.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                Unit
            }
        }
    }

    private fun sanitizeFileSegment(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "project" }
}

sealed interface AutosaveStatus {
    data object Idle : AutosaveStatus
    data object Saving : AutosaveStatus
    data class Saved(val epochMs: Long) : AutosaveStatus
    data class Failed(val reason: String) : AutosaveStatus
}

/**
 * Debounced autosave observer that listens to project state emissions and persists snapshots
 * without blocking timeline interactions.
 */
@OptIn(FlowPreview::class)
class AutosaveCoordinator(
    private val repository: ProjectRepository,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 750L,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val _status = MutableStateFlow<AutosaveStatus>(AutosaveStatus.Idle)
    val status: StateFlow<AutosaveStatus> = _status.asStateFlow()

    private var watchJob: Job? = null

    fun observe(projectFlow: Flow<TimelineProject>) {
        watchJob?.cancel()
        watchJob = scope.launch {
            projectFlow
                .drop(1)
                .distinctUntilChanged()
                .debounce(debounceMs)
                .collect { project ->
                    persistNow(project)
                }
        }
    }

    suspend fun persistNow(project: TimelineProject): Result<Unit> {
        _status.value = AutosaveStatus.Saving
        val result = repository.saveProject(project)
        _status.value = result.fold(
            onSuccess = { AutosaveStatus.Saved(clockMs()) },
            onFailure = { AutosaveStatus.Failed(it.message ?: "Unknown autosave error") }
        )
        return result
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }
}
