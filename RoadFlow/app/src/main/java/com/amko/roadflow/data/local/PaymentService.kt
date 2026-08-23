package com.amko.roadflow.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PaymentService(private val firebaseService: FirebaseService) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun sanitizeEmail(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "")
            .replace("]", "")
    }

    private suspend fun getAuthenticatedUrl(path: String): String {
        val token = firebaseService.getAuthTokenPublicAsync()
        val separator = if (path.contains("?")) "&" else "?"
        return "$path${separator}auth=$token"
    }

    suspend fun isPremiumAsync(email: String): Boolean = withContext(Dispatchers.IO) {
        val key = sanitizeEmail(email)
        val url = getAuthenticatedUrl("${Secrets.FIREBASE_BASE_URL}pretplata/pretplatnici/$key.json")

        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                android.util.Log.d("PaymentDebug", "isPremiumAsync response code=${response.code}")
                return@withContext false
            }

            val json = response.body?.string()
            if (json.isNullOrBlank() || json == "null") return@withContext false

            json.trim() == "true"
        } catch (e: Exception) {
            android.util.Log.d("PaymentDebug", "isPremiumAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    suspend fun ensureUserEntryAsync(email: String): Boolean = withContext(Dispatchers.IO) {
        val key = sanitizeEmail(email)
        val checkUrl = getAuthenticatedUrl("${Secrets.FIREBASE_BASE_URL}pretplata/pretplatnici/$key.json")

        try {
            val body = "true".toRequestBody("application/json".toMediaType())
            val putRequest = Request.Builder()
                .url(checkUrl)
                .put(body)
                .build()

            val putResponse = client.newCall(putRequest).execute()

            if (!putResponse.isSuccessful) {
                android.util.Log.d("PaymentDebug", "ensureUserEntryAsync PUT failed code=${putResponse.code}")
                return@withContext false
            }

            true
        } catch (e: Exception) {
            android.util.Log.d("PaymentDebug", "ensureUserEntryAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    suspend fun getGlobalStatusAsync(): Boolean = withContext(Dispatchers.IO) {
        val url = getAuthenticatedUrl("${Secrets.FIREBASE_BASE_URL}pretplata/status.json")

        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext true

            val json = response.body?.string()
            if (json.isNullOrBlank() || json.trim() == "null") return@withContext true

            json.trim() == "true"
        } catch (e: Exception) {
            android.util.Log.d("PaymentDebug", "getGlobalStatusAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            true
        }
    }

    suspend fun getPriceAsync(): String = withContext(Dispatchers.IO) {
        val url = getAuthenticatedUrl("${Secrets.FIREBASE_BASE_URL}pretplata/cijena.json")

        try {
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext "3"

            val json = response.body?.string()
            if (json.isNullOrBlank() || json.trim() == "null") return@withContext "3"

            json.trim().removeSurrounding("\"")
        } catch (e: Exception) {
            android.util.Log.d("PaymentDebug", "getPriceAsync EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            "3"
        }
    }
}