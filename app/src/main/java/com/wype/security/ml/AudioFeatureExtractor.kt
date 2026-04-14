package com.wype.security.ml

import android.util.Log
import kotlin.math.*

/**
 * Lightweight audio feature extraction for wake word detection
 * Extracts MFCC (Mel-Frequency Cepstral Coefficients) features from audio
 */
class AudioFeatureExtractor {
    
    companion object {
        private const val TAG = "AudioFeatureExtractor"
        
        // Audio processing constants
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE = 512  // 32ms at 16kHz
        const val HOP_LENGTH = 256  // 16ms hop (50% overlap)
        const val N_MELS = 40       // Number of mel filter banks
        const val N_MFCC = 13       // Number of MFCC coefficients
        const val PREEMPHASIS = 0.97f
        
        // Wake word detection window
        const val WINDOW_DURATION_MS = 1000 // 1 second window
        const val WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_DURATION_MS / 1000
        const val FRAMES_PER_WINDOW = (WINDOW_SAMPLES / HOP_LENGTH) + 1
    }
    
    private val melFilterBank = createMelFilterBank()
    private val hannWindow = createHannWindow(FRAME_SIZE)
    
    /**
     * Extract MFCC features from audio samples
     */
    fun extractMFCC(audioSamples: FloatArray): Array<FloatArray> {
        // Apply pre-emphasis
        val preemphasized = applyPreemphasis(audioSamples)
        
        // Frame the audio
        val frames = frameAudio(preemphasized)
        
        // Extract features for each frame
        val mfccFeatures = Array(frames.size) { frameIndex ->
            val frame = frames[frameIndex]
            
            // Apply window function
            val windowedFrame = FloatArray(frame.size) { i ->
                frame[i] * hannWindow[i]
            }
            
            // Compute FFT
            val fft = computeFFT(windowedFrame)
            
            // Compute power spectrum
            val powerSpectrum = computePowerSpectrum(fft)
            
            // Apply mel filter bank
            val melEnergies = applyMelFilterBank(powerSpectrum)
            
            // Compute MFCC
            computeMFCC(melEnergies)
        }
        
        return mfccFeatures
    }
    
    /**
     * Extract features suitable for neural network input
     */
    fun extractFeatures(audioSamples: FloatArray): FloatArray {
        val mfccFeatures = extractMFCC(audioSamples)
        
        // Flatten MFCC features into a single array
        val flatFeatures = FloatArray(mfccFeatures.size * N_MFCC)
        var index = 0
        
        for (frame in mfccFeatures) {
            for (coefficient in frame) {
                flatFeatures[index++] = coefficient
            }
        }
        
        // Normalize features
        return normalizeFeatures(flatFeatures)
    }
    
    /**
     * Apply pre-emphasis filter to reduce noise
     */
    private fun applyPreemphasis(audioSamples: FloatArray): FloatArray {
        val result = FloatArray(audioSamples.size)
        result[0] = audioSamples[0]
        
        for (i in 1 until audioSamples.size) {
            result[i] = audioSamples[i] - PREEMPHASIS * audioSamples[i - 1]
        }
        
        return result
    }
    
    /**
     * Frame audio into overlapping windows
     */
    private fun frameAudio(audioSamples: FloatArray): Array<FloatArray> {
        val numFrames = maxOf(1, (audioSamples.size - FRAME_SIZE) / HOP_LENGTH + 1)
        val frames = Array(numFrames) { FloatArray(FRAME_SIZE) }
        
        for (i in 0 until numFrames) {
            val start = i * HOP_LENGTH
            val end = minOf(start + FRAME_SIZE, audioSamples.size)
            
            // Copy frame data
            System.arraycopy(audioSamples, start, frames[i], 0, end - start)
            
            // Zero-pad if necessary
            for (j in (end - start) until FRAME_SIZE) {
                frames[i][j] = 0f
            }
        }
        
        return frames
    }
    
    /**
     * Compute FFT using radix-2 Cooley-Tukey algorithm — O(n log n).
     * Frame size must be a power of 2 (FRAME_SIZE = 512 satisfies this).
     */
    private fun computeFFT(frame: FloatArray): Array<Complex> {
        val n = frame.size                          // 512 — power of 2
        val re = DoubleArray(n) { frame[it].toDouble() }
        val im = DoubleArray(n)                     // imaginary part starts at 0

        // ---- bit-reversal permutation ----
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }

