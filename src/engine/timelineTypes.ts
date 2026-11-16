export const TIMELINE_CONSTANTS = {
  SCHEMA_VERSION: 1,
  MIN_CLIP_DURATION_MS: 100,
  DEFAULT_FPS: 30,
  DEFAULT_WIDTH: 1920,
  DEFAULT_HEIGHT: 1080,
} as const;

export type TrackType = 'VIDEO' | 'AUDIO';
export type MediaType = 'VIDEO' | 'AUDIO';

export interface ProjectSettings {
  width: number;
  height: number;
  fps: number;
}

export interface MediaAsset {
  id: string;
  uri: string;
  displayName: string;
  mediaType: MediaType;
  totalDurationMs: number;
  width?: number;
  height?: number;
  hasAudioStream: boolean;
}

export interface MediaClip {
  id: string;
  sourceUri: string;
  trackId: string;
  timelineStartMs: number;
  durationMs: number;
  sourceInMs: number;
  sourceOutMs: number;
  sourceDurationMs: number;
  mediaType: MediaType;
  label: string;
  volume: number;
  isMuted: boolean;
}

export interface TimelineTrack {
  id: string;
  name: string;
  type: TrackType;
  orderIndex: number;
  isMuted: boolean;
  isLocked: boolean;
  isVisible: boolean;
}

export interface TimelineProject {
  schemaVersion: number;
  projectId: string;
  name: string;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  settings: ProjectSettings;
  tracks: TimelineTrack[];
  clips: MediaClip[];
  mediaAssets: MediaAsset[];
}

export type TimelineResult<T> =
  | { ok: true; value: T }
  | { ok: false; error: string };

export interface HistoryEntry {
  id: string;
  label: string;
  coalesceKey: string | null;
  before: TimelineProject;
  after: TimelineProject;
  recordedAtEpochMs: number;
}
