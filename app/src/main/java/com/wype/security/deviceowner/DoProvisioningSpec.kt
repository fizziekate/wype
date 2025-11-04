package com.wype.security.deviceowner

data class DoProvisioningSpec(
    val adminComponentName: String,
    val adminPackageDownloadUrl: String? = null,
    val adminPackageChecksumBase64: String? = null,
    val leaveAllSystemAppsEnabled: Boolean = true,
    val enrollmentToken: String? = null,
    val extras: Map<String, String> = emptyMap()
)
