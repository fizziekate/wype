package com.wype.security.net

import com.squareup.moshi.JsonClass
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class WrapReq(val dek: String)
@JsonClass(generateAdapter = true)
data class WrapRes(val kid: String, val wrappedDek: String)

@JsonClass(generateAdapter = true)
data class UnwrapReq(val kid: String, val wrappedDek: String)
@JsonClass(generateAdapter = true)
data class UnwrapRes(val dek: String)

@JsonClass(generateAdapter = true)
data class SnapshotBundle(
    val version: Int = 1,
    val profile: String,
    val buddies: String,
    val prefs: String,
    val meta: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class SnapshotUp(val kid: String, val bundle: SnapshotBundle)

// Include wrappedDek optionally to support restore without extra lookup
@JsonClass(generateAdapter = true)
data class SnapshotDown(val kid: String, val wrappedDek: String? = null, val bundle: SnapshotBundle)

@JsonClass(generateAdapter = true)
data class BootstrapInitReq(val ttlSeconds: Int, val deviceHint: String?)
@JsonClass(generateAdapter = true)
data class BootstrapInitRes(val code: String, val expiresAt: String)

@JsonClass(generateAdapter = true)
data class BootstrapExchangeReq(
    val code: String,
    val deviceInfo: Map<String, String>
)
@JsonClass(generateAdapter = true)
data class BootstrapExchangeRes(val bootstrapToken: String, val expiresIn: Int)

interface WypeApi {
    @POST("/v1/keys/wrap") suspend fun wrap(@Body req: WrapReq): WrapRes
    @POST("/v1/keys/unwrap") suspend fun unwrap(@Body req: UnwrapReq, @Header("Authorization") auth: String): UnwrapRes

    @PUT("/v1/snapshots/current") suspend fun putSnapshot(@Body up: SnapshotUp, @Header("Authorization") auth: String)
    @GET("/v1/snapshots/current") suspend fun getSnapshot(@Header("Authorization") auth: String): SnapshotDown

    @POST("/v1/bootstrap/init") suspend fun bootstrapInit(@Body req: BootstrapInitReq, @Header("Authorization") auth: String): BootstrapInitRes
    @POST("/v1/bootstrap/exchange") suspend fun bootstrapExchange(@Body req: BootstrapExchangeReq): BootstrapExchangeRes
}
