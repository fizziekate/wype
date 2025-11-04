package com.wype.security.ui.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.wype.security.R
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val barWidth = 8f
    private val barSpacing = 4f
    private val maxBarHeight = 120f
    private val minBarHeight = 8f
    private val cornerRadius = 4f
    
    private var isRecording = false
    private var animationOffset = 0f
    private val animationSpeed = 0.15f
    
    // Animation handler and runnable for proper control
    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null
    
    private val staticAmplitudes = floatArrayOf(
        0.3f, 0.6f, 0.4f, 0.8f, 0.5f, 0.7f, 0.3f, 0.9f,
        0.4f, 0.6f, 0.8f, 0.2f, 0.7f, 0.5f, 0.9f, 0.3f,
        0.6f, 0.4f, 0.8f, 0.5f, 0.2f, 0.7f, 0.6f, 0.9f,
        0.4f, 0.3f, 0.8f, 0.5f, 0.6f, 0.7f, 0.2f, 0.9f
    )
    
    private var numberOfBars = 0
    
    init {
        paint.color = ContextCompat.getColor(context, R.color.waveform_active)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        numberOfBars = ((w - paddingLeft - paddingRight) / (barWidth + barSpacing)).toInt()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (numberOfBars == 0) return
        
        val centerY = height / 2f
        val startX = paddingLeft + (width - paddingLeft - paddingRight - 
                     (numberOfBars * (barWidth + barSpacing) - barSpacing)) / 2f
        
        for (i in 0 until numberOfBars) {
            val x = startX + i * (barWidth + barSpacing)
            val amplitude = getAmplitudeForBar(i)
            val barHeight = minBarHeight + amplitude * (maxBarHeight - minBarHeight)
            
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            
            // Set color based on recording state and amplitude
            paint.color = if (isRecording) {
                if (amplitude > 0.6f) {
                    ContextCompat.getColor(context, R.color.waveform_high)
                } else if (amplitude > 0.3f) {
                    ContextCompat.getColor(context, R.color.waveform_medium)
                } else {
                    ContextCompat.getColor(context, R.color.waveform_low)
                }
            } else {
                ContextCompat.getColor(context, R.color.waveform_inactive)
            }
            
            val rectF = RectF(x, top, x + barWidth, bottom)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        }
        
        // Continue animation if recording - using Handler for proper control
        if (isRecording) {
            animationOffset += animationSpeed
            scheduleNextFrame()
        }
    }
    
    private fun getAmplitudeForBar(barIndex: Int): Float {
        return if (isRecording) {
            // Animated waveform with some randomness and wave pattern
            val baseAmplitude = staticAmplitudes[barIndex % staticAmplitudes.size]
            val wave = sin(animationOffset + barIndex * 0.3).toFloat()
            val randomFactor = Random.nextFloat() * 0.3f + 0.7f // 0.7 to 1.0
            ((baseAmplitude + wave * 0.3f) * randomFactor).coerceIn(0f, 1f)
        } else {
            // Static waveform
            staticAmplitudes[barIndex % staticAmplitudes.size] * 0.5f
        }
    }
    
    private fun scheduleNextFrame() {
        if (animationRunnable == null) {
            animationRunnable = Runnable {
                if (isRecording) {
                    invalidate()
                }
                animationRunnable = null
            }
        }
        handler.postDelayed(animationRunnable!!, 50) // ~20 FPS
    }
    
    private fun stopAnimation() {
        animationRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("WaveformView", "Animation runnable removed from handler")
        }
        animationRunnable = null
        Log.d("WaveformView", "Animation runnable set to null")
    }
    
    fun setRecording(recording: Boolean) {
        Log.d("WaveformView", "setRecording called: $recording (current: $isRecording)")
        if (isRecording != recording) {
            isRecording = recording
            if (recording) {
                animationOffset = 0f
                Log.d("WaveformView", "Starting waveform animation")
                // First frame will be triggered by onDraw
            } else {
                // Stop animation immediately
                stopAnimation()
                Log.d("WaveformView", "Stopping waveform animation")
            }
            invalidate()
            Log.d("WaveformView", "Waveform invalidated, isRecording now: $isRecording")
        } else {
            Log.d("WaveformView", "setRecording ignored - no state change")
        }
    }
    
    fun forceStop() {
        Log.d("WaveformView", "forceStop called")
        isRecording = false
        stopAnimation()
        invalidate()
        Log.d("WaveformView", "Force stop completed")
    }
    
    fun isRecording(): Boolean = isRecording
}
