package com.wype.security.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wype.security.auth.TokenProvider
import com.wype.security.crypto.CryptoManager
import com.wype.security.data.SnapshotRepository
import com.wype.security.net.*
import com.wype.security.storage.LocalDekStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.wype.security.BuildConfig

class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val repo  = SnapshotRepository(applicationContext, moshi)
        val store = LocalDekStore(applicationContext)
        val tokenProvider = TokenProvider()

        val ok = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(ok)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WypeApi::class.java)

        return try {
            var wrapped = store.loadWrapped()
            var dek = store.loadDek()

            if (wrapped == null && dek == null) {
                // First time: create and wrap DEK
                val newDek = CryptoManager.newDek()
                val wrapRes = api.wrap(WrapReq(CryptoManager.b64(newDek)))
                store.saveDek(newDek)
                store.saveWrapped(wrapRes.kid, wrapRes.wrappedDek)
                wrapped = wrapRes.kid to wrapRes.wrappedDek
                dek = newDek
            } else if (wrapped != null && dek == null) {
                // Need to unwrap to recover raw DEK
                val bearer = tokenProvider.bearer()
                val unwrap = api.unwrap(UnwrapReq(wrapped.first, wrapped.second), bearer)
                dek = CryptoManager.b64d(unwrap.dek)
                store.saveDek(dek)
            }

            val currentDek = dek ?: return Result.retry()
            val (kid, _) = wrapped ?: return Result.retry()

            val bundle = repo.makeEncryptedBundle(currentDek)
            val jwt = tokenProvider.bearer()
            api.putSnapshot(SnapshotUp(kid, bundle), jwt)

            Result.success()
        } catch (e: Exception) {
            // Avoid logging secrets; print minimal error for diagnostics
            e.printStackTrace()
            Result.retry()
        }
    }
}
