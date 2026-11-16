import timelineModelsKt from '../../android/app/src/main/java/com/gima/gve/domain/model/TimelineModels.kt?raw';
import timelineOperationResultKt from '../../android/app/src/main/java/com/gima/gve/domain/engine/TimelineOperationResult.kt?raw';
import timelineEngineKt from '../../android/app/src/main/java/com/gima/gve/domain/engine/TimelineEngine.kt?raw';
import editHistoryManagerKt from '../../android/app/src/main/java/com/gima/gve/domain/history/EditHistoryManager.kt?raw';
import projectRepositoryKt from '../../android/app/src/main/java/com/gima/gve/domain/repository/ProjectRepository.kt?raw';
import projectJsonSerializerKt from '../../android/app/src/main/java/com/gima/gve/data/serialization/ProjectJsonSerializer.kt?raw';
import localFileProjectRepositoryKt from '../../android/app/src/main/java/com/gima/gve/data/repository/LocalFileProjectRepository.kt?raw';
import safMediaInspectorKt from '../../android/app/src/main/java/com/gima/gve/data/media/SafMediaInspector.kt?raw';
import mediaThumbnailCacheKt from '../../android/app/src/main/java/com/gima/gve/data/media/MediaThumbnailCache.kt?raw';
import media3TimelineMapperKt from '../../android/app/src/main/java/com/gima/gve/rendering/media3/Media3TimelineMapper.kt?raw';
import exoPlayerPreviewControllerKt from '../../android/app/src/main/java/com/gima/gve/rendering/media3/ExoPlayerPreviewController.kt?raw';
import editorStateHolderKt from '../../android/app/src/main/java/com/gima/gve/ui/state/EditorStateHolder.kt?raw';
import homeStateHolderKt from '../../android/app/src/main/java/com/gima/gve/ui/state/HomeStateHolder.kt?raw';
import timelineCanvasKt from '../../android/app/src/main/java/com/gima/gve/ui/components/TimelineCanvas.kt?raw';
import editorToolPanelsKt from '../../android/app/src/main/java/com/gima/gve/ui/components/EditorToolPanels.kt?raw';
import mediaImportSheetKt from '../../android/app/src/main/java/com/gima/gve/ui/components/MediaImportSheet.kt?raw';
import homeScreenKt from '../../android/app/src/main/java/com/gima/gve/ui/screens/HomeScreen.kt?raw';
import editorScreenKt from '../../android/app/src/main/java/com/gima/gve/ui/screens/EditorScreen.kt?raw';
import gveThemeKt from '../../android/app/src/main/java/com/gima/gve/ui/theme/GveTheme.kt?raw';
import mainActivityKt from '../../android/app/src/main/java/com/gima/gve/MainActivity.kt?raw';
import timelineEngineTestKt from '../../android/app/src/test/java/com/gima/gve/domain/TimelineEngineTest.kt?raw';
import projectPersistenceTestKt from '../../android/app/src/test/java/com/gima/gve/data/ProjectPersistenceTest.kt?raw';
import editorStateHolderTestKt from '../../android/app/src/test/java/com/gima/gve/ui/EditorStateHolderTest.kt?raw';
import appBuildGradle from '../../android/app/build.gradle.kts?raw';
import libsVersionsToml from '../../android/gradle/libs.versions.toml?raw';
import rootBuildGradle from '../../android/build.gradle.kts?raw';
import settingsGradle from '../../android/settings.gradle.kts?raw';
import gradleProperties from '../../android/gradle.properties?raw';
import androidManifestXml from '../../android/app/src/main/AndroidManifest.xml?raw';
import androidReadmeMd from '../../android/README.md?raw';

export interface AndroidSourceFile {
  id: string;
  name: string;
  path: string;
  layer: 'Domain' | 'Data' | 'Rendering' | 'UI' | 'Tests' | 'Gradle';
  language: 'kotlin' | 'toml' | 'xml' | 'markdown';
  summary: string;
  content: string;
}

