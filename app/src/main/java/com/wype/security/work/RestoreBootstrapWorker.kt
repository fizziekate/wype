package com.wype.security.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wype.security.crypto.CryptoManager
import com.wype.security.data.SnapshotRepository
import com.wype.security.net.*
import com.wype.security.storage.LocalDekStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import androidx.work.workDataOf
import com.wype.security.BuildConfig

class RestoreBootstrapWorker(
    ctx: Context, params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val code = inputData.getString("code") ?: return Result.failure()

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
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
            setProgress(workDataOf("status" to "Exchanging code…"))
            val exch = api.bootstrapExchange(
                BootstrapExchangeReq(
                    code = code,
                    deviceInfo = mapOf(
                        "brand" to android.os.Build.BRAND,
                        "model" to android.os.Build.MODEL,
                        "android" to android.os.Build.VERSION.RELEASE
                    )
                )
            )
            val bearer = "Bearer ${exch.bootstrapToken}"

            setProgress(workDataOf("status" to "Downloading snapshot…"))
            val snap = api.getSnapshot(bearer)
            val wrapped = snap.wrappedDek
                ?: return Result.retry() // Server should include wrappedDek to avoid extra lookups

            setProgress(workDataOf("status" to "Unwrapping key…"))
            val unwrap = api.unwrap(UnwrapReq(snap.kid, wrapped), bearer)
            val dek = CryptoManager.b64d(unwrap.dek)

            setProgress(workDataOf("status" to "Applying data…"))
            val repo = SnapshotRepository(applicationContext, moshi)
            repo.applyBundle(dek, snap.bundle)

            // Cache for future backups
            val store = LocalDekStore(applicationContext)
            store.saveDek(dek)
            store.saveWrapped(snap.kid, wrapped)

            setProgress(workDataOf("status" to "Done"))
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
