package com.sovereign.commandcenter.data.api

import com.sovereign.commandcenter.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// Data models
data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

data class PasskeyChallengeResponse(
    val challengeId: String,
    val challenge: String,
    val rpId: String,
    val timeoutMs: Long
)

data class PasskeyVerifyResult(
    val success: Boolean,
    val token: String,
    val ownerId: String,
    val expiresAt: String,
    val displayName: String,
    val role: String
)

data class StepUpChallenge(
    val nonce: String,
    val expiresAt: Long,
    val targetOwner: String,
    val algorithm: String
)

data class StepUpResult(
    val success: Boolean,
    val action: String,
    val authorized: Boolean,
    val authorizedAt: String
)

data class RepoHealth(
    val name: String,
    val type: String,
    val status: String,
    val branch: String,
    val verifiedRelease: String,
    val pendingApprovals: Int
)

data class OrgHealth(
    val organization: String,
    val ceo: String,
    val timestamp: String,
    val overallHealth: String,
    val pendingApprovalsCount: Int,
    val repositories: List<RepoHealth>
)

data class CeoMessage(
    val id: String,
    val trigger: String,
    val repository: String,
    val severity: String,
    val timestamp: String,
    val whatSovereignFound: String,
    val whatSovereignDid: String,
    val whatRemains: String,
    val requiresApproval: Boolean
)

data class ApprovalResult(
    val success: Boolean,
    val approvalId: String,
    val approved: Boolean,
    val reason: String
)

object ApiClient {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var baseUrl: String = "https://sovereign-agent-api-production.trinityceo717.workers.dev" // Default Android Emulator host loopback; configurable

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Attach authoritative session cookie if available
            val cookie = SessionManager.getAuthCookieHeader()
            if (!cookie.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookie)
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    // --- 1. Passkey Authentication ---
    suspend fun getPasskeyChallenge(): Result<PasskeyChallengeResponse> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/auth/passkey/challenge")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                val challengeId = json.getString("challengeId")
                val challenge = json.getString("challenge")
                val rpId = json.getJSONObject("rp").getString("id")
                val timeout = json.optLong("timeout", 120_000L)
                Result.success(PasskeyChallengeResponse(challengeId, challenge, rpId, timeout))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyPasskey(challengeId: String, clientDataJSON: String? = null): Result<PasskeyVerifyResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("challengeId", challengeId)
                if (clientDataJSON != null) put("clientDataJSON", clientDataJSON)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/auth/passkey/verify")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                val user = json.optJSONObject("user")
                val result = PasskeyVerifyResult(
                    success = json.optBoolean("success", false),
                    token = json.getString("token"),
                    ownerId = json.getString("ownerId"),
                    expiresAt = json.getString("expiresAt"),
                    displayName = user?.optString("name", "Adebola James Ogunjimi") ?: "Adebola James Ogunjimi",
                    role = user?.optString("role", "CEO") ?: "CEO"
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 2. Action-Bound Biometric Step-Up ---
    suspend fun getStepUpChallenge(): Result<StepUpChallenge> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/auth/step-up/challenge")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                Result.success(
                    StepUpChallenge(
                        nonce = json.getString("nonce"),
                        expiresAt = json.getLong("expiresAt"),
                        targetOwner = json.getString("targetOwner"),
                        algorithm = json.getString("algorithm")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyStepUp(nonce: String, signature: String, action: String): Result<StepUpResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("nonce", nonce)
                put("signature", signature)
                put("action", action)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/auth/step-up/verify")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                Result.success(
                    StepUpResult(
                        success = json.optBoolean("success", false),
                        action = json.getString("action"),
                        authorized = json.optBoolean("authorized", false),
                        authorizedAt = json.optString("authorizedAt", "")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 3. Organization Health & CEO Messages ---
    suspend fun getOrgHealth(): Result<OrgHealth> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/org/health")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                val reposArray = json.optJSONArray("repositories") ?: JSONArray()
                val repos = mutableListOf<RepoHealth>()
                for (i in 0 until reposArray.length()) {
                    val r = reposArray.getJSONObject(i)
                    repos.add(
                        RepoHealth(
                            name = r.getString("name"),
                            type = r.optString("type", ""),
                            status = r.optString("status", "nominal"),
                            branch = r.optString("branch", "main"),
                            verifiedRelease = r.optString("verifiedRelease", ""),
                            pendingApprovals = r.optInt("pendingApprovals", 0)
                        )
                    )
                }
                Result.success(
                    OrgHealth(
                        organization = json.getString("organization"),
                        ceo = json.getString("ceo"),
                        timestamp = json.getString("timestamp"),
                        overallHealth = json.getString("overallHealth"),
                        pendingApprovalsCount = json.optInt("pendingApprovalsCount", 0),
                        repositories = repos
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(): Result<List<CeoMessage>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/messages")
                .get()
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                val msgsArray = json.optJSONArray("messages") ?: JSONArray()
                val messages = mutableListOf<CeoMessage>()
                for (i in 0 until msgsArray.length()) {
                    val m = msgsArray.getJSONObject(i)
                    messages.add(
                        CeoMessage(
                            id = m.getString("id"),
                            trigger = m.optString("trigger", "system"),
                            repository = m.optString("repository", "Sovereign"),
                            severity = m.optString("severity", "info"),
                            timestamp = m.optString("timestamp", ""),
                            whatSovereignFound = m.optString("whatSovereignFound", ""),
                            whatSovereignDid = m.optString("whatSovereignDid", ""),
                            whatRemains = m.optString("whatRemains", ""),
                            requiresApproval = m.optBoolean("requiresApproval", false)
                        )
                    )
                }
                Result.success(messages)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveMessage(messageId: String, action: String, reason: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", action)
                if (reason != null) put("reason", reason)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/command-center/messages/$messageId/resolve")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                Result.success(resp.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 4. HITL Approvals ---
    suspend fun submitApproval(
        approvalId: String,
        approved: Boolean,
        reason: String? = null,
        sessionId: String = "default-session"
    ): Result<ApprovalResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("approvalId", approvalId)
                put("approved", approved)
                put("sessionId", sessionId)
                if (reason != null) put("reason", reason)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/agent/approve")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${resp.code}: $body"))
                val json = JSONObject(body)
                Result.success(
                    ApprovalResult(
                        success = json.optBoolean("success", false),
                        approvalId = json.getString("approvalId"),
                        approved = json.getBoolean("approved"),
                        reason = json.optString("reason", "")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- 5. GitHub Updates (Dynamic Auto-Updater) ---
    fun checkForUpdate(currentVersionCode: Int = 2, onResult: (AppUpdateInfo?) -> Unit) {
        Thread {
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/updatedj25-gif/sovereign-command-center-android/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful && bodyStr.isNotEmpty()) {
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "latest")
                    val notes = json.optString("body", "Latest build updates and improvements.")
                    val defaultApk = "https://github.com/updatedj25-gif/sovereign-command-center-android/releases/download/latest/app-debug.apk"
                    var apkUrl = defaultApk

                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name").endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }

                    onResult(AppUpdateInfo(
                        hasUpdate = true,
                        versionName = tagName,
                        releaseNotes = notes,
                        downloadUrl = apkUrl
                    ))
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }.start()
    }
}
