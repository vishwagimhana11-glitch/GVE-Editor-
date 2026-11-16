import {
  HistoryEntry,
  MediaClip,
  MediaType,
  TIMELINE_CONSTANTS,
  TimelineProject,
  TimelineResult,
  TimelineTrack,
  TrackType,
} from './timelineTypes';

export function clipTimelineEndMs(clip: MediaClip): number {
  return clip.timelineStartMs + clip.durationMs;
}

export function getProjectDurationMs(project: TimelineProject): number {
  if (project.clips.length === 0) return 0;
  return Math.max(...project.clips.map(clipTimelineEndMs));
}

export function clipsForTrack(project: TimelineProject, trackId: string): MediaClip[] {
  return project.clips
    .filter((c) => c.trackId === trackId)
    .sort((a, b) => a.timelineStartMs - b.timelineStartMs);
}

function isCompatible(mediaType: MediaType, trackType: TrackType): boolean {
  return (
    (mediaType === 'VIDEO' && trackType === 'VIDEO') ||
    (mediaType === 'AUDIO' && trackType === 'AUDIO')
  );
}

function findOverlappingClip(
  clips: MediaClip[],
  startMs: number,
  endMs: number,
  excludeClipId: string | null
): MediaClip | undefined {
  return clips.find(
    (c) =>
      c.id !== excludeClipId &&
      startMs < clipTimelineEndMs(c) &&
      endMs > c.timelineStartMs
  );
}

export function generateId(prefix: string): string {
  const rand = Math.random().toString(36).slice(2, 8);
  return `${prefix}-${rand}`;
}

export function createSampleProject(): TimelineProject {
  const now = Date.now();
  return {
    schemaVersion: TIMELINE_CONSTANTS.SCHEMA_VERSION,
    projectId: 'gve-demo-phase2',
    name: 'Kyoto Street Cut',
    createdAtEpochMs: now,
    updatedAtEpochMs: now,
    settings: {
      width: 1920,
      height: 1080,
      fps: 30,
    },
    tracks: [
      {
        id: 'track-v2',
        name: 'V2 Overlay',
        type: 'VIDEO',
        orderIndex: 0,
        isMuted: false,
        isLocked: false,
        isVisible: true,
      },
      {
        id: 'track-v1',
        name: 'V1 Main',
        type: 'VIDEO',
        orderIndex: 1,
        isMuted: false,
        isLocked: false,
        isVisible: true,
      },
      {
        id: 'track-a1',
        name: 'A1 Voice',
        type: 'AUDIO',
        orderIndex: 2,
        isMuted: false,
        isLocked: false,
        isVisible: true,
      },
      {
        id: 'track-a2',
        name: 'A2 Music',
        type: 'AUDIO',
        orderIndex: 3,
        isMuted: false,
        isLocked: false,
        isVisible: true,
      },
    ],
    mediaAssets: [
      {
        id: 'asset-v-alley',
        uri: 'content://media/external/video/media/1042',
        displayName: 'Alley_Lanterns_4K.mp4',
        mediaType: 'VIDEO',
        totalDurationMs: 12000,
        width: 1920,
        height: 1080,
        hasAudioStream: true,
      },
      {
        id: 'asset-v-train',
        uri: 'content://media/external/video/media/1043',
        displayName: 'Station_Arrival.mp4',
        mediaType: 'VIDEO',
        totalDurationMs: 10000,
        width: 1920,
        height: 1080,
        hasAudioStream: true,
      },
      {
        id: 'asset-v-broll',
        uri: 'content://media/external/video/media/1044',
        displayName: 'Rain_CloseUp.mp4',
        mediaType: 'VIDEO',
        totalDurationMs: 8000,
        width: 1920,
        height: 1080,
        hasAudioStream: false,
      },
      {
        id: 'asset-a-vo',
        uri: 'content://media/external/audio/media/2011',
        displayName: 'Narration_Take2.m4a',
        mediaType: 'AUDIO',
        totalDurationMs: 9000,
        hasAudioStream: true,
      },
      {
        id: 'asset-a-bgm',
        uri: 'content://media/external/audio/media/2012',
        displayName: 'Night_Synth_Bed.wav',
        mediaType: 'AUDIO',
        totalDurationMs: 24000,
        hasAudioStream: true,
      },
    ],
    clips: [
      {
        id: 'clip-v1-a',
        sourceUri: 'content://media/external/video/media/1042',
        trackId: 'track-v1',
        timelineStartMs: 0,
        durationMs: 5500,
        sourceInMs: 1000,
        sourceOutMs: 6500,
        sourceDurationMs: 12000,
        mediaType: 'VIDEO',
        label: 'Alley_Lanterns_4K.mp4',
        volume: 1.0,
        isMuted: false,
      },
      {
        id: 'clip-v1-b',
        sourceUri: 'content://media/external/video/media/1043',
        trackId: 'track-v1',
        timelineStartMs: 5500,
        durationMs: 4500,
        sourceInMs: 500,
        sourceOutMs: 5000,
        sourceDurationMs: 10000,
        mediaType: 'VIDEO',
        label: 'Station_Arrival.mp4',
        volume: 1.0,
        isMuted: false,
      },
      {
        id: 'clip-v2-a',
        sourceUri: 'content://media/external/video/media/1044',
        trackId: 'track-v2',
        timelineStartMs: 2000,
        durationMs: 3000,
        sourceInMs: 1000,
        sourceOutMs: 4000,
        sourceDurationMs: 8000,
        mediaType: 'VIDEO',
        label: 'Rain_CloseUp.mp4',
        volume: 0.6,
        isMuted: true,
      },
      {
        id: 'clip-a1-a',
        sourceUri: 'content://media/external/audio/media/2011',
        trackId: 'track-a1',
        timelineStartMs: 800,
        durationMs: 5200,
        sourceInMs: 0,
        sourceOutMs: 5200,
        sourceDurationMs: 9000,
        mediaType: 'AUDIO',
        label: 'Narration_Take2.m4a',
        volume: 1.0,
        isMuted: false,
      },
      {
        id: 'clip-a2-a',
        sourceUri: 'content://media/external/audio/media/2012',
        trackId: 'track-a2',
        timelineStartMs: 0,
        durationMs: 10000,
        sourceInMs: 2000,
        sourceOutMs: 12000,
        sourceDurationMs: 24000,
        mediaType: 'AUDIO',
        label: 'Night_Synth_Bed.wav',
        volume: 0.45,
        isMuted: false,
      },
    ],
  };
}

