package com.liveapitester.http

import com.liveapitester.settings.LiveApiTesterSettings
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class HttpExecutor {

    @Volatile
    private var currentCall: Call? = null

    fun cancel() {
        currentCall?.cancel()
    }

    fun execute(
        request: ApiRequest,
        settings: LiveApiTesterSettings,
        envVars: Map<String, String> = emptyMap()
    ): ApiResponse {
        val resolvedRequest = resolveVariables(request, envVars)
        val client = buildClient(settings)
        val okRequest = buildOkHttpRequest(resolvedRequest)

        val call = client.newCall(okRequest)
        currentCall = call

        val startTime = System.currentTimeMillis()
        call.execute().use { response ->
            val responseTimeMs = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""
            val responseSizeBytes = responseBody.toByteArray().size.toLong()

            val headers = mutableMapOf<String, String>()
            for (i in 0 until response.headers.size) {
                headers[response.headers.name(i)] = response.headers.value(i)
            }

            return ApiResponse(
                statusCode = response.code,
                statusText = response.message,
                headers = headers,
                body = responseBody,
                responseTimeMs = responseTimeMs,
                responseSizeBytes = responseSizeBytes
            )
        }
    }

    private fun resolveVariables(request: ApiRequest, envVars: Map<String, String>): ApiRequest {
        fun interpolate(text: String?): String? {
            if (text == null) return null
            var result = text
            for ((key, value) in envVars) {
                result = result.replace("{{$key}}", value)
            }
            return result
        }

        return request.copy(
            url = interpolate(request.url) ?: request.url,
            headers = request.headers.mapValues { (_, v) -> interpolate(v) ?: v },
            queryParams = request.queryParams.mapValues { (_, v) -> interpolate(v) ?: v },
            body = interpolate(request.body)
        )
    }

    private fun buildClient(settings: LiveApiTesterSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .followRedirects(settings.followRedirects)
            .followSslRedirects(settings.followRedirects)

        if (settings.sslTrustAll) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    private fun buildOkHttpRequest(request: ApiRequest): Request {
        val rawUrl = request.url.trim()
        val baseUrl = rawUrl.toHttpUrlOrNull()
            ?: "http://$rawUrl".toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL: ${request.url}")

        val urlBuilder = baseUrl.newBuilder()

        for ((key, value) in request.queryParams) {
            urlBuilder.addQueryParameter(key, value)
        }

        val okRequestBuilder = Request.Builder().url(urlBuilder.build())

        applyAuth(okRequestBuilder, request)

        for ((key, value) in request.headers) {
            if (key.isNotBlank()) okRequestBuilder.header(key, value)
        }

        val body = buildRequestBody(request)
        when (request.method) {
            HttpMethod.GET -> okRequestBuilder.get()
            HttpMethod.POST -> okRequestBuilder.post(body ?: "".toRequestBody(null))
            HttpMethod.PUT -> okRequestBuilder.put(body ?: "".toRequestBody(null))
            HttpMethod.DELETE -> if (body != null) okRequestBuilder.delete(body) else okRequestBuilder.delete()
            HttpMethod.PATCH -> okRequestBuilder.patch(body ?: "".toRequestBody(null))
            HttpMethod.HEAD -> okRequestBuilder.head()
            HttpMethod.OPTIONS -> okRequestBuilder.method("OPTIONS", body)
        }

        return okRequestBuilder.build()
    }

    private fun applyAuth(builder: Request.Builder, request: ApiRequest) {
        when (request.authType) {
            "Bearer Token" -> {
                val token = request.authCredentials["token"] ?: ""
                if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            }
            "Basic Auth" -> {
                val username = request.authCredentials["username"] ?: ""
                val password = request.authCredentials["password"] ?: ""
                if (username.isNotBlank()) {
                    val credential = Credentials.basic(username, password)
                    builder.header("Authorization", credential)
                }
            }
            "API Key" -> {
                val key = request.authCredentials["key"] ?: ""
                val value = request.authCredentials["value"] ?: ""
                val addTo = request.authCredentials["addTo"] ?: "Header"
                if (key.isNotBlank() && addTo == "Header") {
                    builder.header(key, value)
                }
            }
        }
    }

    private fun buildRequestBody(request: ApiRequest): RequestBody? {
        if (request.body == null || request.body.isBlank()) {
            return null
        }
        val mediaType = when (request.contentType) {
            "application/json" -> "application/json; charset=utf-8"
            "application/xml", "text/xml" -> "application/xml; charset=utf-8"
            "application/x-www-form-urlencoded" -> "application/x-www-form-urlencoded"
            "text/plain" -> "text/plain; charset=utf-8"
            else -> "application/json; charset=utf-8"
        }
        return request.body.toRequestBody(mediaType.toMediaTypeOrNull())
    }

    companion object {
        fun buildCurlCommand(request: ApiRequest, envVars: Map<String, String> = emptyMap()): String {
            fun interpolate(text: String): String {
                var result = text
                for ((key, value) in envVars) {
                    result = result.replace("{{$key}}", value)
                }
                return result
            }

            val sb = StringBuilder("curl -X ${request.method.name}")

            // Add auth headers
            when (request.authType) {
                "Bearer Token" -> {
                    val token = request.authCredentials["token"] ?: ""
                    if (token.isNotBlank()) sb.append(" \\\n  -H 'Authorization: Bearer $token'")
                }
                "Basic Auth" -> {
                    val username = request.authCredentials["username"] ?: ""
                    val password = request.authCredentials["password"] ?: ""
                    if (username.isNotBlank()) sb.append(" \\\n  -u '$username:$password'")
                }
                "API Key" -> {
                    val key = request.authCredentials["key"] ?: ""
                    val value = request.authCredentials["value"] ?: ""
                    val addTo = request.authCredentials["addTo"] ?: "Header"
                    if (key.isNotBlank() && addTo == "Header") {
                        sb.append(" \\\n  -H '${interpolate(key)}: ${interpolate(value)}'")
                    }
                }
            }

            // Add custom headers (skip Authorization - already handled above)
            for ((key, value) in request.headers) {
                if (key.isNotBlank() && !key.equals("Authorization", ignoreCase = true)) {
                    sb.append(" \\\n  -H '${interpolate(key)}: ${interpolate(value)}'")
                }
            }

            // Add content-type header if body is present
            if (!request.body.isNullOrBlank() && request.contentType != "none") {
                sb.append(" \\\n  -H 'Content-Type: ${request.contentType}'")
                val escapedBody = interpolate(request.body).replace("'", "'\\''")
                sb.append(" \\\n  -d '$escapedBody'")
            }

            // Build URL with query params
            val rawUrl = interpolate(request.url)
            val urlBuilder = StringBuilder(rawUrl)
            if (request.queryParams.isNotEmpty()) {
                urlBuilder.append("?")
                urlBuilder.append(request.queryParams.entries.joinToString("&") {
                    "${interpolate(it.key)}=${interpolate(it.value)}"
                })
            }

            sb.append(" \\\n  '${urlBuilder}'")
            return sb.toString()
        }
    }
}
