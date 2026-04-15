package com.liveapitester.collections

import com.liveapitester.http.ApiRequest

data class ApiCollection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Collection",
    val description: String = "",
    val requests: MutableList<SavedRequest> = mutableListOf()
)

data class SavedRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Request",
    val request: ApiRequest = ApiRequest()
)
