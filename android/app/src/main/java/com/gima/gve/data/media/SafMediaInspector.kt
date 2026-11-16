package com.gima.gve.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.gima.gve.domain.model.MediaAsset
import com.gima.gve.domain.model.MediaType
import com.gima.gve.domain.model.TimelineConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Inspects video, audio, and image files picked via Android's Storage Access Framework,
 * persists read URI permissions when supported, and validates metadata on a background dispatcher
 * without modifying original source files.
 */
class SafMediaInspector(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun inspectUri(
        uri: Uri,
        forcedType: MediaType? = null
    ): Result<MediaAsset> = withContext(ioDispatcher) {
        runCatching {
            val resolver = context.contentResolver
            val uriString = uri.toString()
            require(TimelineConstants.isValidSourceUri(uriString)) {
                "Unsupported URI scheme for selected media: $uriString"
            }

            try {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Transient URI grant remains active for the current session.
            }

            val mimeType = resolver.getType(uri).orEmpty()
            val displayName = queryDisplayName(resolver, uri) ?: "Imported_Media"

            val isImage = forcedType == MediaType.IMAGE || mimeType.startsWith("image/")
            if (isImage) {
                return@runCatching inspectImage(resolver, uri, uriString, displayName)
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: throw IllegalArgumentException("Unable to read media duration for '$displayName'.")

                require(durationMs >= TimelineConstants.MIN_CLIP_DURATION_MS) {
                    "Selected media '$displayName' (${durationMs}ms) is shorter than ${TimelineConstants.MIN_CLIP_DURATION_MS}ms."
                }

                val width = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                val height = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                val hasVideoMeta = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    ?.equals("yes", ignoreCase = true) == true
                val hasAudioMeta = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                    ?.equals("yes", ignoreCase = true) == true

                val detectedType = forcedType ?: when {
                    mimeType.startsWith("audio/") -> MediaType.AUDIO
                    mimeType.startsWith("video/") || hasVideoMeta || (width != null && width > 0) -> MediaType.VIDEO
                    hasAudioMeta -> MediaType.AUDIO
                    else -> throw IllegalArgumentException("Selected file '$displayName' is neither a readable video nor audio track.")
                }

                MediaAsset(
                    id = "asset-${UUID.randomUUID().toString().take(8)}",
                    uri = uriString,
                    displayName = displayName,
                    mediaType = detectedType,
                    totalDurationMs = durationMs,
                    width = width,
                    height = height,
                    hasAudioStream = hasAudioMeta || detectedType == MediaType.AUDIO
                )
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun inspectImage(
        resolver: ContentResolver,
        uri: Uri,
        uriString: String,
        displayName: String
    ): MediaAsset {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("Unable to open image '$displayName'.")

        require(options.outWidth > 0 && options.outHeight > 0) {
            "Selected file '$displayName' is not a valid image."
        }

        return MediaAsset(
            id = "asset-${UUID.randomUUID().toString().take(8)}",
            uri = uriString,
            displayName = displayName,
            mediaType = MediaType.IMAGE,
            totalDurationMs = TimelineConstants.MAX_STILL_DURATION_MS,
            width = options.outWidth,
            height = options.outHeight,
            hasAudioStream = false
        )
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}

/**
 * Lightweight persisted list of recently imported media assets across projects.
 */
class RecentMediaStore(
    private val storeFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun loadRecentMedia(): List<MediaAsset> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                if (!storeFile.exists()) return@runCatching emptyList()
                val raw = storeFile.readText(Charsets.UTF_8)
                json.decodeFromString(ListSerializer(MediaAsset.serializer()), raw)
            }.getOrDefault(emptyList())
        }
    }

    suspend fun recordMedia(asset: MediaAsset, maxItems: Int = 24): List<MediaAsset> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = runCatching {
                if (!storeFile.exists()) emptyList()
                else json.decodeFromString(ListSerializer(MediaAsset.serializer()), storeFile.readText(Charsets.UTF_8))
            }.getOrDefault(emptyList())

            val updated = (listOf(asset) + current.filterNot { it.uri == asset.uri }).take(maxItems)
            runCatching {
                storeFile.parentFile?.mkdirs()
                val temp = File(storeFile.parentFile, "${storeFile.name}.tmp")
                temp.writeText(json.encodeToString(ListSerializer(MediaAsset.serializer()), updated), Charsets.UTF_8)
                if (!temp.renameTo(storeFile)) {
                    temp.copyTo(storeFile, overwrite = true)
                    temp.delete()
                }
            }
            updated
        }
    }
}
