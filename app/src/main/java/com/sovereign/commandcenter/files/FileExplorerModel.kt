package com.sovereign.commandcenter.files

data class SessionFileEntry(
    val sessionId: String,
    val runId: String?,
    val path: String,
    val changeType: FileChangeType,
    val sizeBytes: Long,
    val diffContent: String? = null,
    val isRedacted: Boolean = true
)

enum class FileChangeType {
    CREATED,
    MODIFIED,
    DELETED
}

class RedactionUtility {
    companion object {
        private val SECRET_PATTERNS = listOf(
            Regex("(?i)(api[_-]?key|secret|token|password|auth|private[_-]?key)\\s*[:=]\\s*['\"][A-Za-z0-9_\\-+=]{8,}['\"]"),
            Regex("ghp_[A-Za-z0-9]{36}"),
            Regex("sk-[A-Za-z0-9]{32,}"),
            Regex("Bearer\\s+[A-Za-z0-9_\\-\\.]+"),
            Regex("-----BEGIN (RSA|EC|OPENSSH) PRIVATE KEY-----.*?-----END \\1 PRIVATE KEY-----", RegexOption.DOT_MATCHES_ALL)
        )

        fun sanitizeDiff(diffText: String): String {
            var sanitized = diffText
            for (pattern in SECRET_PATTERNS) {
                sanitized = sanitized.replace(pattern, "[REDACTED_BY_SOVEREIGN_POLICY]")
            }
            return sanitized
        }
    }
}
