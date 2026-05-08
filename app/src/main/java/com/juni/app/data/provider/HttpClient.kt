package com.juni.app.data.provider

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP plumbing for all AI providers. Streaming responses keep the
 * connection open for minutes while tokens trickle in, so read timeouts are
 * disabled — we rely on cancellation from the caller's coroutine scope.
 */
object HttpClient {
    val okhttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }
}
