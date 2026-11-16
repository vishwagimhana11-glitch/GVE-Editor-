import React, { useEffect, useRef } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Pause,
  Play,
  SkipBack,
  Volume2,
  VolumeX,
} from 'lucide-react';
import {
  clipTimelineEndMs,
  formatTimecode,
  getProjectDurationMs,
} from '../engine/timelineEngine';
import { MediaClip, TimelineProject } from '../engine/timelineTypes';

interface PreviewMonitorProps {
  project: TimelineProject;
  playheadMs: number;
  isPlaying: boolean;
  onTogglePlay: () => void;
  onSeek: (ms: number) => void;
}

function getSourcePositionAtPlayhead(clip: MediaClip, playheadMs: number): number {
  return clip.sourceInMs + (playheadMs - clip.timelineStartMs);
}

function colorForClipId(id: string): { primary: string; secondary: string } {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  }
  const palettes = [
    { primary: '#0F766E', secondary: '#134E4A' },
    { primary: '#0369A1', secondary: '#0C4A6E' },
    { primary: '#1D4ED8', secondary: '#1E3A8A' },
    { primary: '#0E7490', secondary: '#164E63' },
  ];
  return palettes[hash % palettes.length];
}

export const PreviewMonitor: React.FC<PreviewMonitorProps> = ({
  project,
  playheadMs,
  isPlaying,
  onTogglePlay,
  onSeek,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const totalDurationMs = getProjectDurationMs(project);
  const frameStepMs = Math.round(1000 / project.settings.fps);

  const activeVideoClips = project.tracks
    .filter((t) => t.type === 'VIDEO' && t.isVisible)
    .sort((a, b) => b.orderIndex - a.orderIndex)
    .map((track) => {
      const clip = project.clips.find(
        (c) =>
          c.trackId === track.id &&
          playheadMs >= c.timelineStartMs &&
          playheadMs < clipTimelineEndMs(c)
      );
      return clip ? { track, clip } : null;
    })
    .filter((item): item is { track: (typeof project.tracks)[number]; clip: MediaClip } => item !== null);

  const activeAudioClips = project.tracks
    .filter((t) => t.type === 'AUDIO' && !t.isMuted)
    .map((track) => {
      const clip = project.clips.find(
        (c) =>
          c.trackId === track.id &&
          !c.isMuted &&
          playheadMs >= c.timelineStartMs &&
          playheadMs < clipTimelineEndMs(c)
      );
      return clip ? { track, clip } : null;
    })
    .filter((item): item is { track: (typeof project.tracks)[number]; clip: MediaClip } => item !== null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    // Studio background
    ctx.fillStyle = '#07090C';
    ctx.fillRect(0, 0, w, h);

    // Subtle safe-area guide grid
    ctx.strokeStyle = 'rgba(148, 163, 184, 0.08)';
    ctx.lineWidth = 1;
    ctx.strokeRect(24, 18, w - 48, h - 36);

    if (activeVideoClips.length === 0) {
      ctx.fillStyle = '#475569';
      ctx.font = '500 14px "DM Sans", sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('No video segment at current playhead', w / 2, h / 2 - 6);
      ctx.fillStyle = '#334155';
      ctx.font = '400 12px "JetBrains Mono", monospace';
      ctx.fillText(`Playhead ${formatTimecode(playheadMs)}`, w / 2, h / 2 + 16);
      return;
    }

    const base = activeVideoClips[0];
    const basePalette = colorForClipId(base.clip.sourceUri);
    const baseSourcePos = getSourcePositionAtPlayhead(base.clip, playheadMs);
    const phase = (baseSourcePos / 1000) * 1.4;

    // Procedural cinematic frame gradient tied strictly to sourcePositionMs
    const grad = ctx.createLinearGradient(0, 0, w, h);
    grad.addColorStop(0, basePalette.secondary);
    grad.addColorStop(1, '#090D14');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);

    // Motion horizon arcs keyed to source timecode so trims & splits visibly match source frames
    ctx.save();
    for (let i = 0; i < 5; i++) {
      const yOffset = h * 0.42 + Math.sin(phase + i * 0.85) * 28 + i * 18;
      ctx.beginPath();
      ctx.moveTo(0, yOffset);
      ctx.bezierCurveTo(
        w * 0.33,
        yOffset - Math.cos(phase + i) * 32,
        w * 0.66,
        yOffset + Math.sin(phase * 0.8 + i) * 32,
        w,
        yOffset
      );
      ctx.strokeStyle = `rgba(20, 184, 166, ${0.18 + i * 0.06})`;
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }
    ctx.restore();

    // Active base segment telemetry inside canvas frame
    ctx.fillStyle = 'rgba(11, 13, 17, 0.72)';
    ctx.fillRect(16, 16, 260, 52);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.strokeRect(16, 16, 260, 52);

    ctx.fillStyle = '#F1F5F9';
    ctx.font = '600 12px "Plus Jakarta Sans", sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(`${base.track.name} · ${base.clip.label}`, 26, 36);

    ctx.fillStyle = '#5EEAD4';
    ctx.font = '500 11px "JetBrains Mono", monospace';
    ctx.fillText(`SRC ${formatTimecode(baseSourcePos)} / ${formatTimecode(base.clip.sourceDurationMs)}`, 26, 56);

    // Overlay track PIP frame if present
    if (activeVideoClips.length > 1) {
      const overlay = activeVideoClips[1];
      const ovSourcePos = getSourcePositionAtPlayhead(overlay.clip, playheadMs);
      const pipW = 180;
      const pipH = 102;
      const pipX = w - pipW - 20;
      const pipY = 20;

      ctx.fillStyle = '#0F172A';
      ctx.fillRect(pipX, pipY, pipW, pipH);
      ctx.strokeStyle = '#38BDF8';
      ctx.lineWidth = 1.5;
      ctx.strokeRect(pipX, pipY, pipW, pipH);

      ctx.fillStyle = 'rgba(56, 189, 248, 0.16)';
      ctx.fillRect(pipX + 1, pipY + 1, pipW - 2, pipH - 2);

      ctx.fillStyle = '#E2E8F0';
      ctx.font = '600 11px "Plus Jakarta Sans", sans-serif';
      ctx.fillText(`${overlay.track.name} · ${overlay.clip.label}`, pipX + 10, pipY + 24);

      ctx.fillStyle = '#7DD3FC';
      ctx.font = '400 10px "JetBrains Mono", monospace';
      ctx.fillText(`SRC ${formatTimecode(ovSourcePos)}`, pipX + 10, pipY + 42);
    }
  }, [activeVideoClips, playheadMs]);

  return (
    <div className="flex flex-col bg-[#13161D] border border-[#242936] rounded-xl overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-[#242936] text-xs text-slate-400">
        <div className="flex items-center gap-2 font-medium text-slate-200">
          <span>Segment Preview</span>
          <span aria-hidden="true" className="text-slate-600">·</span>
          <span className="font-mono text-slate-400 tabular-nums">
            {project.settings.width}×{project.settings.height} · {project.settings.fps} fps
          </span>
        </div>
        <div className="flex items-center gap-3 font-mono tabular-nums text-slate-400">
          <span className="flex items-center gap-1.5">
            {activeAudioClips.length > 0 ? (
              <Volume2 className="w-3.5 h-3.5 text-amber-400" />
            ) : (
              <VolumeX className="w-3.5 h-3.5 text-slate-500" />
            )}
            <span>{activeAudioClips.length} audio active</span>
          </span>
        </div>
      </div>

      <div className="relative bg-[#07090C] flex items-center justify-center px-4 py-3">
        <canvas
          ref={canvasRef}
          width={640}
          height={280}
          className="w-full max-w-[640px] aspect-[16/7] rounded-lg border border-[#1E2330]"
        />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4 px-4 py-2.5 border-t border-[#242936] bg-[#101319]">
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => onSeek(0)}
            title="Jump to start"
            className="p-2 rounded-lg text-slate-300 hover:text-white hover:bg-[#1E2330] transition-colors cursor-pointer"
          >
            <SkipBack className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={() => onSeek(Math.max(0, playheadMs - frameStepMs))}
            title="Previous frame"
            className="p-2 rounded-lg text-slate-300 hover:text-white hover:bg-[#1E2330] transition-colors cursor-pointer"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={onTogglePlay}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-teal-500 hover:bg-teal-400 text-slate-950 font-semibold text-xs transition-colors cursor-pointer whitespace-nowrap shrink-0"
          >
            {isPlaying ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            <span>{isPlaying ? 'Pause' : 'Play'}</span>
          </button>
          <button
            type="button"
            onClick={() => onSeek(Math.min(totalDurationMs, playheadMs + frameStepMs))}
            title="Next frame"
            className="p-2 rounded-lg text-slate-300 hover:text-white hover:bg-[#1E2330] transition-colors cursor-pointer"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>

        <div className="flex items-center gap-2 font-mono text-xs tabular-nums">
          <span className="text-teal-400 font-medium">{formatTimecode(playheadMs)}</span>
          <span className="text-slate-600">/</span>
          <span className="text-slate-400">{formatTimecode(totalDurationMs)}</span>
        </div>
      </div>
    </div>
  );
};
