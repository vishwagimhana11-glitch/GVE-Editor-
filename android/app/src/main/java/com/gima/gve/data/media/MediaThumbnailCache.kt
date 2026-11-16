package com.gima.gve.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import com.gima.gve.domain.model.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Memory-bounded cache for timeline thumbnails and compact audio waveform peaks.
 * Designed for 4 GB RAM Android devices:
 * - Thumbnails are downscaled to 160x90 before caching.
 * - Total bitmap memory is capped at 12 MB via [LruCache].
 * - Extraction runs lazily on [Dispatchers.IO] with immediate source bitmap recycling.
 */
class MediaThumbnailCache(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_BITMAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val waveformCache = object : LruCache<String, List<Float>>(64) {}
    private val loadMutex = Mutex()

    fun getCachedThumbnail(uri: String, timeMs: Long): Bitmap? {
        val bucketMs = (timeMs / 500L) * 500L
        return bitmapCache.get("$uri#$bucketMs")
    }

    suspend fun loadThumbnail(
        uriString: String,
        mediaType: MediaType,
        timeMs: Long = 0L
    ): Bitmap? = withContext(ioDispatcher) {
        if (mediaType == MediaType.AUDIO || mediaType == MediaType.TEXT) return@withContext null
        val bucketMs = (timeMs / 500L) * 500L
        val key = "$uriString#$bucketMs"
        bitmapCache.get(key)?.let { return@withContext it }

        loadMutex.withLock {
            bitmapCache.get(key)?.let { return@withLock it }
            val extracted = runCatching {
                val uri = Uri.parse(uriString)
                when (mediaType) {
                    MediaType.IMAGE -> decodeScaledImage(uri)
                    MediaType.VIDEO -> extractVideoFrame(uri, bucketMs)
                    MediaType.AUDIO, MediaType.TEXT -> null
                }
            }.getOrNull()

            if (extracted != null) {
                bitmapCache.put(key, extracted)
            }
            extracted
        }
    }

    fun getCachedWaveform(uriString: String): List<Float>? = waveformCache.get(uriString)

    suspend fun loadWaveform(
        uriString: String,
        barCount: Int = 32
    ): List<Float> = withContext(ioDispatcher) {
        val cacheKey = "$uriString#$barCount"
        waveformCache.get(cacheKey)?.let { return@withContext it }

        val peaks = runCatching { extractSampleSizePeaks(Uri.parse(uriString), barCount) }
            .getOrElse { defaultFallbackPeaks(barCount) }
        waveformCache.put(cacheKey, peaks)
        peaks
    }

    fun clear() {
        bitmapCache.evictAll()
        waveformCache.evictAll()
    }

    private fun extractVideoFrame(uri: Uri, timeMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val timeUs = timeMs * 1_000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMB_WIDTH,
                    THUMB_HEIGHT
                )
            } else {
                val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (full != null) {
                    val scaled = Bitmap.createScaledBitmap(full, THUMB_WIDTH, THUMB_HEIGHT, true)
                    if (scaled !== full) full.recycle()
                    scaled
                } else {
                    null
                }
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeScaledImage(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > THUMB_WIDTH * 2 &&
            bounds.outHeight / sampleSize > THUMB_HEIGHT * 2
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }

    /**
     * Extracts lightweight frame-energy approximations via [MediaExtractor] without full PCM decoding,
     * keeping memory footprint under a few kilobytes even on long audio files.
     */
    private fun extractSampleSizePeaks(uri: Uri, barCount: Int): List<Float> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var durationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    }
                    break
                }
            }
            if (audioTrackIndex < 0 || durationUs <= 0L) return defaultFallbackPeaks(barCount)

            extractor.selectTrack(audioTrackIndex)
            val buffer = ByteBuffer.allocate(8 * 1024)
            val rawSamples = FloatArray(barCount)
            val stepUs = max(1L, durationUs / barCount)

            for (i in 0 until barCount) {
                extractor.seekTo(i * stepUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val size = extractor.readSampleData(buffer, 0)
                if (size > 0) {
                    var energy = 0f
                    val inspectLimit = min(size, 256)
                    for (b in 0 until inspectLimit) {
                        energy += abs(buffer.get(b).toInt()) / 128f
                    }
                    rawSamples[i] = (energy / inspectLimit).coerceIn(0.12f, 1.0f)
                } else {
                    rawSamples[i] = 0.15f
                }
                buffer.clear()
            }
            val maxPeak = rawSamples.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
            rawSamples.map { (it / maxPeak).coerceIn(0.15f, 1.0f) }
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun defaultFallbackPeaks(barCount: Int): List<Float> =
        List(barCount) { idx -> 0.25f + ((idx * 37) % 60) / 100f }

    companion object {
        private const val THUMB_WIDTH = 160
        private const val THUMB_HEIGHT = 90
        private const val MAX_BITMAP_CACHE_BYTES = 12 * 1024 * 1024 // 12 MB cap for 4 GB devices
    }
}
