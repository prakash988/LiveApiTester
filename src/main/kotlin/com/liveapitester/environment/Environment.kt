package com.liveapitester.environment

data class Environment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Environment",
    val variables: MutableMap<String, String> = mutableMapOf()
)
