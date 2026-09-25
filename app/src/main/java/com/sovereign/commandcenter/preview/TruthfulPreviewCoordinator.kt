package com.sovereign.commandcenter.preview

enum class PreviewKind {
    WEB_APP,
    ANDROID_COMPOSE
}

enum class TruthfulPreviewState {
    IDLE,
    COMPILING,
    LIVE,
    BLOCKED
}

data class PreviewEvaluation(
    val kind: PreviewKind,
    val state: TruthfulPreviewState,
    val truthfulReason: String,
    val liveUrl: String? = null
)

object TruthfulPreviewCoordinator {
    fun evaluatePreview(kind: PreviewKind, devServerPort: Int? = null): PreviewEvaluation {
        return when (kind) {
            PreviewKind.WEB_APP -> {
                if (devServerPort != null && devServerPort > 0) {
                    PreviewEvaluation(
                        kind = kind,
                        state = TruthfulPreviewState.LIVE,
                        truthfulReason = "Live dev server running on port $devServerPort.",
                        liveUrl = "http://localhost:$devServerPort"
                    )
                } else {
                    PreviewEvaluation(
                        kind = kind,
                        state = TruthfulPreviewState.IDLE,
                        truthfulReason = "Dev server not active."
                    )
                }
            }
            PreviewKind.ANDROID_COMPOSE -> {
                PreviewEvaluation(
                    kind = kind,
                    state = TruthfulPreviewState.BLOCKED,
                    truthfulReason = "Truthful: Android Native Compose previews are BLOCKED in Cloud Shell due to missing hardware virtualization and ADB interfaces."
                )
            }
        }
    }
}
