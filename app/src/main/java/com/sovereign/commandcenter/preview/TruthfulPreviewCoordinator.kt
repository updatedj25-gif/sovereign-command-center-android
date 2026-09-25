package com.sovereign.commandcenter.preview

enum class PreviewKind {
    WEB_APP,
    ANDROID_COMPOSE
}

enum class TruthfulPreviewState {
    IDLE,
    WAITING_FOR_RENDER,
    RENDERED,
    FAILED,
    BLOCKED
}

data class PreviewEvaluation(
    val kind: PreviewKind,
    val state: TruthfulPreviewState,
    val truthfulReason: String,
    val liveUrl: String? = null,
    val port: Int? = null
)

object TruthfulPreviewCoordinator {
    fun evaluatePreview(
        kind: PreviewKind,
        devServerPort: Int? = null,
        hasRenderSignal: Boolean = false,
        isTimedOut: Boolean = false
    ): PreviewEvaluation {
        return when (kind) {
            PreviewKind.WEB_APP -> {
                when {
                    isTimedOut -> PreviewEvaluation(
                        kind = kind,
                        state = TruthfulPreviewState.FAILED,
                        truthfulReason = "Preview render timed out after 10 seconds. No visible viewport content reported.",
                        port = devServerPort
                    )
                    hasRenderSignal && devServerPort != null && devServerPort > 0 -> PreviewEvaluation(
                        kind = kind,
                        state = TruthfulPreviewState.RENDERED,
                        truthfulReason = "Verified active iframe render signal on port $devServerPort.",
                        liveUrl = "http://localhost:$devServerPort",
                        port = devServerPort
                    )
                    devServerPort != null && devServerPort > 0 -> PreviewEvaluation(
                        kind = kind,
                        state = TruthfulPreviewState.WAITING_FOR_RENDER,
                        truthfulReason = "Dev server detected on port $devServerPort. Awaiting viewport render verification...",
                        liveUrl = "http://localhost:$devServerPort",
                        port = devServerPort
                    )
                    else -> PreviewEvaluation(
                        kind = kind,
                        state = TruthfulPreviewState.IDLE,
                        truthfulReason = "No dev server running in active session workspace."
                    )
                }
            }
            PreviewKind.ANDROID_COMPOSE -> PreviewEvaluation(
                kind = kind,
                state = TruthfulPreviewState.BLOCKED,
                truthfulReason = "Truthful: Android Native Compose previews are BLOCKED in Cloud Shell (missing hardware virtualization & ADB interfaces)."
            )
        }
    }
}
