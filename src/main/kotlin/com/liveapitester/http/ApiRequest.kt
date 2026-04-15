package com.liveapitester.http

data class ApiRequest(
    val method: HttpMethod = HttpMethod.GET,
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val body: String? = null,
    val authType: String = "None",
    val authCredentials: Map<String, String> = emptyMap(),
    val contentType: String = "application/json"
)
