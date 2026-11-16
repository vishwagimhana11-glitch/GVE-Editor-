import React from 'react';
import { CheckCircle2, Clock, FolderGit2, Layers, ShieldCheck, Terminal } from 'lucide-react';

const ARCHITECTURE_LAYERS = [
  {
    name: 'UI Layer',
    pkg: 'com.gima.gve.ui',
    description:
      'Original GVE Editor dark Compose UI: HomeScreen (GveBrandMark, New/Rename, SAF Import, Recent Projects) and Phase 3 EditorScreen (Top Toolbar, ExoPlayer Fit/Fill/Fullscreen Preview, TimelineCanvas, 11-Tool Bottom Bar, MediaImportSheet).',
  },
  {
    name: 'Domain Layer',
    pkg: 'com.gima.gve.domain',
    description:
      'Pure Kotlin TimelineModels, TimelineEngine (trim, split, move, duplicate, ripple delete, speed, volume, transform, text), EditHistoryManager (undo/redo + coalescing), and ProjectRepository.',
  },
  {
    name: 'Data Layer',
    pkg: 'com.gima.gve.data',
    description:
      'ProjectJsonSerializer with integrity checks, Mutex-protected atomic LocalFileProjectRepository, AutosaveCoordinator, SafMediaInspector, RecentMediaStore, and 12 MB MediaThumbnailCache.',
  },
  {
    name: 'Rendering Layer',
    pkg: 'com.gima.gve.rendering.media3',
    description:
      'Media3 1.8.0 Media3TimelineMapper and ExoPlayerPreviewController supporting preview quality (360p/720p/1080p), Fit/Fill modes, speed/volume sync, and gap-aligned EditedMediaItemSequence compositions.',
  },
];

const SCOPE_MATRIX = [
  {
    feature: 'Non-destructive Multi-Track Models (Video, Audio, Overlay, Text) & Validation',
    status: 'Implemented (Static-audited)',
    done: true,
  },
  {
    feature: 'Trim, Split, Move, Duplicate, Speed, Volume, Transform, Text & Ripple Delete',
    status: 'Implemented (Static-audited)',
    done: true,
  },
  {
    feature: 'Bounded Undo/Redo Command Stack, Atomic JSON Persistence & Autosave',
    status: 'Implemented (Static-audited)',
    done: true,
  },
  {
    feature: 'Phase 3 Dark Editor UI, TimelineCanvas (Thumbnails/Waveforms/Zoom/Drag) & MediaImportSheet',
    status: 'Implemented (Not build-verified)',
    done: true,
  },
  {
    feature: 'Media3 1.8.0 ExoPlayer Preview (Black Gap Frame, Fit/Fill, 360p/720p/1080p, Fullscreen)',
    status: 'Implemented (Not build-verified)',
    done: true,
  },
  {
    feature: 'Stickers, Effects, Filters & Transitions Tooling (OpenGL Shader Pipeline)',
    status: 'Coming Soon (Phase 4)',
    done: false,
  },
  {
    feature: 'Simultaneous Multi-Track GL Compositing & Hardware Transformer MP4 Export',
    status: 'Pending (Phase 4)',
    done: false,
  },
];

