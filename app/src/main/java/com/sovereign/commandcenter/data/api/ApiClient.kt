package com.sovereign.commandcenter.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun checkForUpdate(currentVersionCode: Int, onResult: (AppUpdateInfo?) -> Unit) {
        Thread {
            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/updatedj25-gif/sovereign-command-center-android/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: ""
                if (resp.isSuccessful && bodyStr.isNotEmpty()) {
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "v1.0.1")
                    val notes = json.optString("body", "Fingerprint unlock fix and update notifications.")
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name").endsWith(".apk")) {
                                downloadUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    if (downloadUrl.isEmpty()) {
                        downloadUrl = "https://github.com/updatedj25-gif/sovereign-command-center-android/releases"
                    }
                    val isNewer = tagName != "v1.0.0" && tagName != "v1.0.1"
                    onResult(AppUpdateInfo(isNewer, tagName, notes, downloadUrl))
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }.start()
    }
}
