package com.gima.gve.data

import com.gima.gve.data.repository.AutosaveCoordinator
import com.gima.gve.data.repository.AutosaveStatus
import com.gima.gve.data.repository.LocalFileProjectRepository
import com.gima.gve.data.serialization.ProjectJsonSerializer
import com.gima.gve.domain.engine.TimelineEngine
import com.gima.gve.domain.engine.TimelineOperationResult
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val engine = TimelineEngine(clockMs = { 1_700_000_000_000L })

    @Test
    fun `repository saves loads lists and deletes projects safely`() = runTest {
        val dir = tempFolder.newFolder("projects")
        val repository = LocalFileProjectRepository(
            projectsDir = dir,
            ioDispatcher = Dispatchers.Unconfined
        )

        var project = engine.createEmptyProject("Persistence Demo", projectId = "proj-persist")
        val clip = MediaClip(
            id = "clip-1",
            sourceUri = "content://media/external/video/media/99",
            trackId = "track-v1",
            timelineStartMs = 0L,
            durationMs = 3_000L,
            sourceInMs = 500L,
            sourceOutMs = 3_500L,
            sourceDurationMs = 8_000L,
            mediaType = MediaType.VIDEO,
            label = "Demo.mp4"
        )
        project = (engine.addClip(project, clip) as TimelineOperationResult.Success).value

        assertTrue(repository.saveProject(project).isSuccess)

        val loaded = repository.loadProject("proj-persist").getOrThrow()
        assertEquals(project, loaded)

        // Corrupted file alongside valid project must not crash listRecentProjects
        File(dir, "corrupted.gve.json").writeText("{not valid json", Charsets.UTF_8)
        val summaries = repository.listRecentProjects().getOrThrow()
        assertEquals(1, summaries.size)
        assertEquals("Persistence Demo", summaries.first().name)

        assertTrue(repository.deleteProject("proj-persist").isSuccess)
        assertTrue(repository.loadProject("proj-persist").isFailure)
    }

    @Test
    fun `serializer rejects JSON containing duplicate clip IDs`() {
        val serializer = ProjectJsonSerializer()
        val project = engine.createEmptyProject("Base", projectId = "proj-dup-json")
        val clip = MediaClip(
            id = "clip-dup",
            sourceUri = "content://media/external/video/media/12",
            trackId = "track-v1",
            timelineStartMs = 0L,
            durationMs = 1_000L,
            sourceInMs = 0L,
            sourceOutMs = 1_000L,
            sourceDurationMs = 5_000L,
            mediaType = MediaType.VIDEO
        )
        val withOne = (engine.addClip(project, clip) as TimelineOperationResult.Success).value
        val encoded = serializer.encode(withOne)
        val tampered = encoded.replace(
            "\"clips\": [",
            "\"clips\": [\n    {\n      \"id\": \"clip-dup\",\n      \"sourceUri\": \"content://media/external/video/media/12\",\n      \"trackId\": \"track-v1\",\n      \"timelineStartMs\": 2000,\n      \"durationMs\": 1000,\n      \"sourceInMs\": 0,\n      \"sourceOutMs\": 1000,\n      \"sourceDurationMs\": 5000,\n      \"mediaType\": \"VIDEO\"\n    },"
        )

        val decoded = serializer.decode(tampered)
        assertTrue(decoded.isFailure)
    }

    @Test
    fun `autosave coordinator persists debounced project changes for recovery`() = runTest {
        val dir = tempFolder.newFolder("autosave")
        val repository = LocalFileProjectRepository(
            projectsDir = dir,
            ioDispatcher = Dispatchers.Unconfined
        )

        val initial = engine.createEmptyProject("Autosave Edit", projectId = "proj-auto")
        val stateFlow = MutableStateFlow(initial)
        val coordinator = AutosaveCoordinator(
            repository = repository,
            scope = backgroundScope,
            debounceMs = 500L,
            clockMs = { 1_700_000_000_500L }
        )
        coordinator.observe(stateFlow)
        runCurrent()

        val addedClip = MediaClip(
            id = "clip-auto-1",
            sourceUri = "content://media/external/video/media/55",
            trackId = "track-v1",
            timelineStartMs = 0L,
            durationMs = 2_500L,
            sourceInMs = 0L,
            sourceOutMs = 2_500L,
            sourceDurationMs = 5_000L,
            mediaType = MediaType.VIDEO,
            label = "Recovered.mp4"
        )
        val updated = (engine.addClip(initial, addedClip) as TimelineOperationResult.Success).value
        stateFlow.value = updated

        advanceTimeBy(600L)
        advanceUntilIdle()

        assertTrue(coordinator.status.value is AutosaveStatus.Saved)

        // Simulate app restart recovery by creating a fresh repository reading from disk
        val recovered = LocalFileProjectRepository(
            projectsDir = dir,
            ioDispatcher = Dispatchers.Unconfined
        ).loadProject("proj-auto").getOrThrow()

        assertEquals(updated, recovered)
        coordinator.stop()
    }
}