export const BuildGuideView: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <section className="bg-[#13161D] border border-[#242936] rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2.5">
            <Terminal className="w-4 h-4 text-teal-400" />
            <h2 className="text-base font-semibold text-white">Android Studio Build Instructions</h2>
          </div>

          <ol className="space-y-3 text-sm text-slate-300">
            <li className="flex gap-3">
              <span className="font-mono text-xs text-teal-400 pt-0.5">01.</span>
              <span>
                Install <strong className="text-white font-medium">Android Studio Ladybug (2024.2.1+)</strong> with <strong className="text-white font-medium">JDK 17</strong> and Android SDK <span className="font-mono text-xs text-slate-200">API 35</span> (<span className="font-mono text-xs text-slate-200">minSdk = 26</span>).
              </span>
            </li>
            <li className="flex gap-3">
              <span className="font-mono text-xs text-teal-400 pt-0.5">02.</span>
              <span>
                Extract the downloaded ZIP or open the <span className="font-mono text-xs text-teal-300">android/</span> folder in Android Studio (<strong className="text-white font-medium">File → Open</strong>) to resolve Jetpack Compose, Media3 <span className="font-mono text-xs">1.8.0</span>, and <span className="font-mono text-xs">kotlinx-serialization-json</span>.
              </span>
            </li>
            <li className="flex gap-3">
              <span className="font-mono text-xs text-teal-400 pt-0.5">03.</span>
              <div className="space-y-1.5 flex-1">
                <span>If the Gradle wrapper is missing locally, generate it once inside the project root:</span>
                <pre className="p-2.5 rounded-lg bg-[#0B0D11] border border-[#242936] font-mono text-xs text-slate-200 overflow-x-auto">
                  gradle wrapper --gradle-version 8.9
                </pre>
              </div>
            </li>
            <li className="flex gap-3">
              <span className="font-mono text-xs text-teal-400 pt-0.5">04.</span>
              <div className="space-y-1.5 flex-1">
                <span>Run the unit test suite and assemble the debug APK:</span>
                <pre className="p-2.5 rounded-lg bg-[#0B0D11] border border-[#242936] font-mono text-xs text-slate-200 overflow-x-auto">
                  {`gradlew.bat clean\ngradlew.bat :app:testDebugUnitTest\ngradlew.bat :app:assembleDebug`}
                </pre>
              </div>
            </li>
          </ol>
        </section>

        <section className="bg-[#13161D] border border-[#242936] rounded-xl p-5 space-y-4">
          <div className="flex items-center gap-2.5">
            <Layers className="w-4 h-4 text-sky-400" />
            <h2 className="text-base font-semibold text-white">Modular Package Architecture</h2>
          </div>

          <div className="divide-y divide-[#1E2330]">
            {ARCHITECTURE_LAYERS.map((layer) => (
              <div key={layer.name} className="py-3 first:pt-0 last:pb-0">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-white">{layer.name}</span>
                  <span className="text-xs font-mono text-teal-300">{layer.pkg}</span>
                </div>
                <p className="text-xs text-slate-400 mt-1 leading-relaxed">{layer.description}</p>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_360px] gap-4">
        <section className="bg-[#13161D] border border-[#242936] rounded-xl p-5 space-y-3">
          <div className="flex items-center gap-2.5">
            <FolderGit2 className="w-4 h-4 text-teal-400" />
            <h2 className="text-base font-semibold text-white">Implementation & Verification Status</h2>
          </div>
          <div className="divide-y divide-[#1E2330]">
            {SCOPE_MATRIX.map((row) => (
              <div key={row.feature} className="py-2.5 flex items-center justify-between gap-4 text-xs">
                <span className="text-slate-200">{row.feature}</span>
                <span
                  className={`flex items-center gap-1.5 font-mono shrink-0 ${
                    row.done ? 'text-teal-400' : 'text-slate-400'
                  }`}
                >
                  {row.done ? <CheckCircle2 className="w-3.5 h-3.5" /> : <Clock className="w-3.5 h-3.5" />}
                  <span>{row.status}</span>
                </span>
              </div>
            ))}
          </div>
        </section>

        <section className="bg-[#13161D] border border-[#242936] rounded-xl p-5 space-y-3">
          <div className="flex items-center gap-2.5">
            <ShieldCheck className="w-4 h-4 text-amber-400" />
            <h2 className="text-base font-semibold text-white">Security & Media Integrity</h2>
          </div>
          <p className="text-xs text-slate-300 leading-relaxed">
            Original video and audio URIs are treated as read-only inputs. Every trim, split, and move updates only the non-destructive <span className="font-mono text-slate-200">MediaClip</span> descriptors inside the versioned <span className="font-mono text-slate-200">.gve.json</span> project file.
          </p>
          <p className="text-xs text-slate-300 leading-relaxed">
            No Gemini API keys, OAuth client secrets, or cloud rendering credentials are stored inside the Android module or <span className="font-mono text-slate-200">BuildConfig</span>. Future AI or cloud export capabilities must authenticate through a dedicated backend proxy.
          </p>
        </section>
      </div>
    </div>
  );
};