export function addTrack(
  project: TimelineProject,
  type: TrackType
): TimelineResult<TimelineProject> {
  const sameTypeCount = project.tracks.filter((t) => t.type === type).length;
  const name = type === 'VIDEO' ? `V${sameTypeCount + 1}` : `A${sameTypeCount + 1}`;
  const newTrack: TimelineTrack = {
    id: generateId(type === 'VIDEO' ? 'track-v' : 'track-a'),
    name,
    type,
    orderIndex:
      type === 'VIDEO'
        ? 0
        : (Math.max(-1, ...project.tracks.map((t) => t.orderIndex)) + 1),
    isMuted: false,
    isLocked: false,
    isVisible: true,
  };

  const merged =
    type === 'VIDEO'
      ? [newTrack, ...project.tracks]
      : [...project.tracks, newTrack];

  const reindexed = merged.map((t, idx) => ({ ...t, orderIndex: idx }));

  return {
    ok: true,
    value: {
      ...project,
      tracks: reindexed,
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function removeTrack(
  project: TimelineProject,
  trackId: string
): TimelineResult<TimelineProject> {
  const track = project.tracks.find((t) => t.id === trackId);
  if (!track) return { ok: false, error: `Track '${trackId}' was not found.` };
  if (track.isLocked) return { ok: false, error: `Track '${track.name}' is locked.` };

  const sameTypeCount = project.tracks.filter((t) => t.type === track.type).length;
  if (sameTypeCount <= 1) {
    return { ok: false, error: `Cannot remove the last remaining ${track.type} track.` };
  }

  const remainingTracks = project.tracks
    .filter((t) => t.id !== trackId)
    .sort((a, b) => a.orderIndex - b.orderIndex)
    .map((t, idx) => ({ ...t, orderIndex: idx }));

  return {
    ok: true,
    value: {
      ...project,
      tracks: remainingTracks,
      clips: project.clips.filter((c) => c.trackId !== trackId),
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function toggleTrackMute(
  project: TimelineProject,
  trackId: string
): TimelineResult<TimelineProject> {
  return {
    ok: true,
    value: {
      ...project,
      tracks: project.tracks.map((t) =>
        t.id === trackId ? { ...t, isMuted: !t.isMuted } : t
      ),
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function toggleTrackLock(
  project: TimelineProject,
  trackId: string
): TimelineResult<TimelineProject> {
  return {
    ok: true,
    value: {
      ...project,
      tracks: project.tracks.map((t) =>
        t.id === trackId ? { ...t, isLocked: !t.isLocked } : t
      ),
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function addClipToTrack(
  project: TimelineProject,
  trackId: string,
  label?: string,
  durationMs = 3500
): TimelineResult<{ project: TimelineProject; clip: MediaClip }> {
  const track = project.tracks.find((t) => t.id === trackId);
  if (!track) return { ok: false, error: `Track '${trackId}' not found.` };
  if (track.isLocked) return { ok: false, error: `Track '${track.name}' is locked.` };

  const mediaType: MediaType = track.type === 'VIDEO' ? 'VIDEO' : 'AUDIO';
  const trackClips = clipsForTrack(project, trackId);
  const startMs = trackClips.length > 0 ? Math.max(...trackClips.map(clipTimelineEndMs)) : 0;
  const clipId = generateId('clip');
  const clip: MediaClip = {
    id: clipId,
    sourceUri:
      mediaType === 'VIDEO'
        ? `content://media/external/video/media/${clipId}`
        : `content://media/external/audio/media/${clipId}`,
    trackId,
    timelineStartMs: startMs,
    durationMs,
    sourceInMs: 500,
    sourceOutMs: 500 + durationMs,
    sourceDurationMs: durationMs * 2 + 1000,
    mediaType,
    label:
      label ??
      (mediaType === 'VIDEO' ? `Camera_${clipId.slice(-4)}.mp4` : `Audio_${clipId.slice(-4)}.m4a`),
    volume: 1.0,
    isMuted: false,
  };

  return {
    ok: true,
    value: {
      project: {
        ...project,
        clips: [...project.clips, clip],
        updatedAtEpochMs: Date.now(),
      },
      clip,
    },
  };
}

export function trimClipHead(
  project: TimelineProject,
  clipId: string,
  newTimelineStartMs: number
): TimelineResult<TimelineProject> {
  const clip = project.clips.find((c) => c.id === clipId);
  if (!clip) return { ok: false, error: `Clip '${clipId}' not found.` };
  const track = project.tracks.find((t) => t.id === clip.trackId);
  if (!track) return { ok: false, error: `Track '${clip.trackId}' not found.` };
  if (track.isLocked) return { ok: false, error: `Track '${track.name}' is locked.` };

  const trackClips = clipsForTrack(project, track.id);
  const prevClip = [...trackClips]
    .reverse()
    .find((c) => c.id !== clip.id && clipTimelineEndMs(c) <= clip.timelineStartMs);

  const minBySource = clip.timelineStartMs - clip.sourceInMs;
  const minByNeighbor = prevClip ? clipTimelineEndMs(prevClip) : 0;
  const minAllowedStart = Math.max(0, minBySource, minByNeighbor);
  const maxAllowedStart = clipTimelineEndMs(clip) - TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS;

  if (minAllowedStart > maxAllowedStart) {
    return { ok: false, error: 'Clip cannot be trimmed further from the head.' };
  }

  const effectiveStart = Math.round(
    Math.min(maxAllowedStart, Math.max(minAllowedStart, newTimelineStartMs))
  );
  const deltaMs = effectiveStart - clip.timelineStartMs;
  const newSourceIn = clip.sourceInMs + deltaMs;
  const newDuration = clip.sourceOutMs - newSourceIn;

  const updatedClip: MediaClip = {
    ...clip,
    timelineStartMs: effectiveStart,
    sourceInMs: newSourceIn,
    durationMs: newDuration,
  };

  return {
    ok: true,
    value: {
      ...project,
      clips: project.clips.map((c) => (c.id === clipId ? updatedClip : c)),
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function trimClipTail(
  project: TimelineProject,
  clipId: string,
  newTimelineEndMs: number
): TimelineResult<TimelineProject> {
  const clip = project.clips.find((c) => c.id === clipId);
  if (!clip) return { ok: false, error: `Clip '${clipId}' not found.` };
  const track = project.tracks.find((t) => t.id === clip.trackId);
  if (!track) return { ok: false, error: `Track '${clip.trackId}' not found.` };
  if (track.isLocked) return { ok: false, error: `Track '${track.name}' is locked.` };

  const trackClips = clipsForTrack(project, track.id);
  const nextClip = trackClips.find(
    (c) => c.id !== clip.id && c.timelineStartMs >= clipTimelineEndMs(clip)
  );

  const minAllowedEnd = clip.timelineStartMs + TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS;
  const maxBySource = clip.timelineStartMs + (clip.sourceDurationMs - clip.sourceInMs);
  const maxAllowedEnd = nextClip ? Math.min(maxBySource, nextClip.timelineStartMs) : maxBySource;

  if (minAllowedEnd > maxAllowedEnd) {
    return { ok: false, error: 'Clip cannot be trimmed further from the tail.' };
  }

  const effectiveEnd = Math.round(
    Math.min(maxAllowedEnd, Math.max(minAllowedEnd, newTimelineEndMs))
  );
  const newDuration = effectiveEnd - clip.timelineStartMs;
  const newSourceOut = clip.sourceInMs + newDuration;

  const updatedClip: MediaClip = {
    ...clip,
    durationMs: newDuration,
    sourceOutMs: newSourceOut,
  };

  return {
    ok: true,
    value: {
      ...project,
      clips: project.clips.map((c) => (c.id === clipId ? updatedClip : c)),
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function splitClipAtPlayhead(
  project: TimelineProject,
  clipId: string,
  splitPositionMs: number
): TimelineResult<{ project: TimelineProject; leftClip: MediaClip; rightClip: MediaClip }> {
  const clip = project.clips.find((c) => c.id === clipId);
  if (!clip) return { ok: false, error: `Clip '${clipId}' not found.` };
  const track = project.tracks.find((t) => t.id === clip.trackId);
  if (!track) return { ok: false, error: `Track '${clip.trackId}' not found.` };
  if (track.isLocked) return { ok: false, error: `Track '${track.name}' is locked.` };

  const minValid = clip.timelineStartMs + TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS;
  const maxValid = clipTimelineEndMs(clip) - TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS;
  const split = Math.round(splitPositionMs);

  if (split < minValid || split > maxValid) {
    return {
      ok: false,
      error: `Move playhead inside [${minValid}ms .. ${maxValid}ms] to split '${clip.label}'.`,
    };
  }

  const leftDuration = split - clip.timelineStartMs;
  const rightDuration = clip.durationMs - leftDuration;
  const sourceSplit = clip.sourceInMs + leftDuration;

  const leftClip: MediaClip = {
    ...clip,
    durationMs: leftDuration,
    sourceOutMs: sourceSplit,
  };

  const rightClip: MediaClip = {
    ...clip,
    id: generateId('clip'),
    timelineStartMs: split,
    durationMs: rightDuration,
    sourceInMs: sourceSplit,
    sourceOutMs: clip.sourceOutMs,
  };

  const updatedClips = project.clips.flatMap((c) =>
    c.id === clipId ? [leftClip, rightClip] : [c]
  );

  return {
    ok: true,
    value: {
      project: {
        ...project,
        clips: updatedClips,
        updatedAtEpochMs: Date.now(),
      },
      leftClip,
      rightClip,
    },
  };
}

export function moveClip(
  project: TimelineProject,
  clipId: string,
  targetTrackId: string,
  newTimelineStartMs: number
): TimelineResult<TimelineProject> {
  const clip = project.clips.find((c) => c.id === clipId);
  if (!clip) return { ok: false, error: `Clip '${clipId}' not found.` };
  const sourceTrack = project.tracks.find((t) => t.id === clip.trackId);
  const targetTrack = project.tracks.find((t) => t.id === targetTrackId);
  if (!sourceTrack || !targetTrack) return { ok: false, error: 'Track not found.' };
  if (sourceTrack.isLocked) return { ok: false, error: `Track '${sourceTrack.name}' is locked.` };
  if (targetTrack.isLocked) return { ok: false, error: `Track '${targetTrack.name}' is locked.` };
  if (!isCompatible(clip.mediaType, targetTrack.type)) {
    return {
      ok: false,
      error: `Cannot place ${clip.mediaType} clip onto ${targetTrack.type} track.`,
    };
  }

  const clampedStart = Math.max(0, Math.round(newTimelineStartMs));
  const requestedEnd = clampedStart + clip.durationMs;
  const conflict = findOverlappingClip(
    clipsForTrack(project, targetTrackId),
    clampedStart,
    requestedEnd,
    clip.id
  );
  if (conflict) {
    return {
      ok: false,
      error: `Overlaps '${conflict.label}' on ${targetTrack.name}.`,
    };
  }

  return {
    ok: true,
    value: {
      ...project,
      clips: project.clips.map((c) =>
        c.id === clipId ? { ...c, trackId: targetTrackId, timelineStartMs: clampedStart } : c
      ),
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function deleteClip(
  project: TimelineProject,
  clipId: string,
  ripple = false
): TimelineResult<TimelineProject> {
  const clip = project.clips.find((c) => c.id === clipId);
  if (!clip) return { ok: false, error: `Clip '${clipId}' not found.` };
  const track = project.tracks.find((t) => t.id === clip.trackId);
  if (track?.isLocked) return { ok: false, error: `Track '${track.name}' is locked.` };

  const clipEnd = clipTimelineEndMs(clip);
  const updatedClips = project.clips
    .filter((c) => c.id !== clipId)
    .map((other) => {
      if (ripple && other.trackId === clip.trackId && other.timelineStartMs >= clipEnd) {
        return {
          ...other,
          timelineStartMs: Math.max(0, other.timelineStartMs - clip.durationMs),
        };
      }
      return other;
    });

  return {
    ok: true,
    value: {
      ...project,
      clips: updatedClips,
      updatedAtEpochMs: Date.now(),
    },
  };
}

export function validateProjectInvariants(project: TimelineProject): {
  valid: boolean;
  checks: { name: string; passed: boolean; detail: string }[];
} {
  const trackIds = new Set(project.tracks.map((t) => t.id));
  const missingTrackClips = project.clips.filter((c) => !trackIds.has(c.trackId));
  const invalidTimingClips = project.clips.filter(
    (c) =>
      c.timelineStartMs < 0 ||
      c.sourceInMs < 0 ||
      c.sourceOutMs > c.sourceDurationMs ||
      c.durationMs !== c.sourceOutMs - c.sourceInMs ||
      c.durationMs < TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS
  );

  const overlaps: string[] = [];
  for (const track of project.tracks) {
    const sorted = clipsForTrack(project, track.id);
    for (let i = 0; i < sorted.length - 1; i++) {
      if (clipTimelineEndMs(sorted[i]) > sorted[i + 1].timelineStartMs) {
        overlaps.push(`${track.name}: ${sorted[i].id} & ${sorted[i + 1].id}`);
      }
    }
  }

  const checks = [
    {
      name: 'Schema Version',
      passed: project.schemaVersion === TIMELINE_CONSTANTS.SCHEMA_VERSION,
      detail: `v${project.schemaVersion} (expected v${TIMELINE_CONSTANTS.SCHEMA_VERSION})`,
    },
    {
      name: 'Track References',
      passed: missingTrackClips.length === 0,
      detail:
        missingTrackClips.length === 0
          ? `All ${project.clips.length} clips reference valid tracks`
          : `${missingTrackClips.length} orphan clip(s)`,
    },
    {
      name: 'Non-Destructive Bounds',
      passed: invalidTimingClips.length === 0,
      detail:
        invalidTimingClips.length === 0
          ? 'durationMs == sourceOutMs - sourceInMs within [0, sourceDurationMs]'
          : `${invalidTimingClips.length} clip(s) violate timing invariants`,
    },
    {
      name: 'Track Non-Overlap',
      passed: overlaps.length === 0,
      detail:
        overlaps.length === 0
          ? `Verified across ${project.tracks.length} tracks`
          : overlaps.join(', '),
    },
  ];

  return {
    valid: checks.every((c) => c.passed),
    checks,
  };
}

export function formatTimecode(ms: number): string {
  const clamped = Math.max(0, Math.round(ms));
  const minutes = Math.floor(clamped / 60000);
  const seconds = Math.floor((clamped % 60000) / 1000);
  const millis = clamped % 1000;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(millis).padStart(3, '0')}`;
}

export function pushHistoryEntry(
  undoStack: HistoryEntry[],
  label: string,
  before: TimelineProject,
  after: TimelineProject,
  coalesceKey: string | null = null,
  maxSize = 100
): HistoryEntry[] {
  const last = undoStack[undoStack.length - 1];
  if (coalesceKey && last && last.coalesceKey === coalesceKey) {
    return [
      ...undoStack.slice(0, -1),
      {
        ...last,
        after,
        recordedAtEpochMs: Date.now(),
      },
    ];
  }
  const nextEntry: HistoryEntry = {
    id: generateId('hist'),
    label,
    coalesceKey,
    before,
    after,
    recordedAtEpochMs: Date.now(),
  };
  const nextStack = [...undoStack, nextEntry];
  return nextStack.length > maxSize ? nextStack.slice(nextStack.length - maxSize) : nextStack;
}
