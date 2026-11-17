package com.example.lib.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class MediaFileMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val bitrateBps: Long,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val mime: String
)

object AudioVideoEngine {

    fun inspectMedia(context: Context, uri: Uri): MediaFileMetadata {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val hasA = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            val hasV = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            MediaFileMetadata(dur, w, h, br, hasA, hasV, mime)
        } catch (_: Exception) {
            MediaFileMetadata(0L, 0, 0, 0L, hasAudio = false, hasVideo = false, mime = "unknown")
        } finally {
            runCatching { mmr.release() }
        }
    }

    /**
     * Extracts audio track from any video/audio container directly on-device via MediaExtractor + MediaMuxer (.m4a).
     * Optionally trims between [startMs, endMs].
     */
    suspend fun remuxOrTrimAudio(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        startMs: Long = 0L,
        endMs: Long = -1L,
        audioOnly: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, sourceUri, null)
            val trackMap = mutableMapOf<Int, Int>()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                val keep = if (audioOnly) mime.startsWith("audio/") else (mime.startsWith("audio/") || mime.startsWith("video/"))
                if (keep) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(fmt)
                }
            }
            if (trackMap.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No compatible media tracks found in container."))
            }

            muxer.start()
            if (startMs > 0L) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }
            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            val endUs = if (endMs > 0L) endMs * 1000L else Long.MAX_VALUE

            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                info.presentationTimeUs = (pts - startMs * 1000L).coerceAtLeast(0L)
                val sFlags = extractor.sampleFlags
                var bufFlags = 0
                if ((sFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    bufFlags = bufFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    (sFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0
                ) {
                    bufFlags = bufFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                info.flags = bufFlags
                val srcTrack = extractor.sampleTrackIndex
                val dstTrack = trackMap[srcTrack]
                if (dstTrack != null) {
                    muxer.writeSampleData(dstTrack, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
