package com.gima.gve.ui

import com.gima.gve.data.repository.LocalFileProjectRepository
import com.gima.gve.domain.engine.TimelineEngine
import com.gima.gve.domain.model.CropRect
import com.gima.gve.domain.model.MediaAsset
import com.gima.gve.domain.model.MediaType
import com.gima.gve.rendering.media3.PreviewQuality
import com.gima.gve.rendering.media3.PreviewScaleMode
import com.gima.gve.ui.state.EditorStateHolder
import com.gima.gve.ui.state.EditorToolTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorStateHolderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var seq = 0
    private val engine = TimelineEngine(
        idGenerator = { "id-${++seq}" },
        clockMs = { 1_700_000_000_000L }
    )

    @Test
    fun `clip selection tool panel toggle and playhead seek behave deterministically`() = runTest {
        val repo = LocalFileProjectRepository(
            projectsDir = tempFolder.newFolder("state_ui"),
            ioDispatcher = Dispatchers.Unconfined
        )
        val project = engine.createEmptyProject("UI State Demo", projectId = "proj-ui-1")
        val holder = EditorStateHolder(
            initialProject = project,
            scope = backgroundScope,
            repository = repo,
            engine = engine,
            idGenerator = { "u-${++seq}" }
        )

        val videoAsset = MediaAsset(
            id = "asset-v1",
            uri = "content://media/external/video/media/700",
            displayName = "Clip_700.mp4",
            mediaType = MediaType.VIDEO,
            totalDurationMs = 6_000L,
            width = 1920,
            height = 1080
        )

        holder.placeAssetOnTimeline(videoAsset)
        val addedClipId = holder.uiState.value.selectedClipId
        assertNotNull(addedClipId)
        assertEquals(6_000L, holder.uiState.value.project.totalDurationMs)

        // Selecting a clip opens Edit tool tab when none was open; toggling closes it
        holder.selectClip(addedClipId)
        assertEquals(EditorToolTab.EDIT, holder.uiState.value.activeToolTab)
        holder.selectToolTab(EditorToolTab.EDIT)
        assertNull(holder.uiState.value.activeToolTab)
        holder.selectToolTab(EditorToolTab.SPEED)
        assertEquals(EditorToolTab.SPEED, holder.uiState.value.activeToolTab)
        holder.closeToolPanel()
        assertNull(holder.uiState.value.activeToolTab)

        // Playhead seeking clamps to [0, totalDurationMs]
        holder.seekTo(2_400L)
        assertEquals(2_400L, holder.uiState.value.playheadMs)
        holder.seekTo(99_000L)
        assertEquals(6_000L, holder.uiState.value.playheadMs)

        // Preview settings update cleanly
        holder.setPreviewQuality(PreviewQuality.HIGH_1080P)
        assertEquals(PreviewQuality.HIGH_1080P, holder.uiState.value.previewQuality)
        holder.togglePreviewScaleMode()
        assertEquals(PreviewScaleMode.FILL, holder.uiState.value.previewScaleMode)

        holder.flushAndClose()
    }

    @Test
    fun `undo redo and project rename persist and restore accurately across state holders`() = runTest {
        val dir = tempFolder.newFolder("state_restore")
        val repo = LocalFileProjectRepository(
            projectsDir = dir,
            ioDispatcher = Dispatchers.Unconfined
        )
        val initial = engine.createEmptyProject("Draft Cut", projectId = "proj-restore")
        val holder = EditorStateHolder(
            initialProject = initial,
            scope = backgroundScope,
            repository = repo,
            engine = engine,
            idGenerator = { "r-${++seq}" }
        )

        val videoAsset = MediaAsset(
            id = "asset-1",
            uri = "content://media/external/video/media/10",
            displayName = "Main.mp4",
            mediaType = MediaType.VIDEO,
            totalDurationMs = 5_000L
        )
        holder.placeAssetOnTimeline(videoAsset)
        holder.seekTo(2_000L)
        holder.splitSelectedClipAtPlayhead()
        assertEquals(2, holder.uiState.value.project.clips.size)

        holder.addTextClip("Chapter 1", durationMs = 2_500L)
        assertEquals(3, holder.uiState.value.project.clips.size)

        holder.setSelectedClipTransform(90, CropRect(0f, 0f, 1f, 1f))
        holder.renameProject("Final Studio Cut")
        assertEquals("Final Studio Cut", holder.uiState.value.project.name)

        // Undo rename -> Undo transform -> check state
        assertTrue(holder.uiState.value.history.canUndo)
        holder.undo()
        assertEquals("Draft Cut", holder.uiState.value.project.name)
        holder.redo()
        assertEquals("Final Studio Cut", holder.uiState.value.project.name)

        // Flush to disk and re-hydrate in a fresh EditorStateHolder
        val saveResult = holder.flushAndClose()
        assertTrue(saveResult.isSuccess)
        advanceUntilIdle()

        val reloadedProject = repo.loadProject("proj-restore").getOrThrow()
        val restoredHolder = EditorStateHolder(
            initialProject = reloadedProject,
            scope = backgroundScope,
            repository = repo,
            engine = engine,
            idGenerator = { "r2-${++seq}" }
        )

        assertEquals("Final Studio Cut", restoredHolder.uiState.value.project.name)
        assertEquals(3, restoredHolder.uiState.value.project.clips.size)
        assertFalse(restoredHolder.uiState.value.history.canUndo)
        assertNotNull(restoredHolder.uiState.value.selectedClipId)

        restoredHolder.flushAndClose()
    }
}
