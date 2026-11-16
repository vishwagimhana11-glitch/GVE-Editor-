import React, { useMemo, useRef, useState } from 'react';
import {
  Film,
  Lock,
  Music,
  Plus,
  Redo2,
  Scissors,
  Trash2,
  Undo2,
  Unlock,
  Volume2,
  VolumeX,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import {
  clipTimelineEndMs,
  clipsForTrack,
  formatTimecode,
  getProjectDurationMs,
} from '../engine/timelineEngine';
import {
  HistoryEntry,
  MediaClip,
  TIMELINE_CONSTANTS,
  TimelineProject,
  TrackType,
} from '../engine/timelineTypes';
import { PreviewMonitor } from './PreviewMonitor';

interface TimelineWorkspaceProps {
  project: TimelineProject;
  selectedClipId: string | null;
  playheadMs: number;
  isPlaying: boolean;
  undoStack: HistoryEntry[];
  redoStack: HistoryEntry[];
  lastError: string | null;
  autosaveLabel: string;
  onSelectClip: (clipId: string | null) => void;
  onSeek: (ms: number) => void;
  onTogglePlay: () => void;
  onSplitAtPlayhead: () => void;
  onTrimHead: (clipId: string, newStartMs: number, coalesce?: boolean) => void;
  onTrimTail: (clipId: string, newEndMs: number, coalesce?: boolean) => void;
  onMoveClip: (clipId: string, targetTrackId: string, newStartMs: number, coalesce?: boolean) => void;
  onDeleteClip: (clipId: string, ripple: boolean) => void;
  onAddTrack: (type: TrackType) => void;
  onRemoveTrack: (trackId: string) => void;
  onToggleTrackMute: (trackId: string) => void;
  onToggleTrackLock: (trackId: string) => void;
  onAddClip: (trackId: string) => void;
  onUndo: () => void;
  onRedo: () => void;
  onClearError: () => void;
}

type DragMode =
  | {
      kind: 'move';
      clipId: string;
      trackId: string;
      startClientX: number;
      initialStartMs: number;
    }
  | {
      kind: 'trim-head';
      clipId: string;
      startClientX: number;
      initialStartMs: number;
    }
  | {
      kind: 'trim-tail';
      clipId: string;
      startClientX: number;
      initialEndMs: number;
    };

export const TimelineWorkspace: React.FC<TimelineWorkspaceProps> = ({
  project,
  selectedClipId,
  playheadMs,
  isPlaying,
  undoStack,
  redoStack,
  lastError,
  autosaveLabel,
  onSelectClip,
  onSeek,
  onTogglePlay,
  onSplitAtPlayhead,
  onTrimHead,
  onTrimTail,
  onMoveClip,
  onDeleteClip,
  onAddTrack,
  onRemoveTrack,
  onToggleTrackMute,
  onToggleTrackLock,
  onAddClip,
  onUndo,
  onRedo,
  onClearError,
}) => {
  const [pxPerSecond, setPxPerSecond] = useState<number>(80);
  const [inspectorTab, setInspectorTab] = useState<'clip' | 'history'>('clip');
  const [dragState, setDragState] = useState<DragMode | null>(null);
  const tracksScrollRef = useRef<HTMLDivElement | null>(null);

  const selectedClip: MediaClip | undefined = useMemo(
    () => project.clips.find((c) => c.id === selectedClipId),
    [project.clips, selectedClipId]
  );

  const totalDurationMs = getProjectDurationMs(project);
  const timelineSpanMs = Math.max(14000, totalDurationMs + 3000);
  const timelineWidthPx = Math.max(720, Math.round((timelineSpanMs / 1000) * pxPerSecond));

  const msToPx = (ms: number) => (ms / 1000) * pxPerSecond;
  const pxToMs = (px: number) => Math.max(0, Math.round((px / pxPerSecond) * 1000));

  const canSplitSelected = useMemo(() => {
    if (!selectedClip) return false;
    return (
      playheadMs >= selectedClip.timelineStartMs + TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS &&
      playheadMs <= clipTimelineEndMs(selectedClip) - TIMELINE_CONSTANTS.MIN_CLIP_DURATION_MS
    );
  }, [selectedClip, playheadMs]);

  const handleRulerPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const updateFromClientX = (clientX: number) => {
      const x = Math.max(0, clientX - rect.left);
      onSeek(Math.min(totalDurationMs, pxToMs(x)));
    };
    updateFromClientX(e.clientX);

    const onMove = (ev: PointerEvent) => updateFromClientX(ev.clientX);
    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const startClipDrag = (e: React.PointerEvent, mode: DragMode) => {
    e.stopPropagation();
    onSelectClip(mode.clipId);
    setDragState(mode);

    const onMove = (ev: PointerEvent) => {
      const deltaPx = ev.clientX - mode.startClientX;
      const deltaMs = Math.round((deltaPx / pxPerSecond) * 1000);
      if (mode.kind === 'move') {
        onMoveClip(mode.clipId, mode.trackId, Math.max(0, mode.initialStartMs + deltaMs), true);
      } else if (mode.kind === 'trim-head') {
        onTrimHead(mode.clipId, mode.initialStartMs + deltaMs, true);
      } else if (mode.kind === 'trim-tail') {
        onTrimTail(mode.clipId, mode.initialEndMs + deltaMs, true);
      }
    };

    const onUp = () => {
      setDragState(null);
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
    };

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  };

  const rulerTicks = useMemo(() => {
    const stepMs = pxPerSecond >= 100 ? 1000 : 2000;
    const ticks: number[] = [];
    for (let t = 0; t <= timelineSpanMs; t += stepMs) {
      ticks.push(t);
    }
    return ticks;
  }, [timelineSpanMs, pxPerSecond]);

  const sortedTracks = useMemo(
    () => [...project.tracks].sort((a, b) => a.orderIndex - b.orderIndex),
    [project.tracks]
  );

  return (
    <div className="grid grid-cols-1 xl:grid-cols-[1fr_340px] gap-4 items-start">
      <div className="flex flex-col gap-4 min-w-0">
        <PreviewMonitor
          project={project}
          playheadMs={playheadMs}
          isPlaying={isPlaying}
          onTogglePlay={onTogglePlay}
          onSeek={onSeek}
        />

        {/* Editing Toolbar */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5 bg-[#13161D] border border-[#242936] rounded-xl">
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={onUndo}
              disabled={undoStack.length === 0}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-200 hover:bg-[#222836] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer whitespace-nowrap shrink-0"
              title={undoStack.length > 0 ? `Undo: ${undoStack[undoStack.length - 1].label}` : 'Nothing to undo'}
            >
              <Undo2 className="w-3.5 h-3.5" />
              <span>Undo</span>
            </button>
            <button
              type="button"
              onClick={onRedo}
              disabled={redoStack.length === 0}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-200 hover:bg-[#222836] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer whitespace-nowrap shrink-0"
              title={redoStack.length > 0 ? `Redo: ${redoStack[redoStack.length - 1].label}` : 'Nothing to redo'}
            >
              <Redo2 className="w-3.5 h-3.5" />
              <span>Redo</span>
            </button>

            <div className="h-4 w-px bg-[#242936] mx-1" aria-hidden="true" />

            <button
              type="button"
              onClick={onSplitAtPlayhead}
              disabled={!selectedClip}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer whitespace-nowrap shrink-0 ${
                canSplitSelected
                  ? 'bg-teal-500/15 border border-teal-500/50 text-teal-300 hover:bg-teal-500/25'
                  : 'border border-[#2A3040] bg-[#1A1E28] text-slate-300 hover:bg-[#222836]'
              } disabled:opacity-40 disabled:cursor-not-allowed`}
            >
              <Scissors className="w-3.5 h-3.5" />
              <span>Split at Playhead</span>
            </button>

            <button
              type="button"
              onClick={() => selectedClip && onDeleteClip(selectedClip.id, false)}
              disabled={!selectedClip}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-300 hover:text-red-300 hover:border-red-500/40 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer whitespace-nowrap shrink-0"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Delete</span>
            </button>

            <button
              type="button"
              onClick={() => selectedClip && onDeleteClip(selectedClip.id, true)}
              disabled={!selectedClip}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-300 hover:text-amber-300 hover:border-amber-500/40 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer whitespace-nowrap shrink-0"
            >
              <span>Ripple Delete</span>
            </button>
          </div>

          <div className="flex items-center gap-3">
            <span className="text-xs text-slate-400 font-mono tabular-nums whitespace-nowrap">
              {autosaveLabel}
            </span>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => setPxPerSecond((z) => Math.max(40, z - 20))}
                aria-label="Zoom out timeline"
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#1E2330] transition-colors cursor-pointer"
              >
                <ZoomOut className="w-4 h-4" />
              </button>
              <span className="text-xs font-mono text-slate-400 tabular-nums w-12 text-center">
                {pxPerSecond}px/s
              </span>
              <button
                type="button"
                onClick={() => setPxPerSecond((z) => Math.min(180, z + 20))}
                aria-label="Zoom in timeline"
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-[#1E2330] transition-colors cursor-pointer"
              >
                <ZoomIn className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {lastError && (
          <div className="flex items-center justify-between gap-3 px-4 py-2.5 rounded-xl bg-red-950/60 border border-red-500/40 text-xs text-red-200">
            <span>{lastError}</span>
            <button
              type="button"
              onClick={onClearError}
              className="text-red-300 hover:text-white font-medium cursor-pointer whitespace-nowrap"
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Multi-Track Timeline */}
        <div className="bg-[#13161D] border border-[#242936] rounded-xl overflow-hidden">
          <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#242936]">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <span className="font-medium text-slate-200">Multi-Track Timeline</span>
              <span aria-hidden="true" className="text-slate-600">·</span>
              <span>{project.tracks.length} tracks</span>
              <span aria-hidden="true" className="text-slate-600">·</span>
              <span>{project.clips.length} clips</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onAddTrack('VIDEO')}
                className="flex items-center gap-1 px-2.5 py-1 rounded-md border border-[#2A3040] bg-[#1A1E28] text-xs text-slate-300 hover:text-white hover:border-teal-500/50 transition-colors cursor-pointer whitespace-nowrap shrink-0"
              >
                <Plus className="w-3.5 h-3.5 text-teal-400" />
                <span>Video Track</span>
              </button>
              <button
                type="button"
                onClick={() => onAddTrack('AUDIO')}
                className="flex items-center gap-1 px-2.5 py-1 rounded-md border border-[#2A3040] bg-[#1A1E28] text-xs text-slate-300 hover:text-white hover:border-amber-500/50 transition-colors cursor-pointer whitespace-nowrap shrink-0"
              >
                <Plus className="w-3.5 h-3.5 text-amber-400" />
                <span>Audio Track</span>
              </button>
            </div>
          </div>

          <div className="flex">
            {/* Fixed Track Headers */}
            <div className="w-44 shrink-0 border-r border-[#242936] bg-[#101319]">
              <div className="h-8 px-3 flex items-center border-b border-[#242936] text-[11px] font-mono text-slate-500">
                TRACKS
              </div>
              {sortedTracks.map((track) => (
                <div
                  key={track.id}
                  className="h-16 px-3 flex flex-col justify-center gap-1 border-b border-[#1E2330] last:border-b-0"
                >
                  <div className="flex items-center justify-between gap-1">
                    <div className="flex items-center gap-1.5 min-w-0">
                      {track.type === 'VIDEO' ? (
                        <Film className="w-3.5 h-3.5 text-teal-400 shrink-0" />
                      ) : (
                        <Music className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                      )}
                      <span className="text-xs font-medium text-slate-200 truncate">
                        {track.name}
                      </span>
                    </div>
                    <button
                      type="button"
                      onClick={() => onAddClip(track.id)}
                      title={`Add sample clip to ${track.name}`}
                      className="p-1 rounded text-slate-400 hover:text-white hover:bg-[#1E2330] transition-colors cursor-pointer"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => onToggleTrackMute(track.id)}
                      title={track.isMuted ? 'Unmute track' : 'Mute track'}
                      className={`p-1 rounded text-xs transition-colors cursor-pointer ${
                        track.isMuted ? 'text-red-400 bg-red-500/10' : 'text-slate-500 hover:text-slate-300'
                      }`}
                    >
                      {track.isMuted ? <VolumeX className="w-3 h-3" /> : <Volume2 className="w-3 h-3" />}
                    </button>
                    <button
                      type="button"
                      onClick={() => onToggleTrackLock(track.id)}
                      title={track.isLocked ? 'Unlock track' : 'Lock track'}
                      className={`p-1 rounded text-xs transition-colors cursor-pointer ${
                        track.isLocked ? 'text-amber-400 bg-amber-500/10' : 'text-slate-500 hover:text-slate-300'
                      }`}
                    >
                      {track.isLocked ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
                    </button>
                    {project.tracks.filter((t) => t.type === track.type).length > 1 && (
                      <button
                        type="button"
                        onClick={() => onRemoveTrack(track.id)}
                        title="Remove track"
                        className="p-1 rounded text-slate-500 hover:text-red-400 transition-colors cursor-pointer ml-auto"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {/* Scrollable Timeline Canvas */}
            <div ref={tracksScrollRef} className="flex-1 overflow-x-auto relative select-none">
              <div style={{ width: `${timelineWidthPx}px` }} className="relative">
                {/* Ruler */}
                <div
                  onPointerDown={handleRulerPointerDown}
                  className="h-8 border-b border-[#242936] bg-[#0E1117] relative cursor-ew-resize"
                >
                  {rulerTicks.map((t) => (
                    <div
                      key={t}
                      style={{ left: `${msToPx(t)}px` }}
                      className="absolute top-0 bottom-0 flex flex-col justify-end"
                    >
                      <span className="text-[10px] font-mono text-slate-500 pl-1 pb-1 tabular-nums">
                        {(t / 1000).toFixed(0)}s
                      </span>
                      <div className="w-px h-2 bg-[#2D3548]" />
                    </div>
                  ))}
                </div>

                {/* Track Lanes */}
                {sortedTracks.map((track) => {
                  const trackClips = clipsForTrack(project, track.id);
                  return (
                    <div
                      key={track.id}
                      onClick={() => onSelectClip(null)}
                      className={`h-16 border-b border-[#1E2330] last:border-b-0 relative ${
                        track.isLocked ? 'bg-[#0C0E13] opacity-75' : 'bg-[#0B0D11]'
                      }`}
                    >
                      {trackClips.map((clip) => {
                        const leftPx = msToPx(clip.timelineStartMs);
                        const widthPx = Math.max(24, msToPx(clip.durationMs));
                        const isSelected = clip.id === selectedClipId;
                        const isVideo = clip.mediaType === 'VIDEO';
                        const isDraggingThis = dragState?.clipId === clip.id;

                        return (
                          <div
                            key={clip.id}
                            style={{ left: `${leftPx}px`, width: `${widthPx}px` }}
                            onPointerDown={(e) => {
                              if (track.isLocked) {
                                onSelectClip(clip.id);
                                return;
                              }
                              startClipDrag(e, {
                                kind: 'move',
                                clipId: clip.id,
                                trackId: track.id,
                                startClientX: e.clientX,
                                initialStartMs: clip.timelineStartMs,
                              });
                            }}
                            onClick={(e) => {
                              e.stopPropagation();
                              onSelectClip(clip.id);
                            }}
                            className={`absolute top-2 bottom-2 rounded-md border overflow-hidden flex items-center transition-colors ${
                              track.isLocked ? 'cursor-not-allowed' : 'cursor-grab active:cursor-grabbing'
                            } ${
                              isVideo
                                ? isSelected
                                  ? 'bg-[#113D44] border-teal-400'
                                  : 'bg-[#0D2A32] border-[#1A4B57] hover:border-teal-500/70'
                                : isSelected
                                  ? 'bg-[#3D280A] border-amber-400'
                                  : 'bg-[#291B07] border-[#4A3210] hover:border-amber-500/70'
                            } ${isDraggingThis ? 'opacity-90' : ''}`}
                          >
                            {/* Left Trim Handle */}
                            {!track.isLocked && (
                              <div
                                onPointerDown={(e) =>
                                  startClipDrag(e, {
                                    kind: 'trim-head',
                                    clipId: clip.id,
                                    startClientX: e.clientX,
                                    initialStartMs: clip.timelineStartMs,
                                  })
                                }
                                title="Drag to trim clip head"
                                className="w-2 h-full shrink-0 bg-white/10 hover:bg-teal-400/70 cursor-ew-resize transition-colors"
                              />
                            )}

                            <div className="flex-1 min-w-0 px-2 py-1 flex flex-col justify-center">
                              <span className="text-[11px] font-medium text-slate-100 truncate">
                                {clip.label}
                              </span>
                              <span className="text-[10px] font-mono text-slate-300/80 truncate tabular-nums">
                                {(clip.durationMs / 1000).toFixed(2)}s · in {(clip.sourceInMs / 1000).toFixed(1)}s
                              </span>
                            </div>

                            {/* Right Trim Handle */}
                            {!track.isLocked && (
                              <div
                                onPointerDown={(e) =>
                                  startClipDrag(e, {
                                    kind: 'trim-tail',
                                    clipId: clip.id,
                                    startClientX: e.clientX,
                                    initialEndMs: clipTimelineEndMs(clip),
                                  })
                                }
                                title="Drag to trim clip tail"
                                className="w-2 h-full shrink-0 bg-white/10 hover:bg-teal-400/70 cursor-ew-resize transition-colors"
                              />
                            )}
                          </div>
                        );
                      })}
                    </div>
                  );
                })}

                {/* Playhead Line */}
                <div
                  style={{ left: `${msToPx(playheadMs)}px` }}
                  className="absolute top-0 bottom-0 w-px bg-teal-400 pointer-events-none z-20"
                >
                  <div className="w-2.5 h-3 -ml-[5px] bg-teal-400 rounded-b-sm" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Right Column: Clip Data Model & Edit History Inspector */}
      <div className="bg-[#13161D] border border-[#242936] rounded-xl overflow-hidden">
        <div className="flex border-b border-[#242936] p-1 bg-[#101319] gap-1">
          <button
            type="button"
            onClick={() => setInspectorTab('clip')}
            className={`flex-1 py-1.5 text-xs font-medium rounded-lg transition-colors cursor-pointer ${
              inspectorTab === 'clip'
                ? 'bg-[#1E2330] text-white'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Clip Model
          </button>
          <button
            type="button"
            onClick={() => setInspectorTab('history')}
            className={`flex-1 py-1.5 text-xs font-medium rounded-lg transition-colors cursor-pointer ${
              inspectorTab === 'history'
                ? 'bg-[#1E2330] text-white'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            History ({undoStack.length}/{redoStack.length})
          </button>
        </div>

        {inspectorTab === 'clip' ? (
          <div className="p-4 space-y-4">
            {!selectedClip ? (
              <div className="py-8 text-center space-y-2">
                <p className="text-sm font-medium text-slate-300">No clip selected</p>
                <p className="text-xs text-slate-500">
                  Click any video or audio segment on the timeline to inspect its non-destructive coordinates or trim bounds.
                </p>
              </div>
            ) : (
              <>
                <div>
                  <h3 className="text-sm font-semibold text-white truncate">{selectedClip.label}</h3>
                  <p className="text-xs text-slate-400 mt-0.5 font-mono">
                    {selectedClip.mediaType} · Track {project.tracks.find((t) => t.id === selectedClip.trackId)?.name ?? selectedClip.trackId}
                  </p>
                </div>

                {/* Non-Destructive Source Window Visualization */}
                <div className="space-y-1.5 pt-2 border-t border-[#242936]">
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span>Source Window</span>
                    <span className="font-mono tabular-nums text-slate-300">
                      {selectedClip.sourceInMs}..{selectedClip.sourceOutMs} / {selectedClip.sourceDurationMs}ms
                    </span>
                  </div>
                  <div className="h-2.5 w-full rounded-full bg-[#0B0D11] border border-[#242936] relative overflow-hidden">
                    <div
                      style={{
                        left: `${(selectedClip.sourceInMs / selectedClip.sourceDurationMs) * 100}%`,
                        width: `${(selectedClip.durationMs / selectedClip.sourceDurationMs) * 100}%`,
                      }}
                      className="absolute top-0 bottom-0 bg-teal-400/80 rounded-full"
                    />
                  </div>
                </div>

                {/* Precision Trim Controls */}
                <div className="space-y-2 pt-2 border-t border-[#242936]">
                  <span className="text-xs font-medium text-slate-300 block">Precision Trim</span>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => onTrimHead(selectedClip.id, selectedClip.timelineStartMs - 250)}
                      className="px-2.5 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-mono text-slate-200 hover:bg-[#222836] cursor-pointer"
                    >
                      Head -250ms
                    </button>
                    <button
                      type="button"
                      onClick={() => onTrimHead(selectedClip.id, selectedClip.timelineStartMs + 250)}
                      className="px-2.5 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-mono text-slate-200 hover:bg-[#222836] cursor-pointer"
                    >
                      Head +250ms
                    </button>
                    <button
                      type="button"
                      onClick={() => onTrimTail(selectedClip.id, clipTimelineEndMs(selectedClip) - 250)}
                      className="px-2.5 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-mono text-slate-200 hover:bg-[#222836] cursor-pointer"
                    >
                      Tail -250ms
                    </button>
                    <button
                      type="button"
                      onClick={() => onTrimTail(selectedClip.id, clipTimelineEndMs(selectedClip) + 250)}
                      className="px-2.5 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-mono text-slate-200 hover:bg-[#222836] cursor-pointer"
                    >
                      Tail +250ms
                    </button>
                  </div>
                </div>

                {/* Exact MediaClip Kotlin Fields */}
                <div className="pt-2 border-t border-[#242936]">
                  <span className="text-xs font-medium text-slate-300 block mb-2">
                    MediaClip Data Fields
                  </span>
                  <dl className="divide-y divide-[#1E2330] text-xs">
                    {[
                      ['id', selectedClip.id],
                      ['trackId', selectedClip.trackId],
                      ['timelineStartMs', `${selectedClip.timelineStartMs} ms`],
                      ['durationMs', `${selectedClip.durationMs} ms`],
                      ['sourceInMs', `${selectedClip.sourceInMs} ms`],
                      ['sourceOutMs', `${selectedClip.sourceOutMs} ms`],
                      ['sourceDurationMs', `${selectedClip.sourceDurationMs} ms`],
                      ['sourceUri', selectedClip.sourceUri],
                    ].map(([field, val]) => (
                      <div key={field} className="py-1.5 flex items-center justify-between gap-3">
                        <dt className="font-mono text-slate-400 shrink-0">{field}</dt>
                        <dd className="font-mono text-slate-200 tabular-nums truncate text-right" title={val}>
                          {val}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </div>
              </>
            )}
          </div>
        ) : (
          <div className="p-4 space-y-4">
            <div className="space-y-1">
              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>Undo Stack</span>
                <span className="font-mono tabular-nums">{undoStack.length} entries</span>
              </div>
              {undoStack.length === 0 ? (
                <p className="text-xs text-slate-500 py-4 text-center">
                  Perform a trim, split, move, or add action to record reversible commands.
                </p>
              ) : (
                <ul className="divide-y divide-[#1E2330] max-h-64 overflow-y-auto">
                  {[...undoStack].reverse().map((entry, idx) => (
                    <li key={entry.id} className="py-2 flex items-center justify-between gap-2 text-xs">
                      <span className={idx === 0 ? 'text-teal-300 font-medium' : 'text-slate-300'}>
                        {entry.label}
                      </span>
                      <span className="font-mono text-slate-500 tabular-nums">
                        {new Date(entry.recordedAtEpochMs).toLocaleTimeString([], {
                          hour12: false,
                          minute: '2-digit',
                          second: '2-digit',
                        })}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {redoStack.length > 0 && (
              <div className="pt-3 border-t border-[#242936] space-y-1">
                <div className="flex items-center justify-between text-xs text-slate-400">
                  <span>Redo Stack</span>
                  <span className="font-mono tabular-nums">{redoStack.length} entries</span>
                </div>
                <ul className="divide-y divide-[#1E2330] max-h-40 overflow-y-auto">
                  {[...redoStack].reverse().map((entry) => (
                    <li key={entry.id} className="py-2 flex items-center justify-between gap-2 text-xs text-slate-400">
                      <span>{entry.label}</span>
                      <span className="font-mono text-slate-600">ready</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
