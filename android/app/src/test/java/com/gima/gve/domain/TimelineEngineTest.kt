package com.gima.gve.domain

import com.gima.gve.data.serialization.ProjectJsonSerializer
import com.gima.gve.domain.engine.TimelineEngine
import com.gima.gve.domain.engine.TimelineError
import com.gima.gve.domain.engine.TimelineOperationResult
import com.gima.gve.domain.history.EditHistoryManager
import com.gima.gve.domain.history.MoveClipCommand
import com.gima.gve.domain.history.SplitClipCommand
import com.gima.gve.domain.history.TrimClipHeadCommand
import com.gima.gve.domain.history.TrimClipTailCommand
import com.gima.gve.domain.model.CropRect
import com.gima.gve.domain.model.MediaClip
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TimelineConstants
import com.gima.gve.domain.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineEngineTest {

    private var idCounter = 0
    private val engine = TimelineEngine(
        idGenerator = { "gen-${++idCounter}" },
        clockMs = { 1_700_000_000_000L }
    )

    private fun sampleClip(
        id: String = "clip-1",
        trackId: String = "track-v1",
        timelineStartMs: Long = 0L,
        durationMs: Long = 5_000L,
        sourceInMs: Long = 1_000L,
        sourceDurationMs: Long = 12_000L,
        mediaType: MediaType = MediaType.VIDEO,
        sourceUri: String = "content://media/external/video/media/100"
    ) = MediaClip(
        id = id,
        sourceUri = sourceUri,
        trackId = trackId,
        timelineStartMs = timelineStartMs,
        durationMs = durationMs,
        sourceInMs = sourceInMs,
        sourceOutMs = sourceInMs + durationMs,
        sourceDurationMs = sourceDurationMs,
        mediaType = mediaType,
        label = "Shot_A.mp4"
    )

    @Test
    fun `splitClipAtPlayhead preserves continuous source frames without modifying original`() {
        val base = engine.createEmptyProject("Split Test", projectId = "proj-1")
        val clip = sampleClip(timelineStartMs = 1_000L, durationMs = 4_000L, sourceInMs = 2_000L)
        val withClip = (engine.addClip(base, clip) as TimelineOperationResult.Success).value

        val splitResult = engine.splitClipAtPlayhead(
            project = withClip,
            clipId = clip.id,
            splitPositionMs = 2_500L,
            rightClipId = "clip-1-right"
        )
        assertTrue(splitResult is TimelineOperationResult.Success)
        val outcome = (splitResult as TimelineOperationResult.Success).value

        assertEquals(2, outcome.project.clips.size)
        assertEquals(1_000L, outcome.leftClip.timelineStartMs)
        assertEquals(1_500L, outcome.leftClip.durationMs)
        assertEquals(2_000L, outcome.leftClip.sourceInMs)
        assertEquals(3_500L, outcome.leftClip.sourceOutMs)

        assertEquals(2_500L, outcome.rightClip.timelineStartMs)
        assertEquals(2_500L, outcome.rightClip.durationMs)
        assertEquals(3_500L, outcome.rightClip.sourceInMs)
        assertEquals(6_000L, outcome.rightClip.sourceOutMs)
        assertEquals(clip.sourceUri, outcome.rightClip.sourceUri)
    }

    @Test
    fun `splitClipAtPlayhead rejects splits violating minimum clip duration`() {
        val base = engine.createEmptyProject("Split Bounds", projectId = "proj-2")
        val clip = sampleClip(timelineStartMs = 0L, durationMs = 1_000L, sourceInMs = 0L)
        val withClip = (engine.addClip(base, clip) as TimelineOperationResult.Success).value

        val result = engine.splitClipAtPlayhead(withClip, clip.id, splitPositionMs = 50L)
        assertTrue(result is TimelineOperationResult.Failure)
        assertTrue((result as TimelineOperationResult.Failure).error is TimelineError.InvalidSplitPosition)
    }

    @Test
    fun `trimClipHead clamps to sourceIn zero and neighbor clip end`() {
        val base = engine.createEmptyProject("Trim Test", projectId = "proj-3")
        val first = sampleClip(id = "c1", timelineStartMs = 0L, durationMs = 2_000L, sourceInMs = 0L)
        val second = sampleClip(id = "c2", timelineStartMs = 2_500L, durationMs = 3_000L, sourceInMs = 2_000L)

        var project = (engine.addClip(base, first) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, second) as TimelineOperationResult.Success).value

        val trimmed = (engine.trimClipHead(project, "c2", newTimelineStartMs = 1_500L) as TimelineOperationResult.Success).value
        val updatedSecond = trimmed.findClip("c2")!!
        assertEquals(2_000L, updatedSecond.timelineStartMs)
        assertEquals(1_500L, updatedSecond.sourceInMs)
        assertEquals(3_500L, updatedSecond.durationMs)
    }

    @Test
    fun `trimClipTail clamps to sourceDurationMs and next clip start`() {
        val base = engine.createEmptyProject("Tail Trim", projectId = "proj-4")
        val clip = sampleClip(id = "c1", timelineStartMs = 0L, durationMs = 4_000L, sourceInMs = 1_000L, sourceDurationMs = 6_000L)
        val project = (engine.addClip(base, clip) as TimelineOperationResult.Success).value

        val trimmed = (engine.trimClipTail(project, clip.id, newTimelineEndMs = 9_000L) as TimelineOperationResult.Success).value
        val updated = trimmed.findClip(clip.id)!!
        assertEquals(5_000L, updated.durationMs)
        assertEquals(6_000L, updated.sourceOutMs)
    }

    @Test
    fun `multiple video and audio tracks allow intentional cross-track overlaps while blocking same-track overlaps`() {
        var project = engine.createEmptyProject("Multi-Track", projectId = "proj-mt")
        project = (engine.addTrack(project, TrackType.VIDEO, name = "V2 Overlay", trackId = "track-v2") as TimelineOperationResult.Success).value
        project = (engine.addTrack(project, TrackType.AUDIO, name = "A2 Music", trackId = "track-a2") as TimelineOperationResult.Success).value

        val v1Clip = sampleClip(id = "v1-c1", trackId = "track-v1", timelineStartMs = 0L, durationMs = 5_000L)
        val v2Overlay = sampleClip(id = "v2-c1", trackId = "track-v2", timelineStartMs = 1_000L, durationMs = 3_000L)
        val a1Voice = sampleClip(
            id = "a1-c1",
            trackId = "track-a1",
            timelineStartMs = 500L,
            durationMs = 4_000L,
            mediaType = MediaType.AUDIO,
            sourceUri = "content://media/external/audio/media/201"
        )
        val a2Music = sampleClip(
            id = "a2-c1",
            trackId = "track-a2",
            timelineStartMs = 0L,
            durationMs = 8_000L,
            mediaType = MediaType.AUDIO,
            sourceUri = "content://media/external/audio/media/202"
        )

        project = (engine.addClip(project, v1Clip) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, v2Overlay) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, a1Voice) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, a2Music) as TimelineOperationResult.Success).value

        // Cross-track overlaps validate cleanly
        project.validateIntegrity()
        assertEquals(4, project.clips.size)

        // Same-track overlap on track-v1 must fail
        val conflicting = sampleClip(id = "v1-conflict", trackId = "track-v1", timelineStartMs = 4_000L, durationMs = 2_000L)
        val overlapResult = engine.addClip(project, conflicting)
        assertTrue(overlapResult is TimelineOperationResult.Failure)
        assertTrue((overlapResult as TimelineOperationResult.Failure).error is TimelineError.TrackOverlap)
    }

    @Test
    fun `moveClip and ripple delete update clip positions accurately`() {
        var project = engine.createEmptyProject("Positioning", projectId = "proj-pos")
        project = (engine.addTrack(project, TrackType.VIDEO, name = "V2", trackId = "track-v2") as TimelineOperationResult.Success).value
        val c1 = sampleClip(id = "c1", trackId = "track-v1", timelineStartMs = 0L, durationMs = 2_000L)
        val c2 = sampleClip(id = "c2", trackId = "track-v1", timelineStartMs = 2_000L, durationMs = 3_000L)
        project = (engine.addClip(project, c1) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, c2) as TimelineOperationResult.Success).value

        // Move c2 onto track-v2 at 500ms
        val moved = (engine.moveClip(project, "c2", targetTrackId = "track-v2", newTimelineStartMs = 500L) as TimelineOperationResult.Success).value
        assertEquals("track-v2", moved.findClip("c2")!!.trackId)
        assertEquals(500L, moved.findClip("c2")!!.timelineStartMs)

        // Ripple delete c1 on original project shifts c2 from 2000ms -> 0ms
        val rippled = (engine.deleteClip(project, "c1", ripple = true) as TimelineOperationResult.Success).value
        assertEquals(1, rippled.clips.size)
        assertEquals(0L, rippled.findClip("c2")!!.timelineStartMs)
    }

    @Test
    fun `duplicate clip IDs and duplicate track IDs are rejected`() {
        val project = engine.createEmptyProject("Duplicates", projectId = "proj-dup")
        val dupTrack = engine.addTrack(project, TrackType.VIDEO, trackId = "track-v1")
        assertTrue(dupTrack is TimelineOperationResult.Failure)
        assertTrue((dupTrack as TimelineOperationResult.Failure).error is TimelineError.DuplicateTrackId)

        val c1 = sampleClip(id = "clip-same", timelineStartMs = 0L, durationMs = 1_000L)
        val withFirst = (engine.addClip(project, c1) as TimelineOperationResult.Success).value
        val c2 = sampleClip(id = "clip-same", timelineStartMs = 2_000L, durationMs = 1_000L)
        val dupClip = engine.addClip(withFirst, c2)
        assertTrue(dupClip is TimelineOperationResult.Failure)
        assertTrue((dupClip as TimelineOperationResult.Failure).error is TimelineError.DuplicateClipId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid clip duration below minimum throws IllegalArgumentException`() {
        sampleClip(durationMs = TimelineConstants.MIN_CLIP_DURATION_MS - 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid source URI scheme throws IllegalArgumentException`() {
        sampleClip(sourceUri = "ftp://example.com/bad.mp4")
    }

    @Test
    fun `undo redo and gesture coalescing work deterministically`() {
        val base = engine.createEmptyProject("History Test", projectId = "proj-5")
        val clip = sampleClip(timelineStartMs = 0L, durationMs = 5_000L, sourceInMs = 0L)
        val initial = (engine.addClip(base, clip) as TimelineOperationResult.Success).value

        val history = EditHistoryManager(engine)
        val afterSplit = (history.applyCommand(
            initial,
            SplitClipCommand(clip.id, splitPositionMs = 2_000L, rightClipId = "c-right")
        ) as TimelineOperationResult.Success).value

        // Coalesced continuous trim updates collapse into a single undo entry
        val trim1 = (history.applyCommand(
            afterSplit,
            TrimClipHeadCommand("c-right", newTimelineStartMs = 2_200L),
            allowCoalesce = true
        ) as TimelineOperationResult.Success).value
        val trim2 = (history.applyCommand(
            trim1,
            TrimClipHeadCommand("c-right", newTimelineStartMs = 2_500L),
            allowCoalesce = true
        ) as TimelineOperationResult.Success).value

        assertEquals(2, history.snapshot().undoCount)
        assertEquals(2_500L, trim2.findClip("c-right")!!.timelineStartMs)

        val undoneTrim = history.undo()
        assertNotNull(undoneTrim)
        assertEquals(2_000L, undoneTrim!!.findClip("c-right")!!.timelineStartMs)
        assertTrue(history.snapshot().canRedo)

        val redoneTrim = history.redo()
        assertEquals(2_500L, redoneTrim!!.findClip("c-right")!!.timelineStartMs)

        // Branching edit clears redo stack
        history.undo()
        val branched = (history.applyCommand(
            undoneTrim,
            TrimClipTailCommand("c-right", newTimelineEndMs = 4_500L)
        ) as TimelineOperationResult.Success).value
        assertEquals(2_500L, branched.findClip("c-right")!!.durationMs)
        assertFalse(history.snapshot().canRedo)
    }

    @Test
    fun `json serialization round-trip preserves multi-track project`() {
        val serializer = ProjectJsonSerializer()
        var project = engine.createEmptyProject("Serialization Test", projectId = "proj-json")
        project = (engine.addTrack(project, TrackType.VIDEO, name = "V2 Overlay", trackId = "track-v2") as TimelineOperationResult.Success).value
        project = (engine.addClip(project, sampleClip(id = "v1-c1", trackId = "track-v1")) as TimelineOperationResult.Success).value
        project = (engine.addClip(
            project,
            sampleClip(
                id = "a1-c1",
                trackId = "track-a1",
                mediaType = MediaType.AUDIO,
                sourceUri = "content://media/external/audio/media/300"
            )
        ) as TimelineOperationResult.Success).value

        val encoded = serializer.encode(project)
        val decoded = serializer.decode(encoded).getOrThrow()

        assertEquals(project, decoded)
    }

    @Test
    fun `phase 3 operations support speed transform volume text overlay and track visibility`() {
        var project = engine.createEmptyProject("Phase 3 Ops", projectId = "proj-p3")
        project = (engine.renameProject(project, "Studio Cut") as TimelineOperationResult.Success).value
        assertEquals("Studio Cut", project.name)

        project = (engine.addTrack(project, TrackType.OVERLAY, name = "OV1", trackId = "track-ov1") as TimelineOperationResult.Success).value
        project = (engine.addTrack(project, TrackType.TEXT, name = "T1", trackId = "track-t1") as TimelineOperationResult.Success).value

        val mainClip = sampleClip(id = "v1-main", trackId = "track-v1", timelineStartMs = 0L, durationMs = 4_000L, sourceInMs = 0L)
        val overlayClip = sampleClip(
            id = "ov-img",
            trackId = "track-ov1",
            timelineStartMs = 500L,
            durationMs = 2_000L,
            sourceInMs = 0L,
            sourceDurationMs = TimelineConstants.MAX_STILL_DURATION_MS,
            mediaType = MediaType.IMAGE,
            sourceUri = "content://media/external/images/media/44"
        )
        val textClip = MediaClip(
            id = "txt-1",
            sourceUri = "gve://text/txt-1",
            trackId = "track-t1",
            timelineStartMs = 0L,
            durationMs = 3_000L,
            sourceInMs = 0L,
            sourceOutMs = 3_000L,
            sourceDurationMs = TimelineConstants.MAX_STILL_DURATION_MS,
            mediaType = MediaType.TEXT,
            label = "Intro Title",
            textContent = "Intro Title"
        )

        project = (engine.addClip(project, mainClip) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, overlayClip) as TimelineOperationResult.Success).value
        project = (engine.addClip(project, textClip) as TimelineOperationResult.Success).value

        // Speed 2.0x halves 4000ms span -> 2000ms duration
        project = (engine.updateClipSpeed(project, "v1-main", 2.0f) as TimelineOperationResult.Success).value
        assertEquals(2_000L, project.findClip("v1-main")!!.durationMs)
        assertEquals(2.0f, project.findClip("v1-main")!!.speed)

        // Duplicate places copy cleanly after v1-main
        project = (engine.duplicateClip(project, "v1-main", newClipId = "v1-copy") as TimelineOperationResult.Success).value
        assertEquals(2_000L, project.findClip("v1-copy")!!.timelineStartMs)

        // Transform & volume & text update
        project = (engine.updateClipTransform(project, "ov-img", 90, CropRect(0.1f, 0.1f, 0.9f, 0.9f)) as TimelineOperationResult.Success).value
        assertEquals(90, project.findClip("ov-img")!!.rotationDegrees)

        project = (engine.updateClipVolume(project, "v1-main", 1.5f, isMuted = true) as TimelineOperationResult.Success).value
        assertEquals(1.5f, project.findClip("v1-main")!!.volume)
        assertTrue(project.findClip("v1-main")!!.isMuted)

        project = (engine.updateTextClip(project, "txt-1", "Updated Title") as TimelineOperationResult.Success).value
        assertEquals("Updated Title", project.findClip("txt-1")!!.textContent)

        project = (engine.setTrackVisible(project, "track-ov1", visible = false) as TimelineOperationResult.Success).value
        assertFalse(project.findTrack("track-ov1")!!.isVisible)

        project.validateIntegrity()
    }

    @Test
    fun `endCoalescing keeps two separate drag gestures as two undo entries`() {
        val base = engine.createEmptyProject("Drag Coalesce", projectId = "proj-drag")
        val clip = sampleClip(id = "c-drag", timelineStartMs = 0L, durationMs = 2_000L)
        val initial = (engine.addClip(base, clip) as TimelineOperationResult.Success).value

        val history = EditHistoryManager(engine)

        // Drag 1
        val d1a = (history.applyCommand(
            initial,
            MoveClipCommand("c-drag", "track-v1", 200L),
            allowCoalesce = true
        ) as TimelineOperationResult.Success).value
        val d1b = (history.applyCommand(
            d1a,
            MoveClipCommand("c-drag", "track-v1", 500L),
            allowCoalesce = true
        ) as TimelineOperationResult.Success).value
        history.endCoalescing()

        // Drag 2 on the same clip
        val d2a = (history.applyCommand(
            d1b,
            MoveClipCommand("c-drag", "track-v1", 900L),
            allowCoalesce = true
        ) as TimelineOperationResult.Success).value
        val d2b = (history.applyCommand(
            d2a,
            MoveClipCommand("c-drag", "track-v1", 1_200L),
            allowCoalesce = true
        ) as TimelineOperationResult.Success).value
        history.endCoalescing()

        assertEquals(2, history.snapshot().undoCount)
        assertEquals(1_200L, d2b.findClip("c-drag")!!.timelineStartMs)

        val afterFirstUndo = history.undo()!!
        assertEquals(500L, afterFirstUndo.findClip("c-drag")!!.timelineStartMs)

        val afterSecondUndo = history.undo()!!
        assertEquals(0L, afterSecondUndo.findClip("c-drag")!!.timelineStartMs)
    }
}
