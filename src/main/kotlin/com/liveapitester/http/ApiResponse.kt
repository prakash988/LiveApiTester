package com.liveapitester.http

data class ApiResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val body: String,
    val responseTimeMs: Long,
    val responseSizeBytes: Long,
    val timestamp: Long = System.currentTimeMillis()
)
