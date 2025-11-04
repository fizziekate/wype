# Porcupine Wake Word Models

This directory should contain custom Porcupine wake word model files (.ppn) for your app.

## Built-in Wake Words (No files needed)
The following wake words are built into Porcupine and don't require model files:
- picovoice (default)
- bumblebee
- computer
- hey google
- hey siri
- jarvis
- alexa
- americano
- blueberry
- terminator

## Custom Wake Word Models
To add custom wake word models:

1. **Train a custom wake word** at https://console.picovoice.ai/
2. **Download the Android model file** (`.ppn` file)
3. **Name the file**: `{wake_word}_android.ppn` (e.g., `emergency_android.ppn`)
4. **Place it in this directory**

## Usage
- The app will automatically detect and use custom models if available
- If a custom model is not found, it will fall back to built-in wake words
- Default wake word is "picovoice" if nothing else is configured

## Example Files (if you had them):
- `emergency_android.ppn` - Custom "emergency" wake word
- `help_android.ppn` - Custom "help" wake word
- `wype_android.ppn` - Custom "wype" wake word

**Note**: Custom wake word models require a Porcupine access key from Picovoice Console.
