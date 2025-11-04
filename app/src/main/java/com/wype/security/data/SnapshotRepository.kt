package com.wype.security.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.wype.security.crypto.CryptoManager
import com.wype.security.net.SnapshotBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SnapshotRepository(private val ctx: Context, private val moshi: Moshi) {

    // TODO: replace with your real sources (Room, prefs, contacts you own)
    private suspend fun profileJson(): ByteArray = "{\"name\":\"Felicity\",\"email\":\"fizziekate@gmail.com\"}".toByteArray()
    private suspend fun buddiesJson(): ByteArray = "[{\"name\":\"Craig\",\"phone\":\"+61...\"}]".toByteArray()
    private suspend fun prefsJson(): ByteArray = "{\"sensitivity\":0.85,\"notify\":true}".toByteArray()

    suspend fun makeEncryptedBundle(dek: ByteArray): SnapshotBundle = withContext(Dispatchers.Default) {
        val prof = CryptoManager.encrypt(dek, profileJson())
        val budd = CryptoManager.encrypt(dek, buddiesJson())
        val pref = CryptoManager.encrypt(dek, prefsJson())

        SnapshotBundle(
            version = 1,
            profile = CryptoManager.b64(prof.iv + prof.ct),
            buddies = CryptoManager.b64(budd.iv + budd.ct),
            prefs   = CryptoManager.b64(pref.iv + pref.ct),
            meta = mapOf("ts" to System.currentTimeMillis())
        )
    }

    fun split(boxB64: String): Pair<ByteArray, ByteArray> {
        val b = CryptoManager.b64d(boxB64)
        val iv = b.copyOfRange(0, 12)
        val ct = b.copyOfRange(12, b.size)
        return iv to ct
    }

    suspend fun applyBundle(dek: ByteArray, b: SnapshotBundle) = withContext(Dispatchers.IO) {
        val (piv, pct) = split(b.profile)
        val (biv, bct) = split(b.buddies)
        val (fiv, fct) = split(b.prefs)
        val profile = CryptoManager.decrypt(dek, piv, pct)
        val buddies = CryptoManager.decrypt(dek, biv, bct)
        val prefs   = CryptoManager.decrypt(dek, fiv, fct)

        // TODO: write into your local DB / preferences
        // db.profile.upsert(profile)
        // db.buddies.replaceAll(buddies)
        // settings.apply(prefs)
        profile.size + buddies.size + prefs.size // touch to avoid unused warnings
    }
}
