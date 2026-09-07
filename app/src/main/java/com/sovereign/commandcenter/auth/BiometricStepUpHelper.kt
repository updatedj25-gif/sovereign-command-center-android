package com.sovereign.commandcenter.auth

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sovereign.commandcenter.data.api.ApiClient
import com.sovereign.commandcenter.data.api.StepUpResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

object BiometricStepUpHelper {
    private const val KEY_ALIAS = "SovereignStepUpECKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun getOrCreateECPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) return entry.privateKey
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_SIGN
        )
            .setDigests(
                android.security.keystore.KeyProperties.DIGEST_SHA256,
                android.security.keystore.KeyProperties.DIGEST_SHA512
            )
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair().private
    }

    /**
     * Executes an action-bound cryptographic step-up verification:
     * 1. Fetches single-use nonce from server
     * 2. Shows biometric prompt
     * 3. Signs nonce:action:repository:environment payload using device Keystore key
     * 4. Submits signature to backend server for authoritative verification
     */
    fun executeActionBoundStepUp(
        activity: FragmentActivity,
        action: String,
        repository: String,
        environment: String = "Production",
        sessionId: String = "default",
        onAuthorized: (StepUpResult) -> Unit,
        onFailed: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            // Step 1: Request Server Challenge
            val challengeResult = ApiClient.getStepUpChallenge()
            if (challengeResult.isFailure) {
                onFailed("Failed to fetch step-up challenge: ${challengeResult.exceptionOrNull()?.message}")
                return@launch
            }
            val challenge = challengeResult.getOrThrow()

            // Step 2: Prepare Keystore Signature
            val payloadToSign = "${challenge.nonce}:$action:$repository:$environment:$sessionId"
            val signature = try {
                Signature.getInstance("SHA256withECDSA").apply {
                    initSign(getOrCreateECPrivateKey())
                }
            } catch (e: Exception) {
                onFailed("Keystore signature initialization failed: ${e.message}")
                return@launch
            }

            val executor = ContextCompat.getMainExecutor(activity)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authorize High-Risk Action")
                .setSubtitle("$action on $repository ($environment)")
                .setAllowedAuthenticators(authenticators)
                .build()

            val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            signature.update(payloadToSign.toByteArray(StandardCharsets.UTF_8))
                            val signatureBytes = signature.sign()
                            val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

                            // Step 3: Verify on Server
                            val verifyResult = ApiClient.verifyStepUp(
                                nonce = challenge.nonce,
                                signature = signatureBase64,
                                action = action
                            )

                            if (verifyResult.isSuccess && verifyResult.getOrThrow().authorized) {
                                onAuthorized(verifyResult.getOrThrow())
                            } else {
                                onFailed("Server verification rejected step-up: ${verifyResult.exceptionOrNull()?.message ?: "Unauthorized"}")
                            }
                        } catch (e: Exception) {
                            onFailed("Cryptographic signature failed: ${e.message}")
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailed("Biometric unlock error: $errString")
                }
            })

            try {
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
            } catch (e: Exception) {
                // Fallback to authenticate without CryptoObject if device does not support CryptoObject with BiometricStrong
                val fallbackPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                signature.update(payloadToSign.toByteArray(StandardCharsets.UTF_8))
                                val sig = Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
                                val verifyResult = ApiClient.verifyStepUp(challenge.nonce, sig, action)
                                if (verifyResult.isSuccess && verifyResult.getOrThrow().authorized) {
                                    onAuthorized(verifyResult.getOrThrow())
                                } else {
                                    onFailed("Step-up unauthorized by server.")
                                }
                            } catch (ex: Exception) {
                                onFailed("Signing failed: ${ex.message}")
                            }
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onFailed("Unlock cancelled: $errString")
                    }
                })
                fallbackPrompt.authenticate(promptInfo)
            }
        }
    }
}
