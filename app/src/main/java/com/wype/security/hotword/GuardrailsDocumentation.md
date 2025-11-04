# Wype Hotword False Positive Guardrails & Silence System

## Overview
Comprehensive multi-layer false positive prevention and complete silence enforcement for the Wype emergency hotword detection system.

## 🛡️ False Positive Prevention Layers

### Layer 1: Engine-Level Guardrails (PorcupineHotword.kt)
```kotlin
// Dead-time gate: 300-500ms variable delay
private const val DEAD_TIME_MIN_MS = 300L
private const val DEAD_TIME_MAX_MS = 500L

// Audio energy validation (VAD)
private const val VAD_RMS_THRESHOLD = 0.02f

// Media volume interference prevention  
private const val MEDIA_VOLUME_THRESHOLD = 0.6f

// Confidence threshold for acceptance
private const val MIN_CONFIDENCE_THRESHOLD = 0.75f
```

**Engine Guardrail Checks:**
1. **Dead-time Gate**: 300-500ms random delay after any detection
2. **Confidence Threshold**: Only accept detections >75% confidence
3. **Media Volume Check**: Block if music/alarm >60% volume
4. **VAD Check**: Ensure sufficient audio energy (RMS >0.02)

### Layer 2: Service-Level Guardrails (WypeHotwordService.kt)
```kotlin
// Extended service-level dead time
private const val SERVICE_LEVEL_DEAD_TIME_MS = 2000L

// Rate limiting protection
private const val MAX_DETECTIONS_PER_MINUTE = 3

// Stricter media volume threshold
private const val MEDIA_VOLUME_THRESHOLD = 0.7f
```

**Service Guardrail Checks:**
1. **Extended Dead-time**: 2-second service-level dead time
2. **Rate Limiting**: Maximum 3 detections per 60-second window
3. **Stricter Media Volume**: Block if any stream >70% volume
4. **Protection Mode Validation**: Emergency phrases only when armed

### Layer 3: AlertGate System (AlertGate.kt)
```kotlin
// Global alert cooldown period
private const val COOLDOWN_DURATION_MS = 3 * 60 * 1000L // 3 minutes

// Thread-safe atomic gate control
private val lastAlertTime = AtomicLong(0L)
```

**AlertGate Protection:**
- **3-Minute Global Cooldown**: Only one alert per 3 minutes system-wide
- **Thread-Safe**: Atomic operations prevent race conditions
- **Single-Fire**: Once triggered, blocks all alerts for cooldown period

## 🤫 Complete Silence Enforcement

### Silent Notification Channel
```kotlin
// WypeApp.kt - IMPORTANCE_LOW channel
val channel = NotificationChannel(
    WYPE_SILENT_CHANNEL_ID,
    CHANNEL_NAME,
    NotificationManager.IMPORTANCE_LOW
).apply {
    setSound(null, null)        // No sound
    enableVibration(false)      // No vibration  
    enableLights(false)         // No LED
    setShowBadge(false)         // No app badge
    lockscreenVisibility = VISIBILITY_SECRET  // Hidden from lockscreen
}
```

### Silent Service Notification
```kotlin
// WypeHotwordService.kt - Completely invisible notification
NotificationCompat.Builder(this, WYPE_SILENT_CHANNEL_ID)
    .setContentTitle("")                    // Empty title
    .setContentText("")                     // Empty text
    .setPriority(PRIORITY_MIN)              // Lowest priority
    .setVisibility(VISIBILITY_SECRET)       // Hidden from lockscreen
    .setSilent(true)                        // Force silent
    .setVibrate(null)                       // No vibration
    .setSound(null)                         // No sound
    .setDefaults(0)                         // No defaults
```

### No User-Facing Alerts
- ❌ **No Toast Messages**: All user-facing toasts removed from hotword detection
- ❌ **No Dialogs**: No alert dialogs during detection or processing
- ❌ **No Sounds**: No beeps, chimes, or audio feedback
- ❌ **No Vibrations**: No haptic feedback whatsoever
- ❌ **No Visual Indicators**: No LED lights or screen wake
- ❌ **No Lock Screen**: Hidden from all lock screen notifications

## 📊 Guardrail Flow Diagram

```
Hotword Detection
       ↓
Engine Layer Checks:
 ├── Dead-time (300-500ms) ❌ → BLOCKED
 ├── Confidence (<75%)     ❌ → BLOCKED
 ├── Media Volume (>60%)   ❌ → BLOCKED
 ├── VAD Energy (<0.02)    ❌ → BLOCKED
 └── All Pass             ✅ → Continue
       ↓
Service Layer Checks:
 ├── Dead-time (2000ms)    ❌ → BLOCKED
 ├── Rate Limit (>3/min)   ❌ → BLOCKED
 ├── Media Volume (>70%)   ❌ → BLOCKED
 ├── Protection Mode       ❌ → BLOCKED (emergency only)
 └── All Pass             ✅ → Continue
       ↓
AlertGate Check:
 ├── Cooldown Active       ❌ → BLOCKED
 └── Gate Open            ✅ → Continue
       ↓
Silent Alert Sent
(No user notification)
```

