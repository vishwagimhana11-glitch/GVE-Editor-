package com.gima.gve.domain.repository

import com.gima.gve.domain.model.TimelineProject

data class ProjectSummary(
    val projectId: String,
    val name: String,
    val updatedAtEpochMs: Long,
    val totalDurationMs: Long,
    val clipCount: Int
)

interface ProjectRepository {
    suspend fun saveProject(project: TimelineProject): Result<Unit>
    suspend fun loadProject(projectId: String): Result<TimelineProject>
    suspend fun listRecentProjects(): Result<List<ProjectSummary>>
    suspend fun deleteProject(projectId: String): Result<Unit>
}
