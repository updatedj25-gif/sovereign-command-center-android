package com.sovereign.commandcenter.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    var baseUrl = "https://3000-updatedj25-sovereignagen-wzgoxn5f935.ws-eu118.gitpod.io" // Can be configured in settings
    var sessionToken: String? = null
    var activeOwnerId: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun getPasskeyChallenge(onResult: (Boolean, String?, String?) -> Unit) {
        Thread {
            try {
                val req = Request.Builder()
                    .url("$baseUrl/api/command-center/auth/passkey/challenge")
                    .post("{}".toRequestBody(JSON_MEDIA))
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: "{}"
                if (resp.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val challengeId = json.optString("challengeId")
                    val challenge = json.optString("challenge")
                    onResult(true, challengeId, challenge)
                } else {
                    onResult(false, null, null)
                }
            } catch (e: Exception) {
                onResult(false, null, null)
            }
        }.start()
    }

    fun verifyPasskey(challengeId: String, onResult: (Boolean, String?) -> Unit) {
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("challengeId", challengeId)
                    put("clientDataJSON", "android-passkey-verified")
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/command-center/auth/passkey/verify")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: "{}"
                if (resp.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    sessionToken = json.optString("token")
                    activeOwnerId = json.optString("ownerId")
                    onResult(true, sessionToken)
                } else {
                    onResult(false, "Server rejected assertion: HTTP ${resp.code}")
                }
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }.start()
    }
}