## 🔧 Configuration Parameters

### Dead-Time Randomization
```kotlin
// Prevents predictable timing attacks
val randomDelay = DEAD_TIME_MIN_MS + 
    (Math.random() * (DEAD_TIME_MAX_MS - DEAD_TIME_MIN_MS))
```

### Media Volume Streams Monitored
```kotlin
// Multiple audio streams checked
STREAM_MUSIC         // Music/media playback
STREAM_ALARM         // Alarm clock
STREAM_RING          // Phone ringtone  
STREAM_NOTIFICATION  // System notifications
```

### Rate Limiting Window
```kotlin
// Sliding 60-second window with reset
if (currentTime - detectionWindowStartTime > 60000) {
    detectionCount = 0  // Reset counter
    detectionWindowStartTime = currentTime
}
```

## 🚨 Emergency vs Arming Detection

### Arming Phrases (Always Processed)
- Process regardless of protection mode state
- **Silently** arms protection mode
- No user notification of arming
- Only debug logs generated

### Emergency Phrases (Protection-Mode Gated)
- Only processed when protection mode is **ARMED**
- Triggers silent alert worker if all guardrails pass
- No user notification of emergency detection
- Alert sent completely silently

## 📝 Logging Strategy

### Debug Logs Only
```kotlin
Log.d(TAG, "Guardrail BLOCKED: Dead-time gate")       // Debug
Log.v(TAG, "VAD check failed: RMS too low")           // Verbose
Log.w(TAG, "🚨 EMERGENCY PHRASE DETECTED (SILENT)")   // Warning (dev)
```

### No User-Visible Messages
```kotlin
// ❌ NEVER USE THESE in hotword detection:
Toast.makeText(...)                    // NO TOASTS
AlertDialog.Builder(...)               // NO DIALOGS  
NotificationManagerCompat.notify(...)  // NO NOTIFICATIONS
MediaPlayer.play(...)                  // NO SOUNDS
Vibrator.vibrate(...)                  // NO VIBRATIONS
```

## ✅ Acceptance Criteria Validation

### Dead-Time Implementation
- ✅ **300-500ms dead time**: Variable delay implemented
- ✅ **Simple VAD gate**: RMS energy check implemented
- ✅ **Atomic operations**: Thread-safe guardrails

### Media Volume Integration  
- ✅ **AudioManager usage**: System volume monitoring
- ✅ **Multiple stream check**: Music, alarm, ring, notification
- ✅ **Configurable threshold**: 60% engine, 70% service levels

### Complete Silence
- ✅ **No notification channels**: Only IMPORTANCE_LOW silent channel
- ✅ **No Toasts anywhere**: Removed from all detection paths
- ✅ **Silent operation**: All user feedback eliminated

## 🧪 Testing Guardrails

### Simulation Methods
```kotlin
// Test dead-time effectiveness
PorcupineHotword.simulateRapidDetections()

// Test media volume blocking
AudioManager.setStreamVolume(STREAM_MUSIC, maxVolume * 0.8)

// Test rate limiting
WypeHotwordService.simulateSpamDetections()

// Test AlertGate cooldown
AlertGate.testCooldownBehavior()
```

### Expected Behavior
1. **Rapid detections**: Only first detection processed
2. **High media volume**: All detections blocked
3. **Spam protection**: Max 3 detections per minute
4. **Global cooldown**: 3-minute system-wide protection
5. **Complete silence**: No user-visible feedback ever

## 🔒 Security & Privacy

### Stealth Operation
- **No indication** when protection arms
- **No indication** when emergency detected  
- **No indication** when alerts blocked
- **Debug logs only** for development

### Data Protection
- **No audio stored**: Real-time processing only
- **No detection history**: Stateless guardrails
- **Minimal logging**: Essential debugging only
- **Local processing**: No cloud detection calls

## 📈 Performance Impact

### Computational Overhead
- **Minimal CPU**: Simple threshold checks
- **Low memory**: Atomic variables only
- **No I/O blocking**: Async audio processing
- **Battery efficient**: Passive monitoring

### Real-Time Requirements
- **Sub-millisecond checks**: Fast guardrail evaluation
- **Non-blocking**: Never delays audio processing
- **Thread-safe**: Concurrent access protected

This multi-layer guardrail system ensures maximum false positive protection while maintaining complete stealth operation, meeting all acceptance criteria for a production emergency detection system.
