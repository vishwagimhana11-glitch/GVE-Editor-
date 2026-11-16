# GVE Editor (`com.gima.gve`)

Non-destructive Android video editor built with Kotlin, Jetpack Compose, Coroutines, `kotlinx.serialization`, and AndroidX Media3 (`1.8.0`).

## Architecture Overview

- `domain/model/TimelineModels.kt`: Immutable `TimelineProject`, `TimelineTrack` (`VIDEO`, `AUDIO`, `OVERLAY`, `TEXT`), `MediaClip` (with speed, volume, rotation, normalized `CropRect`, text content), `MediaAsset`, and `ProjectSettings` with duplicate-ID and same-track-only overlap validation.
- `domain/engine/TimelineEngine.kt`: Pure functions for multi-track creation/removal, clip addition, duplication, head/tail trimming, playhead splitting, clip moving, standard/ripple deletion, track mute/lock/visibility, clip speed/volume/transform, text editing, and project renaming.
- `domain/history/EditHistoryManager.kt`: Reversible `TimelineCommand` history stack with bounded undo/redo and continuous drag-gesture coalescing (`allowCoalesce`).
- `data/serialization/ProjectJsonSerializer.kt`: Versioned `kotlinx.serialization` JSON encoder/decoder invoking `TimelineProject.validateIntegrity()`.
- `data/repository/LocalFileProjectRepository.kt`: Mutex-synchronized atomic temp-file writes and debounced `AutosaveCoordinator`.
- `data/media/SafMediaInspector.kt`: SAF `OpenDocument` read permission persistence, `MediaMetadataRetriever` inspection for video/audio, bounds-only image inspection, and `RecentMediaStore`.
- `data/media/MediaThumbnailCache.kt`: 12 MB `LruCache` providing downscaled `160x90` timeline thumbnails and compact `MediaExtractor` sample-energy audio waveforms for 4 GB RAM devices.
- `rendering/media3/Media3TimelineMapper.kt` & `ExoPlayerPreviewController.kt`: Maps non-destructive `MediaClip` segments to `MediaItem.ClippingConfiguration`, primary-track `ExoPlayer` playback with preview quality (`360p`/`720p`/`1080p`), Fit/Fill scaling, speed/volume sync, and gap-aligned Media3 `1.8.0` `EditedMediaItemSequence` compositions.
- `ui/screens/HomeScreen.kt`, `EditorScreen.kt` & `ui/components/*`: Original GVE Editor dark Compose UI (`GveBrandMark`, Home rename/open/import, central black/fullscreen ExoPlayer preview, multi-track `TimelineCanvas` with drag-to-move and zoom, 11-tool bottom bar, `MediaImportSheet`, and Export Readiness dialog).

## Media3 Capability Boundaries

- **Supported now**: Sequential real-time preview of the primary visible video track (`V1`) with `sourceInMs..sourceOutMs` clipping, preview quality constraints (`360p`/`720p`/`1080p`), Fit/Fill scaling, fullscreen preview, playback speed/volume sync, live text overlay badge preview, and gap-aligned `Composition` building (`EditedMediaItemSequence.Builder(trackTypes).addGap(...)`).
- **Scheduled for Phase 4**: Simultaneous live multi-track video/GL compositing (`V1 + Overlay + Text`), hardware `Media3 Transformer` MP4 export UI, and **Coming Soon** shader/animation tools (`Stickers`, `Effects`, `Filters`, `Transitions`), plus authenticated backend cloud rendering.

## Android Studio Build & Verification Instructions

1. Install **Android Studio Ladybug (2024.2.1)** or newer with **JDK 17** and **Android SDK 35** (`compileSdk = 35`, `minSdk = 26`).
2. Open the `android/` folder in Android Studio (**File -> Open**).
3. If the Gradle wrapper scripts (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) are not yet present in your local checkout, generate them once inside the project root using Gradle `8.9`:
   ```
   gradle wrapper --gradle-version 8.9
   ```
4. Sync Gradle, then run the unit test suite and assemble the debug APK:
   ```
   # Windows
   gradlew.bat clean
   gradlew.bat :app:testDebugUnitTest
   gradlew.bat :app:assembleDebug

   # macOS / Linux
   ./gradlew clean
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:assembleDebug
   ```

## Security Note
Never embed Gemini API keys, OAuth client secrets, or cloud rendering credentials inside Android source files, `BuildConfig`, or `res/values`. Any AI or cloud rendering features must communicate exclusively through an authenticated backend service.