export const ANDROID_SOURCE_FILES: AndroidSourceFile[] = [
  {
    id: 'timeline-models',
    name: 'TimelineModels.kt',
    path: 'app/src/main/java/com/gima/gve/domain/model/TimelineModels.kt',
    layer: 'Domain',
    language: 'kotlin',
    summary: 'Immutable MediaClip, TimelineTrack (VIDEO/AUDIO/OVERLAY/TEXT), CropRect, MediaAsset, and TimelineProject integrity validation.',
    content: timelineModelsKt,
  },
  {
    id: 'timeline-engine',
    name: 'TimelineEngine.kt',
    path: 'app/src/main/java/com/gima/gve/domain/engine/TimelineEngine.kt',
    layer: 'Domain',
    language: 'kotlin',
    summary: 'Pure timeline engine: trim, split, move, duplicate, ripple delete, speed, volume, transform, text editing, and multi-track support.',
    content: timelineEngineKt,
  },
  {
    id: 'timeline-result',
    name: 'TimelineOperationResult.kt',
    path: 'app/src/main/java/com/gima/gve/domain/engine/TimelineOperationResult.kt',
    layer: 'Domain',
    language: 'kotlin',
    summary: 'Typed TimelineError hierarchy including duplicate ID, overlap, invalid URI/duration, and invalid operation errors.',
    content: timelineOperationResultKt,
  },
  {
    id: 'edit-history',
    name: 'EditHistoryManager.kt',
    path: 'app/src/main/java/com/gima/gve/domain/history/EditHistoryManager.kt',
    layer: 'Domain',
    language: 'kotlin',
    summary: 'Reversible TimelineCommand pattern across all Phase 2 & Phase 3 operations with bounded undo/redo and coalescing.',
    content: editHistoryManagerKt,
  },
  {
    id: 'project-repo',
    name: 'ProjectRepository.kt',
    path: 'app/src/main/java/com/gima/gve/domain/repository/ProjectRepository.kt',
    layer: 'Domain',
    language: 'kotlin',
    summary: 'Domain repository contract for saving, loading, listing, and deleting projects.',
    content: projectRepositoryKt,
  },
  {
    id: 'json-serializer',
    name: 'ProjectJsonSerializer.kt',
    path: 'app/src/main/java/com/gima/gve/data/serialization/ProjectJsonSerializer.kt',
    layer: 'Data',
    language: 'kotlin',
    summary: 'kotlinx.serialization JSON encoder/decoder with full TimelineProject.validateIntegrity verification.',
    content: projectJsonSerializerKt,
  },
  {
    id: 'local-file-repo',
    name: 'LocalFileProjectRepository.kt',
    path: 'app/src/main/java/com/gima/gve/data/repository/LocalFileProjectRepository.kt',
    layer: 'Data',
    language: 'kotlin',
    summary: 'Mutex-protected atomic file persistence and debounced Coroutine Flow AutosaveCoordinator.',
    content: localFileProjectRepositoryKt,
  },
  {
    id: 'saf-media-inspector',
    name: 'SafMediaInspector.kt',
    path: 'app/src/main/java/com/gima/gve/data/media/SafMediaInspector.kt',
    layer: 'Data',
    language: 'kotlin',
    summary: 'SAF URI permission persistence, MediaMetadataRetriever inspection, bounds-only image probing, and RecentMediaStore.',
    content: safMediaInspectorKt,
  },
  {
    id: 'media-thumbnail-cache',
    name: 'MediaThumbnailCache.kt',
    path: 'app/src/main/java/com/gima/gve/data/media/MediaThumbnailCache.kt',
    layer: 'Data',
    language: 'kotlin',
    summary: '12 MB LruCache for downscaled 160x90 video/image thumbnails and lightweight MediaExtractor audio waveform peaks.',
    content: mediaThumbnailCacheKt,
  },
  {
    id: 'media3-mapper',
    name: 'Media3TimelineMapper.kt',
    path: 'app/src/main/java/com/gima/gve/rendering/media3/Media3TimelineMapper.kt',
    layer: 'Rendering',
    language: 'kotlin',
    summary: 'Media3 1.8.0 mapper for primary ExoPlayer playlists and gap-aligned EditedMediaItemSequence compositions.',
    content: media3TimelineMapperKt,
  },
  {
    id: 'exoplayer-controller',
    name: 'ExoPlayerPreviewController.kt',
    path: 'app/src/main/java/com/gima/gve/rendering/media3/ExoPlayerPreviewController.kt',
    layer: 'Rendering',
    language: 'kotlin',
    summary: 'Lifecycle-safe ExoPlayer controller with preview quality (360p/720p/1080p), Fit/Fill modes, and speed/volume sync.',
    content: exoPlayerPreviewControllerKt,
  },
  {
    id: 'home-state-holder',
    name: 'HomeStateHolder.kt',
    path: 'app/src/main/java/com/gima/gve/ui/state/HomeStateHolder.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'State holder for New Project, Recent Projects, Rename, Open/Delete, and SAF video import.',
    content: homeStateHolderKt,
  },
  {
    id: 'editor-state-holder',
    name: 'EditorStateHolder.kt',
    path: 'app/src/main/java/com/gima/gve/ui/state/EditorStateHolder.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Reactive StateFlow state holder driving Phase 3 tools, zoom, preview modes, undo/redo, and debounced autosave.',
    content: editorStateHolderKt,
  },
  {
    id: 'timeline-canvas',
    name: 'TimelineCanvas.kt',
    path: 'app/src/main/java/com/gima/gve/ui/components/TimelineCanvas.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Multi-track timeline canvas with ruler, playhead needle, track controls, lazy thumbnails/waveforms, zoom, and drag-to-move.',
    content: timelineCanvasKt,
  },
  {
    id: 'editor-tool-panels',
    name: 'EditorToolPanels.kt',
    path: 'app/src/main/java/com/gima/gve/ui/components/EditorToolPanels.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Bottom 11-tool bar (Edit, Audio, Text, Overlay, Speed, Crop, Volume, and Coming Soon shader tools) with collapsible panels.',
    content: editorToolPanelsKt,
  },
  {
    id: 'media-import-sheet',
    name: 'MediaImportSheet.kt',
    path: 'app/src/main/java/com/gima/gve/ui/components/MediaImportSheet.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Material 3 ModalBottomSheet with SAF Video/Audio/Image pickers, Project Media & Recent Media tabs, and thumbnails.',
    content: mediaImportSheetKt,
  },
  {
    id: 'home-screen',
    name: 'HomeScreen.kt',
    path: 'app/src/main/java/com/gima/gve/ui/screens/HomeScreen.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Compose Home screen with GveBrandMark, New Project, Rename Project, SAF Import Video, and Recent Projects list.',
    content: homeScreenKt,
  },
  {
    id: 'editor-screen',
    name: 'EditorScreen.kt',
    path: 'app/src/main/java/com/gima/gve/ui/screens/EditorScreen.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Phase 3 Compose Editor workspace with Top Toolbar, ExoPlayer preview (black/Fit-Fill/fullscreen), TimelineCanvas, tools, and Export Readiness.',
    content: editorScreenKt,
  },
  {
    id: 'gve-theme',
    name: 'GveTheme.kt',
    path: 'app/src/main/java/com/gima/gve/ui/theme/GveTheme.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Original GVE Editor dark charcoal theme (Electric Indigo/Blue accents) and Canvas GveBrandMark vector logo.',
    content: gveThemeKt,
  },
  {
    id: 'main-activity',
    name: 'MainActivity.kt',
    path: 'app/src/main/java/com/gima/gve/MainActivity.kt',
    layer: 'UI',
    language: 'kotlin',
    summary: 'Main Compose Activity wiring RecentMediaStore, MediaThumbnailCache, HomeScreen, and EditorScreen.',
    content: mainActivityKt,
  },
  {
    id: 'timeline-engine-test',
    name: 'TimelineEngineTest.kt',
    path: 'app/src/test/java/com/gima/gve/domain/TimelineEngineTest.kt',
    layer: 'Tests',
    language: 'kotlin',
    summary: 'JUnit 4 unit tests for trim, split, cross-track overlaps, speed, transform, volume, text/overlay tracks, and undo/redo.',
    content: timelineEngineTestKt,
  },
  {
    id: 'project-persistence-test',
    name: 'ProjectPersistenceTest.kt',
    path: 'app/src/test/java/com/gima/gve/data/ProjectPersistenceTest.kt',
    layer: 'Tests',
    language: 'kotlin',
    summary: 'Unit tests for JSON save/load, corrupted file tolerance, duplicate ID decode rejection, and autosave recovery.',
    content: projectPersistenceTestKt,
  },
  {
    id: 'editor-state-holder-test',
    name: 'EditorStateHolderTest.kt',
    path: 'app/src/test/java/com/gima/gve/ui/EditorStateHolderTest.kt',
    layer: 'Tests',
    language: 'kotlin',
    summary: 'Unit tests for clip selection, tool tabs, playhead sync, undo/redo, project rename, and repository state restoration.',
    content: editorStateHolderTestKt,
  },
  {
    id: 'app-build-gradle',
    name: 'app/build.gradle.kts',
    path: 'app/build.gradle.kts',
    layer: 'Gradle',
    language: 'kotlin',
    summary: 'Android application Gradle module with Compose BOM, Media3 1.8.0, Lifecycle Compose, and kotlinx.serialization.',
    content: appBuildGradle,
  },
  {
    id: 'libs-versions',
    name: 'libs.versions.toml',
    path: 'gradle/libs.versions.toml',
    layer: 'Gradle',
    language: 'toml',
    summary: 'Gradle version catalog pinning AGP 8.7.2, Kotlin 2.0.21, Compose BOM, Coroutines, Serialization, and Media3 1.8.0.',
    content: libsVersionsToml,
  },
  {
    id: 'root-build-gradle',
    name: 'build.gradle.kts',
    path: 'build.gradle.kts',
    layer: 'Gradle',
    language: 'kotlin',
    summary: 'Root Gradle plugin declarations.',
    content: rootBuildGradle,
  },
  {
    id: 'settings-gradle',
    name: 'settings.gradle.kts',
    path: 'settings.gradle.kts',
    layer: 'Gradle',
    language: 'kotlin',
    summary: 'Gradle pluginManagement and repository configuration.',
    content: settingsGradle,
  },
  {
    id: 'gradle-properties',
    name: 'gradle.properties',
    path: 'gradle.properties',
    layer: 'Gradle',
    language: 'toml',
    summary: 'Gradle JVM args and AndroidX configuration.',
    content: gradleProperties,
  },
  {
    id: 'android-manifest',
    name: 'AndroidManifest.xml',
    path: 'app/src/main/AndroidManifest.xml',
    layer: 'Gradle',
    language: 'xml',
    summary: 'Manifest declaring MainActivity launcher for com.gima.gve.',
    content: androidManifestXml,
  },
  {
    id: 'android-readme',
    name: 'README.md',
    path: 'README.md',
    layer: 'Gradle',
    language: 'markdown',
    summary: 'Android Studio setup, Gradle wrapper generation, and build verification instructions.',
    content: androidReadmeMd,
  },
];
