package com.liveapitester.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.liveapitester.http.ApiRequest
import com.liveapitester.http.ApiResponse
import com.liveapitester.settings.LiveApiTesterSettings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiService {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun explainError(request: ApiRequest, response: ApiResponse): String {
        val prompt = buildString {
            appendLine("I made an API request and got an error. Please explain the error and suggest how to fix it.")
            appendLine()
            appendLine("Request:")
            appendLine("  Method: ${request.method}")
            appendLine("  URL: ${request.url}")
            if (request.headers.isNotEmpty()) {
                val safeHeaders = request.headers.entries
                    .joinToString(", ") { (k, v) ->
                        if (k.lowercase() == "authorization") "$k: [REDACTED]" else "$k: $v"
                    }
                appendLine("  Headers: $safeHeaders")
            }
            if (!request.body.isNullOrBlank()) {
                appendLine("  Body: ${request.body.take(500)}")
            }
            appendLine()
            appendLine("Response:")
            appendLine("  Status: ${response.statusCode} ${response.statusText}")
            appendLine("  Response Time: ${response.responseTimeMs}ms")
            if (response.body.isNotBlank()) {
                appendLine("  Body: ${response.body.take(1000)}")
            }
            appendLine()
            appendLine("Please provide:")
            appendLine("1. What this error means")
            appendLine("2. Common causes")
            appendLine("3. How to fix it")
        }
        return callAiApi(prompt)
    }

    fun suggestTests(request: ApiRequest, response: ApiResponse): String {
        val prompt = buildString {
            appendLine("Generate comprehensive test cases for this API endpoint.")
            appendLine()
            appendLine("Endpoint:")
            appendLine("  Method: ${request.method}")
            appendLine("  URL: ${request.url}")
            if (request.headers.isNotEmpty()) {
                val safeHeaders = request.headers.entries
                    .filter { (k, _) -> k.lowercase() != "authorization" }
                    .joinToString(", ") { (k, v) -> "$k: $v" }
                if (safeHeaders.isNotBlank()) appendLine("  Headers: $safeHeaders")
            }
            if (!request.body.isNullOrBlank()) {
                appendLine("  Request Body: ${request.body.take(500)}")
            }
            appendLine()
            appendLine("Sample Response:")
            appendLine("  Status: ${response.statusCode} ${response.statusText}")
            if (response.body.isNotBlank()) {
                appendLine("  Body: ${response.body.take(500)}")
            }
            appendLine()
            appendLine("Generate test cases covering:")
            appendLine("1. Happy path / positive scenarios")
            appendLine("2. Negative scenarios (invalid input, missing fields)")
            appendLine("3. Edge cases (boundary values, empty data)")
            appendLine("4. Authentication/authorization tests")
            appendLine("5. Performance considerations")
        }
        return callAiApi(prompt)
    }

    fun generateRequestBody(url: String, method: String, context: String): String {
        val prompt = buildString {
            appendLine("Generate a sample JSON request body for this API endpoint.")
            appendLine()
            appendLine("Endpoint:")
            appendLine("  Method: $method")
            appendLine("  URL: $url")
            if (context.isNotBlank()) {
                appendLine("  Context: $context")
            }
            appendLine()
            appendLine("Please provide:")
            appendLine("1. A realistic sample JSON request body")
            appendLine("2. Brief explanation of each field")
            appendLine("3. Required vs optional fields")
        }
        return callAiApi(prompt)
    }

    fun analyzeDebugState(
        request: ApiRequest,
        response: ApiResponse?,
        stackTrace: String,
        variables: Map<String, String>,
        sourceContext: String
    ): String {
        val prompt = buildString {
            appendLine("You are debugging a backend API. A breakpoint was hit during this API request.")
            appendLine("Analyze the current state and identify potential issues.")
            appendLine()
            appendLine("Request:")
            appendLine("  Method: ${request.method}")
            appendLine("  URL: ${request.url}")
            if (!request.body.isNullOrBlank()) {
                appendLine("  Body: ${request.body.take(500)}")
            }
            if (response != null) {
                appendLine()
                appendLine("Response (so far):")
                appendLine("  Status: ${response.statusCode} ${response.statusText}")
                if (response.body.isNotBlank()) {
                    appendLine("  Body: ${response.body.take(500)}")
                }
            }
            if (stackTrace.isNotBlank()) {
                appendLine()
                appendLine("Stack Trace:")
                appendLine(stackTrace.take(2000))
            }
            if (variables.isNotEmpty()) {
                appendLine()
                appendLine("Current Variables:")
                variables.entries.take(20).forEach { (k, v) ->
                    appendLine("  $k = $v")
                }
            }
            if (sourceContext.isNotBlank()) {
                appendLine()
                appendLine("Source Code Context:")
                appendLine(sourceContext.take(1000))
            }
            appendLine()
            appendLine("Explain what's happening at this point in execution and if there are any bugs or issues.")
        }
        return callAiApi(prompt)
    }

    private fun callAiApi(prompt: String): String {
        val settings = LiveApiTesterSettings.getInstance()
        val apiKey = getApiKey()

        if (apiKey.isNullOrBlank()) {
            return "❌ GitHub Personal Access Token not configured. Please go to Settings > Tools > LiveApiTester and enter your GitHub PAT."
        }

        val requestBody = JsonObject().apply {
            addProperty("model", settings.aiModel)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to "You are a helpful API testing assistant. Provide clear, concise, and actionable responses."),
                mapOf("role" to "user", "content" to prompt)
            )))
            addProperty("temperature", 0.7)
            addProperty("max_tokens", 2000)
        }

        val request = Request.Builder()
            .url(settings.aiEndpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    "❌ GitHub Models API error (${response.code}): $responseBody"
                } else {
                    parseAiResponse(responseBody)
                }
            }
        } catch (e: IOException) {
            "❌ Network error calling GitHub Models API: ${e.message}"
        } catch (e: Exception) {
            "❌ Error: ${e.message}"
        }
    }

    private fun parseAiResponse(json: String): String {
        return try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)
            val choices = jsonObject.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val message = choices[0].asJsonObject
                    .getAsJsonObject("message")
                    .get("content")
                    .asString
                message
            } else {
                "No response content received from AI."
            }
        } catch (e: Exception) {
            "Error parsing AI response: ${e.message}\n\nRaw response: $json"
        }
    }

    private fun getApiKey(): String? {
        val credentialAttributes = CredentialAttributes(
            generateServiceName("LiveApiTester", "GITHUB_PAT")
        )
        return PasswordSafe.instance.getPassword(credentialAttributes)
    }

    companion object {
        fun saveApiKey(apiKey: String) {
            val credentialAttributes = CredentialAttributes(
                generateServiceName("LiveApiTester", "GITHUB_PAT")
            )
            PasswordSafe.instance.setPassword(credentialAttributes, apiKey)
        }
    }
}