        // ---- Cooley-Tukey butterfly ----
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val ang  = -2.0 * PI / len
            val wbR  = cos(ang)
            val wbI  = sin(ang)
            var s = 0
            while (s < n) {
                var wR = 1.0; var wI = 0.0
                for (k in 0 until half) {
                    val uR = re[s + k];          val uI = im[s + k]
                    val vR = re[s + k + half] * wR - im[s + k + half] * wI
                    val vI = re[s + k + half] * wI + im[s + k + half] * wR
                    re[s + k]        = uR + vR;  im[s + k]        = uI + vI
                    re[s + k + half] = uR - vR;  im[s + k + half] = uI - vI
                    val nwR = wR * wbR - wI * wbI
                    wI = wR * wbI + wI * wbR;    wR = nwR
                }
                s += len
            }
            len = len shl 1
        }

        // Return only the first n/2+1 bins (positive frequencies)
        return Array(n / 2 + 1) { i -> Complex(re[i], im[i]) }
    }
    
    /**
     * Compute power spectrum from FFT
     */
    private fun computePowerSpectrum(fft: Array<Complex>): FloatArray {
        return FloatArray(fft.size) { i ->
            (fft[i].real * fft[i].real + fft[i].imag * fft[i].imag).toFloat()
        }
    }
    
    /**
     * Apply mel filter bank to power spectrum
     */
    private fun applyMelFilterBank(powerSpectrum: FloatArray): FloatArray {
        val melEnergies = FloatArray(N_MELS)
        
        for (m in 0 until N_MELS) {
            var energy = 0f
            for (k in melFilterBank[m].indices) {
                energy += melFilterBank[m][k] * powerSpectrum[k]
            }
            melEnergies[m] = maxOf(energy, 1e-10f) // Prevent log(0)
        }
        
        return melEnergies
    }
    
    /**
     * Compute MFCC from mel energies using DCT
     */
    private fun computeMFCC(melEnergies: FloatArray): FloatArray {
        val mfcc = FloatArray(N_MFCC)
        
        // Take log of mel energies
        val logMelEnergies = FloatArray(melEnergies.size) { i ->
            ln(melEnergies[i])
        }
        
        // Apply DCT
        for (i in 0 until N_MFCC) {
            var sum = 0.0
            for (j in logMelEnergies.indices) {
                sum += logMelEnergies[j] * cos(PI * i * (j + 0.5) / N_MELS)
            }
            mfcc[i] = sum.toFloat()
        }
        
        return mfcc
    }
    
    /**
     * Create Hann window
     */
    private fun createHannWindow(size: Int): FloatArray {
        return FloatArray(size) { n ->
            (0.5 * (1 - cos(2 * PI * n / (size - 1)))).toFloat()
        }
    }
    
    /**
     * Create mel filter bank
     */
    private fun createMelFilterBank(): Array<FloatArray> {
        val filterBank = Array(N_MELS) { FloatArray(FRAME_SIZE / 2 + 1) }
        
        // Create mel-spaced filter centers
        val melMin = hzToMel(0f)
        val melMax = hzToMel(SAMPLE_RATE / 2f)
        val melPoints = FloatArray(N_MELS + 2) { i ->
            melMin + i * (melMax - melMin) / (N_MELS + 1)
        }
        
        // Convert back to Hz and then to FFT bins
        val hzPoints = FloatArray(melPoints.size) { i ->
            melToHz(melPoints[i])
        }
        val binPoints = FloatArray(hzPoints.size) { i ->
            (hzPoints[i] * FRAME_SIZE / SAMPLE_RATE).roundToInt().toFloat()
        }
        
        // Create triangular filters
        for (m in 1..N_MELS) {
            val left = binPoints[m - 1].toInt()
            val center = binPoints[m].toInt()
            val right = binPoints[m + 1].toInt()
            
            // Left slope
            for (k in left until center) {
                if (k >= 0 && k < filterBank[m - 1].size) {
                    filterBank[m - 1][k] = (k - left).toFloat() / (center - left)
                }
            }
            
            // Right slope
            for (k in center until right) {
                if (k >= 0 && k < filterBank[m - 1].size) {
                    filterBank[m - 1][k] = (right - k).toFloat() / (right - center)
                }
            }
        }
        
        return filterBank
    }
    
    /**
     * Convert Hz to Mel scale
     */
    private fun hzToMel(hz: Float): Float {
        return 2595f * log10(1f + hz / 700f)
    }
    
    /**
     * Convert Mel to Hz scale
     */
    private fun melToHz(mel: Float): Float {
        return 700f * (10f.pow(mel / 2595f) - 1f)
    }
    
    /**
     * Normalize features to zero mean and unit variance
     */
    private fun normalizeFeatures(features: FloatArray): FloatArray {
        val mean = features.average().toFloat()
        val variance = features.map { (it - mean) * (it - mean) }.average().toFloat()
        val std = sqrt(variance + 1e-8f) // Add small epsilon to prevent division by zero
        
        return FloatArray(features.size) { i ->
            (features[i] - mean) / std
        }
    }
    
    /**
     * Simple complex number class
     */
    data class Complex(val real: Double, val imag: Double)
}
