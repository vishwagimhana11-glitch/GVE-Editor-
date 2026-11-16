import { useEffect, useState } from 'react';
import { BuildGuideView } from './components/BuildGuideView';
import { JsonSchemaView } from './components/JsonSchemaView';
import { KotlinSourceExplorer } from './components/KotlinSourceExplorer';
import { TimelineWorkspace } from './components/TimelineWorkspace';
import { ANDROID_SOURCE_FILES } from './engine/androidFiles';
import {
  addClipToTrack,
  addTrack,
  createSampleProject,
  deleteClip,
  getProjectDurationMs,
  moveClip,
  pushHistoryEntry,
  removeTrack,
  splitClipAtPlayhead,
  toggleTrackLock,
  toggleTrackMute,
  trimClipHead,
  trimClipTail,
  validateProjectInvariants,
} from './engine/timelineEngine';
import { HistoryEntry, TimelineProject, TrackType } from './engine/timelineTypes';
import { buildZipBlob } from './utils/zipBuilder';

type ActiveSection = 'engine' | 'source' | 'json' | 'guide';

const AUTOSAVE_STORAGE_KEY = 'gve.phase2.autosave.v1';

function loadInitialProject(): TimelineProject {
  try {
    const raw = localStorage.getItem(AUTOSAVE_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as TimelineProject;
      if (parsed && Array.isArray(parsed.tracks) && Array.isArray(parsed.clips)) {
        const validation = validateProjectInvariants(parsed);
        if (validation.valid) return parsed;
      }
    }
  } catch {
    // Fallback to clean sample project
  }
  return createSampleProject();
}

