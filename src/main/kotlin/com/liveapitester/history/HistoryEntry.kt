package com.liveapitester.history

import com.liveapitester.http.ApiRequest

data class HistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val request: ApiRequest,
    val statusCode: Int,
    val statusText: String,
    val responseTimeMs: Long,
    val responseSizeBytes: Long,
    val responseBody: String = ""
)
