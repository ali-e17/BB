package com.example.bb

import com.google.gson.Gson
import retrofit2.Response

/** Reads the JSON error envelope returned by the PHP API for non-2xx responses. */
object ApiErrorParser {
    private val gson = Gson()

    fun parse(response: Response<*>): ApiResponse? {
        if (response.isSuccessful) return null
        val raw = runCatching { response.errorBody()?.string() }
            .getOrNull()
            .orEmpty()
        if (raw.isBlank()) return null
        return runCatching { gson.fromJson(raw, ApiResponse::class.java) }.getOrNull()
    }
}
