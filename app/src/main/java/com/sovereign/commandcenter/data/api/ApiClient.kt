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
    val versionCode: Int = 0,
    val versionName: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val signingCertificateSha256: String = "",
    val mandatory: Boolean = false
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
    @Volatile
    var allowTestOverride: Boolean = false
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var baseUrl: String = com.sovereign.commandcenter.BuildConfig.API_BASE_URL
        set(value) {
            val sanitized = value.trim().removeSuffix("/")
            if (!com.sovereign.commandcenter.BuildConfig.DEBUG && !allowTestOverride) {
                require(sanitized.startsWith("https://")) { "Release builds permit HTTPS only" }
                require(!sanitized.contains("localhost") && !sanitized.contains("127.0.0.1") && !sanitized.contains("10.0.2.2")) { "Release builds reject local loopbacks" }
                require(!sanitized.contains("trycloudflare.com")) { "Temporary tunnels are forbidden" }
            }
            field = sanitized
        }

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
                    .url("$baseUrl/api/command-center/mobile/android/update")
                    .header("Accept", "application/json")
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful && bodyStr.isNotEmpty()) {
                    val json = JSONObject(bodyStr)
                    val serverVersionCode = json.optInt("versionCode", 0)
                    val versionName = json.optString("versionName", "")
                    val releaseNotes = json.optString("releaseNotes", "System updates and security improvements.")
                    val downloadUrl = json.optString("apkUrl", "")
                    val sha256 = json.optString("sha256", "")
                    val certSha256 = json.optString("signingCertificateSha256", "")
                    val mandatory = json.optBoolean("mandatory", false)

                    val hasUpdate = serverVersionCode > currentVersionCode && downloadUrl.startsWith("https://")

                    onResult(AppUpdateInfo(
                        hasUpdate = hasUpdate,
                        versionCode = serverVersionCode,
                        versionName = versionName,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        sha256 = sha256,
                        signingCertificateSha256 = certSha256,
                        mandatory = mandatory
                    ))
                } else {
                    onResult(null)
                }
            } catch (_: Exception) {
                onResult(null)
            }
        }.start()
    }

    /**
     * Downloads an update APK and verifies its SHA-256 digest before allowing installation.
     */
    fun downloadAndVerifyApk(
        context: android.content.Context,
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit,
        onVerified: (java.io.File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!updateInfo.downloadUrl.startsWith("https://")) {
            onError("Insecure download URL rejected. HTTPS is mandatory.")
            return
        }

        Thread {
            try {
                val req = Request.Builder().url(updateInfo.downloadUrl).build()
                val resp = okHttpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    onError("Download failed with HTTP ${resp.code}")
                    return@Thread
                }

                val body = resp.body ?: run {
                    onError("Empty response body from update server")
                    return@Thread
                }

                val updateDir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
                // Clear old updates
                updateDir.listFiles()?.forEach { it.delete() }

                val targetFile = java.io.File(updateDir, "update_${updateInfo.versionCode}.apk")
                val digest = java.security.MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            digest.update(buffer, 0, bytesRead)
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }

                val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }

                // If server provides a checksum, require strict match
                if (updateInfo.sha256.isNotBlank() && !calculatedHash.equals(updateInfo.sha256.trim(), ignoreCase = true)) {
                    targetFile.delete()
                    onError("SHA-256 Checksum Mismatch! Expected: ${updateInfo.sha256}, Got: $calculatedHash")
                    return@Thread
                }

                onVerified(targetFile)
            } catch (e: Exception) {
                onError("Download and verification error: ${e.message}")
            }
        }.start()
    }

}
