package com.wype.security.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Manages local wake word model (.ppn) files for Porcupine
 * Supports both built-in keywords and custom models
 */
class WakeWordModelManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WakeWordModelManager"
        private const val MODELS_DIR = "wake_word_models"
    }
    
    private val modelsDirectory: File by lazy {
        File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Available wake word types
     */
    enum class WakeWordType {
        BUILT_IN,    // Uses Porcupine built-in keywords
        CUSTOM       // Uses custom .ppn model files
    }
    
    /**
     * Wake word configuration data class
     */
    data class WakeWordConfig(
        val name: String,
        val displayName: String,
        val type: WakeWordType,
        val filePath: String? = null // Only for custom models
    )
    
    /**
     * Get all available wake word configurations
     */
    fun getAvailableWakeWords(): List<WakeWordConfig> {
        val configs = mutableListOf<WakeWordConfig>()
        
        // Add built-in wake words
        val builtInWords = listOf(
            "picovoice" to "Picovoice",
            "alexa" to "Alexa", 
            "americano" to "Americano",
            "blueberry" to "Blueberry",
            "bumblebee" to "Bumblebee",
            "computer" to "Computer",
            "grapefruit" to "Grapefruit", 
            "grasshopper" to "Grasshopper",
            "hey google" to "Hey Google",
            "hey siri" to "Hey Siri",
            "jarvis" to "Jarvis",
            "ok google" to "OK Google",
            "porcupine" to "Porcupine",
            "terminator" to "Terminator"
        )
        
        builtInWords.forEach { (name, displayName) ->
            configs.add(WakeWordConfig(name, displayName, WakeWordType.BUILT_IN))
        }
        
        // Add custom models from assets and local storage
        configs.addAll(getCustomWakeWords())
        
        return configs
    }
    
    /**
     * Get custom wake word models from local storage
     */
    private fun getCustomWakeWords(): List<WakeWordConfig> {
        val customWords = mutableListOf<WakeWordConfig>()
        
        try {
            // Check assets folder for bundled custom models
            val assetFiles = context.assets.list("wake_word_models") ?: emptyArray()
            assetFiles.filter { it.endsWith(".ppn") }.forEach { fileName ->
                val name = fileName.removeSuffix(".ppn")
                val displayName = name.capitalize()
                customWords.add(WakeWordConfig(
                    name = name,
                    displayName = displayName,
                    type = WakeWordType.CUSTOM,
                    filePath = "assets://$fileName"
                ))
            }
        } catch (e: IOException) {
            Log.d(TAG, "No custom models found in assets")
        }
        
        // Check local files directory
        if (modelsDirectory.exists()) {
            modelsDirectory.listFiles()?.filter { it.name.endsWith(".ppn") }?.forEach { file ->
                val name = file.nameWithoutExtension
                val displayName = name.capitalize()
                customWords.add(WakeWordConfig(
                    name = name,
                    displayName = displayName,
                    type = WakeWordType.CUSTOM,
                    filePath = file.absolutePath
                ))
            }
        }
        
        return customWords
    }
    
    /**
     * Copy custom model from assets to local storage
     */
    fun copyAssetModelToLocal(assetFileName: String): String? {
        try {
            val localFile = File(modelsDirectory, assetFileName)
            
            context.assets.open("wake_word_models/$assetFileName").use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Copied model $assetFileName to local storage")
            return localFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset model $assetFileName", e)
            return null
        }
    }
    
    /**
     * Get the file path for a wake word config
     */
    fun getModelFilePath(config: WakeWordConfig): String? {
        return when (config.type) {
            WakeWordType.BUILT_IN -> null // Built-in doesn't need file path
            WakeWordType.CUSTOM -> {
                if (config.filePath?.startsWith("assets://") == true) {
                    // Copy from assets to local storage first
                    val assetFileName = config.filePath.removePrefix("assets://")
                    copyAssetModelToLocal(assetFileName)
                } else {
                    config.filePath
                }
            }
        }
    }
    
    /**
     * Download and save a custom wake word model
     */
    fun saveCustomModel(name: String, modelData: ByteArray): String? {
        try {
            val fileName = "${name}.ppn"
            val localFile = File(modelsDirectory, fileName)
            
            FileOutputStream(localFile).use { output ->
                output.write(modelData)
            }
            
            Log.i(TAG, "Saved custom model: $fileName")
            return localFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom model $name", e)
            return null
        }
    }
    
    /**
     * Delete a custom wake word model
     */
    fun deleteCustomModel(name: String): Boolean {
        try {
            val fileName = "${name}.ppn"
            val localFile = File(modelsDirectory, fileName)
            
            if (localFile.exists() && localFile.delete()) {
                Log.i(TAG, "Deleted custom model: $fileName")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete custom model $name", e)
        }
        return false
    }
    
    /**
     * Check if a model file exists and is valid
     */
    fun isModelValid(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.exists() && file.length() > 0 && file.canRead()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get model file size in bytes
     */
    fun getModelSize(filePath: String): Long {
        return try {
            File(filePath).length()
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * Extension function to capitalize first letter
 */
private fun String.capitalize(): String {
    return if (isEmpty()) this else this[0].uppercaseChar() + this.substring(1)
}
