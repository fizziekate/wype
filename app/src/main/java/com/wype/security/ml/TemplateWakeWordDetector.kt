package com.wype.security.ml

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.*
import java.io.File
import java.nio.ByteOrder

/**
 * Template-based wake word detector using DTW (Dynamic Time Warping).
 *
 * How it works:
 *  1. On init, decode the user's saved 3gp recording to PCM and extract MFCC features
 *     as a "template" sequence.
 *  2. Continuously record live audio into a rolling buffer.
 *  3. Every CHECK_INTERVAL_MS, extract MFCC from the last ~2.5 seconds of live audio
 *     and compare it to the template with DTW.
 *  4. If the normalised DTW distance is below DTW_MATCH_THRESHOLD, fire onWakeWordDetected.
 *
 * No cloud, no internet, no pre-trained model required.
 */
class TemplateWakeWordDetector(
    private val templatePath: String,
    private val callback: WakeWordCallback
) : CoroutineScope {

    companion object {
        private const val TAG = "TemplateWakeWordDetector"

        // ---- audio capture ----
        private const val SAMPLE_RATE       = 16000
        private const val AUDIO_FORMAT      = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_CONFIG    = AudioFormat.CHANNEL_IN_MONO

        // Rolling buffer holds 3 seconds of 16-kHz mono audio
        private const val BUFFER_DURATION_S = 3
        private val BUFFER_SAMPLES          = SAMPLE_RATE * BUFFER_DURATION_S  // 48 000

        // ---- MFCC framing ----
        private const val FRAME_SIZE_MS     = 25   // 25 ms  → 400 samples @ 16 kHz
        private const val HOP_SIZE_MS       = 10   // 10 ms  → 160 samples
        private val FRAME_SAMPLES           = SAMPLE_RATE * FRAME_SIZE_MS / 1000  // 400
        private val HOP_SAMPLES             = SAMPLE_RATE * HOP_SIZE_MS  / 1000  // 160

        // ---- detection ----
        // Lower = stricter match required.  Tune for your environment.
        private const val DTW_MATCH_THRESHOLD = 7.5f
        private const val COOLDOWN_MS       = 2_000L
        private const val CHECK_INTERVAL_MS = 250L   // run DTW 4× per second

        // Snapshot used for DTW: last 2.5 s before query point
        private val SNAPSHOT_SAMPLES        = (SAMPLE_RATE * 2.5).toInt()  // 40 000

        private val AUDIO_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        ).coerceAtLeast(4096)
    }

    // ---- coroutine scope ----
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    // ---- audio infrastructure ----
    private var audioRecord: AudioRecord? = null
    private var isListening = false

    // Circular buffer
    private val sampleBuffer = FloatArray(BUFFER_SAMPLES)
    private var bufferWritePos = 0
    private var bufferFilled   = false

    // ---- template ----
    private var templateFeatures: Array<FloatArray>? = null
    private val featureExtractor = AudioFeatureExtractor()

    // ---- state ----
    private var lastDetectionTime = 0L

    // ================================================================
    // Public interface
    // ================================================================

    interface WakeWordCallback {
        fun onWakeWordDetected(confidence: Float, wakeWord: String)
        fun onError(error: String)
    }

    /**
     * Call once before [startListening].
     * Returns false if the template file cannot be decoded or is too short.
     */
    fun initialize(): Boolean {
        return try {
            val feats = loadTemplateFeatures(templatePath)
            if (feats == null || feats.isEmpty()) {
                Log.e(TAG, "Template features empty – cannot start detector")
                callback.onError("No valid template recording found at $templatePath")
                return false
            }
            templateFeatures = feats
            Log.i(TAG, "Template loaded: ${feats.size} MFCC frames from $templatePath")

            initializeAudioRecord()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            callback.onError("Init failed: ${e.message}")
            false
        }
    }

    /** Start listening.  [initialize] must be called first. */
    fun startListening(): Boolean {
        if (isListening) return true
        return try {
            audioRecord?.startRecording()
            isListening = true
            launch { captureAudioLoop() }
            launch { detectionLoop() }
            Log.i(TAG, "Template DTW detection started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            callback.onError("Could not start audio: ${e.message}")
            false
        }
    }

    fun stopListening() {
        isListening = false
        try { audioRecord?.stop() } catch (_: Exception) {}
    }

    fun release() {
        stopListening()
        job.cancel()
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    fun getModelInfo(): String =
        "Template DTW Matcher – ${templateFeatures?.size ?: 0} frames, threshold=$DTW_MATCH_THRESHOLD"

    // ================================================================
    // Audio capture
    // ================================================================

    private suspend fun captureAudioLoop() = withContext(Dispatchers.Default) {
        val pcmBuf = ShortArray(AUDIO_BUFFER_SIZE)
        while (isListening) {
            val read = audioRecord?.read(pcmBuf, 0, pcmBuf.size) ?: 0
            if (read > 0) {
                for (i in 0 until read) {
                    sampleBuffer[bufferWritePos] = pcmBuf[i].toFloat() / Short.MAX_VALUE
                    bufferWritePos = (bufferWritePos + 1) % BUFFER_SAMPLES
                    if (bufferWritePos == 0) bufferFilled = true
                }
            }
            delay(5)
        }
    }

    // ================================================================
    // Detection loop
    // ================================================================

    private suspend fun detectionLoop() = withContext(Dispatchers.Default) {
        // Wait until buffer has at least 1 second of audio
        while (isListening && !bufferFilled && bufferWritePos < SAMPLE_RATE) delay(100)

        while (isListening) {
            delay(CHECK_INTERVAL_MS)

            val now = System.currentTimeMillis()
            if (now - lastDetectionTime < COOLDOWN_MS) continue

            val template = templateFeatures ?: continue

            try {
                val snapshot     = getBufferSnapshot()
                val liveFeatures = extractMfccFrames(snapshot)
                if (liveFeatures.isEmpty()) continue

                val distance       = dtw(template, liveFeatures)
                val normalizedDist = distance / template.size.toFloat()

                Log.v(TAG, "DTW distance: ${"%.2f".format(normalizedDist)}  (threshold $DTW_MATCH_THRESHOLD)")

                if (normalizedDist < DTW_MATCH_THRESHOLD) {
                    lastDetectionTime = now
                    val confidence = (1f - normalizedDist / DTW_MATCH_THRESHOLD).coerceIn(0f, 1f)
                    Log.i(TAG, "Wake phrase detected! DTW=${"%.2f".format(normalizedDist)} confidence=${"%.2f".format(confidence)}")
                    withContext(Dispatchers.Main) {
                        callback.onWakeWordDetected(confidence, "custom_phrase")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "DTW detection error", e)
            }
        }
    }

    // ================================================================
    // Buffer helpers
    // ================================================================

    /** Return the most recent [SNAPSHOT_SAMPLES] samples from the circular buffer. */
    private fun getBufferSnapshot(): FloatArray {
        val available = if (bufferFilled) BUFFER_SAMPLES else bufferWritePos
        val take      = minOf(SNAPSHOT_SAMPLES, available)
        val result    = FloatArray(take)
        var readPos   = ((bufferWritePos - take) + BUFFER_SAMPLES) % BUFFER_SAMPLES
        for (i in 0 until take) {
            result[i] = sampleBuffer[readPos]
            readPos   = (readPos + 1) % BUFFER_SAMPLES
        }
        return result
    }

    // ================================================================
    // MFCC extraction
    // ================================================================

    /** Slice float PCM into overlapping frames, extract 13-dim MFCC per frame. */
    private fun extractMfccFrames(samples: FloatArray): Array<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start  = 0
        while (start + FRAME_SAMPLES <= samples.size) {
            val frame      = samples.copyOfRange(start, start + FRAME_SAMPLES)
            val mfccFrames = featureExtractor.extractMFCC(frame)
            if (mfccFrames.isNotEmpty()) {
                // Average sub-frames to get one vector per window
                val avg = FloatArray(AudioFeatureExtractor.N_MFCC) { i ->
                    mfccFrames.map { it[i] }.average().toFloat()
                }
                frames.add(avg)
            }
            start += HOP_SAMPLES
        }
        return frames.toTypedArray()
    }

    // ================================================================
    // DTW
    // ================================================================

    /**
     * Classic O(N×M) DTW between two MFCC sequences.
     * Returns the accumulated cost at the end of the optimal path.
     */
    private fun dtw(ref: Array<FloatArray>, query: Array<FloatArray>): Float {
        val n   = ref.size
        val m   = query.size
        val INF = Float.MAX_VALUE / 2f

        // Use two-row rolling DP to keep memory at O(M)
        var prev = FloatArray(m + 1) { INF }
        var curr = FloatArray(m + 1) { INF }
        prev[0] = 0f

        for (i in 1..n) {
            curr[0] = INF
            for (j in 1..m) {
                val cost = euclidean(ref[i - 1], query[j - 1])
                curr[j]  = cost + minOf(prev[j], curr[j - 1], prev[j - 1])
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[m]
    }

    private fun euclidean(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) { val d = a[i] - b[i]; sum += d * d }
        return sqrt(sum)
    }

    // ================================================================
    // Template loading  (decode 3gp → PCM → MFCC)
    // ================================================================

    private fun loadTemplateFeatures(path: String): Array<FloatArray>? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Template file missing or empty: $path")
            return null
        }
        val pcm = decodeAudioFileToPcm(path) ?: return null
        Log.i(TAG, "Decoded ${pcm.size} PCM samples from template")
        return extractMfccFrames(pcm)
    }

    /**
     * Decode any Android-supported audio file (3gp, amr, m4a, …) to 16-bit PCM float samples.
     * Uses [MediaExtractor] + [MediaCodec] — no third-party libs required.
     */
    private fun decodeAudioFileToPcm(path: String): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)

            // Find the first audio track
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt  = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format     = fmt
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track in template file")
                return null
            }
            extractor.selectTrack(trackIndex)

            val mime  = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info     = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            val pcmList  = mutableListOf<Float>()

            while (!outputEos) {
                // Feed compressed data into the codec
                if (!inputEos) {
                    val idx = codec.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val buf  = codec.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Pull decoded PCM
                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIdx >= 0) {
                    val buf     = codec.getOutputBuffer(outIdx)!!
                    val shorts  = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shorts.hasRemaining()) {
                        pcmList.add(shorts.get().toFloat() / Short.MAX_VALUE)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "Codec output format changed: ${codec.outputFormat}")
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            return if (pcmList.isEmpty()) null else pcmList.toFloatArray()

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding template audio", e)
            extractor.release()
            return null
        }
    }

    // ================================================================
    // AudioRecord setup
    // ================================================================

    private fun initializeAudioRecord() {
        val ar = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            AUDIO_BUFFER_SIZE
        )
        check(ar.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord did not initialise (state=${ar.state})"
        }
        audioRecord = ar
    }
}