export default function App() {
  const [section, setSection] = useState<ActiveSection>('engine');
  const [project, setProject] = useState<TimelineProject>(() => loadInitialProject());
  const [selectedClipId, setSelectedClipId] = useState<string | null>('clip-v1-a');
  const [playheadMs, setPlayheadMs] = useState<number>(2600);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [undoStack, setUndoStack] = useState<HistoryEntry[]>([]);
  const [redoStack, setRedoStack] = useState<HistoryEntry[]>([]);
  const [lastError, setLastError] = useState<string | null>(null);
  const [autosaveLabel, setAutosaveLabel] = useState<string>('Autosaved');

  // Debounced autosave to localStorage mirroring AutosaveCoordinator.kt
  useEffect(() => {
    setAutosaveLabel('Saving...');
    const timer = window.setTimeout(() => {
      try {
        localStorage.setItem(AUTOSAVE_STORAGE_KEY, JSON.stringify(project));
        const timeStr = new Date().toLocaleTimeString([], {
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        });
        setAutosaveLabel(`Autosaved ${timeStr}`);
      } catch {
        setAutosaveLabel('Autosave unavailable');
      }
    }, 500);
    return () => window.clearTimeout(timer);
  }, [project]);

  // Playback loop
  useEffect(() => {
    if (!isPlaying) return;
    const total = getProjectDurationMs(project);
    if (total <= 0) {
      setIsPlaying(false);
      return;
    }

    let lastTs = performance.now();
    let rafId = 0;

    const tick = (now: number) => {
      const delta = now - lastTs;
      lastTs = now;
      setPlayheadMs((prev) => {
        const next = prev + delta;
        if (next >= total) {
          setIsPlaying(false);
          return total;
        }
        return Math.round(next);
      });
      rafId = requestAnimationFrame(tick);
    };

    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [isPlaying, project]);

  const commitProjectChange = (
    label: string,
    nextProject: TimelineProject,
    coalesceKey: string | null = null
  ) => {
    setUndoStack((prev) => pushHistoryEntry(prev, label, project, nextProject, coalesceKey));
    setRedoStack([]);
    setProject(nextProject);
    setLastError(null);
  };

  const handleSplitAtPlayhead = () => {
    if (!selectedClipId) return;
    const res = splitClipAtPlayhead(project, selectedClipId, playheadMs);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange('Split clip', res.value.project);
    setSelectedClipId(res.value.rightClip.id);
  };

  const handleTrimHead = (clipId: string, newStartMs: number, coalesce = false) => {
    const res = trimClipHead(project, clipId, newStartMs);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange('Trim clip head', res.value, coalesce ? `trim-head-${clipId}` : null);
  };

  const handleTrimTail = (clipId: string, newEndMs: number, coalesce = false) => {
    const res = trimClipTail(project, clipId, newEndMs);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange('Trim clip tail', res.value, coalesce ? `trim-tail-${clipId}` : null);
  };

  const handleMoveClip = (
    clipId: string,
    targetTrackId: string,
    newStartMs: number,
    coalesce = false
  ) => {
    const res = moveClip(project, clipId, targetTrackId, newStartMs);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange('Move clip', res.value, coalesce ? `move-${clipId}` : null);
  };

  const handleDeleteClip = (clipId: string, ripple: boolean) => {
    const res = deleteClip(project, clipId, ripple);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange(ripple ? 'Ripple delete clip' : 'Delete clip', res.value);
    if (selectedClipId === clipId) setSelectedClipId(null);
  };

  const handleAddTrack = (type: TrackType) => {
    const res = addTrack(project, type);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange(`Add ${type.toLowerCase()} track`, res.value);
  };

  const handleRemoveTrack = (trackId: string) => {
    const res = removeTrack(project, trackId);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange('Remove track', res.value);
  };

  const handleToggleTrackMute = (trackId: string) => {
    const res = toggleTrackMute(project, trackId);
    if (res.ok) commitProjectChange('Toggle track mute', res.value);
  };

  const handleToggleTrackLock = (trackId: string) => {
    const res = toggleTrackLock(project, trackId);
    if (res.ok) commitProjectChange('Toggle track lock', res.value);
  };

  const handleAddClip = (trackId: string) => {
    const res = addClipToTrack(project, trackId);
    if (!res.ok) {
      setLastError(res.error);
      return;
    }
    commitProjectChange(`Add ${res.value.clip.label}`, res.value.project);
    setSelectedClipId(res.value.clip.id);
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const entry = undoStack[undoStack.length - 1];
    setUndoStack((prev) => prev.slice(0, -1));
    setRedoStack((prev) => [...prev, entry]);
    setProject(entry.before);
    setLastError(null);
  };

  const handleRedo = () => {
    if (redoStack.length === 0) return;
    const entry = redoStack[redoStack.length - 1];
    setRedoStack((prev) => prev.slice(0, -1));
    setUndoStack((prev) => [...prev, entry]);
    setProject(entry.after);
    setLastError(null);
  };

  const handleDownloadAndroidZip = () => {
    const entries = ANDROID_SOURCE_FILES.map((file) => ({
      path: `gve-editor-android/${file.path}`,
      content: file.content,
    }));
    const blob = buildZipBlob(entries);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'gve-editor-android.zip';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImportProject = (imported: TimelineProject) => {
    setUndoStack([]);
    setRedoStack([]);
    setProject(imported);
    setSelectedClipId(imported.clips[0]?.id ?? null);
    setPlayheadMs(0);
    setLastError(null);
  };

  const handleResetSample = () => {
    const fresh = createSampleProject();
    setUndoStack([]);
    setRedoStack([]);
    setProject(fresh);
    setSelectedClipId(fresh.clips[0]?.id ?? null);
    setPlayheadMs(2600);
    setLastError(null);
  };

  const navItems: { id: ActiveSection; label: string }[] = [
    { id: 'engine', label: 'Timeline Engine' },
    { id: 'source', label: 'Kotlin Source' },
    { id: 'json', label: 'JSON Schema' },
    { id: 'guide', label: 'Build Guide' },
  ];

  return (
    <div className="min-h-screen bg-[#0B0D11] text-[#F1F5F9]">
      {/* 3-Zone Top Bar Contract */}
      <header className="border-b border-[#242936] bg-[#101319]">
        <div className="max-w-[1440px] mx-auto px-6 h-16 flex items-center justify-between gap-8">
          <a
            href="#top"
            onClick={(e) => {
              e.preventDefault();
              setSection('engine');
            }}
            className="text-lg font-bold tracking-tight text-white whitespace-nowrap shrink-0"
          >
            GVE Editor
          </a>

          <nav className="flex items-center gap-6 overflow-x-auto">
            {navItems.map((item) => {
              const active = section === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setSection(item.id)}
                  className={`text-sm font-medium transition-colors whitespace-nowrap shrink-0 cursor-pointer pb-0.5 border-b-2 ${
                    active
                      ? 'text-white border-teal-400'
                      : 'text-slate-400 border-transparent hover:text-slate-200'
                  }`}
                >
                  {item.label}
                </button>
              );
            })}
          </nav>

          <button
            type="button"
            onClick={handleDownloadAndroidZip}
            className="px-4 py-2 rounded-lg bg-teal-500 hover:bg-teal-400 text-slate-950 text-xs font-semibold transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            Download Project ZIP
          </button>
        </div>
      </header>

      <main className="max-w-[1440px] mx-auto px-6 py-6 space-y-5">
        {/* Clean Contextual Subheader */}
        <div className="flex flex-wrap items-baseline justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold text-white tracking-tight">
              {section === 'engine' && `${project.name} — Phase 2 Timeline Engine`}
              {section === 'source' && 'Android Kotlin & Compose Source Files'}
              {section === 'json' && 'Project JSON Serialization & Invariant Audit'}
              {section === 'guide' && 'Android Studio Setup & Modular Architecture'}
            </h1>
            <p className="text-xs text-slate-400 mt-1">
              Package <span className="font-mono text-slate-300">com.gima.gve</span>
              <span aria-hidden="true" className="mx-2 text-slate-600">·</span>
              <span>Non-destructive editing engine</span>
              <span aria-hidden="true" className="mx-2 text-slate-600">·</span>
              <span>Kotlin + Jetpack Compose + AndroidX Media3</span>
            </p>
          </div>
        </div>

        {section === 'engine' && (
          <TimelineWorkspace
            project={project}
            selectedClipId={selectedClipId}
            playheadMs={playheadMs}
            isPlaying={isPlaying}
            undoStack={undoStack}
            redoStack={redoStack}
            lastError={lastError}
            autosaveLabel={autosaveLabel}
            onSelectClip={setSelectedClipId}
            onSeek={(ms) => {
              setIsPlaying(false);
              setPlayheadMs(ms);
            }}
            onTogglePlay={() => setIsPlaying((p) => !p)}
            onSplitAtPlayhead={handleSplitAtPlayhead}
            onTrimHead={handleTrimHead}
            onTrimTail={handleTrimTail}
            onMoveClip={handleMoveClip}
            onDeleteClip={handleDeleteClip}
            onAddTrack={handleAddTrack}
            onRemoveTrack={handleRemoveTrack}
            onToggleTrackMute={handleToggleTrackMute}
            onToggleTrackLock={handleToggleTrackLock}
            onAddClip={handleAddClip}
            onUndo={handleUndo}
            onRedo={handleRedo}
            onClearError={() => setLastError(null)}
          />
        )}

        {section === 'source' && <KotlinSourceExplorer />}

        {section === 'json' && (
          <JsonSchemaView
            project={project}
            onImportProject={handleImportProject}
            onResetSample={handleResetSample}
          />
        )}

        {section === 'guide' && <BuildGuideView />}
      </main>
    </div>
  );
}
