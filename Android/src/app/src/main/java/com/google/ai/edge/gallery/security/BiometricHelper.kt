package com.google.ai.edge.gallery.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Box: Biometric authentication with StrongBox-backed key for chat encryption.
 *
 * Generates an AES key in Android Keystore that requires biometric auth to unlock.
 * On devices with StrongBox (Titan M / similar HSM), the key is stored in hardware.
 */
class BiometricHelper(private val activity: FragmentActivity) {

    companion object {
        private const val KEY_ALIAS = "box_biometric_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    fun canAuthenticate(): BiometricStatus {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    /**
     * Generate or retrieve the biometric-bound AES key from Keystore.
     * Uses StrongBox if available on the device.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(true) // StrongBox hardware security
            .build()

        return try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            // Fallback: device may not have StrongBox — generate without it
            val fallbackSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            keyGenerator.init(fallbackSpec)
            keyGenerator.generateKey()
        }
    }

    /**
     * Authenticate with biometric and provide a crypto object for encryption/decryption.
     */
    fun authenticateWithCrypto(
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onFailure: (Int, CharSequence?) -> Unit,
        onError: (Int, CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                SecurityAuditLog.log(activity, "BIOMETRIC_AUTH_SUCCESS")
                onSuccess(result.cryptoObject)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                SecurityAuditLog.log(activity, "BIOMETRIC_AUTH_FAILED")
                onFailure(-1, "Authentication failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                SecurityAuditLog.log(activity, "BIOMETRIC_AUTH_ERROR: code=$errorCode")
                onError(errorCode, errString)
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Box")
            .setSubtitle("Authenticate to access your chats")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo, cryptoObject)
        } catch (_: Exception) {
            // If crypto setup fails, fall back to simple biometric auth
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        }
    }

    /**
     * Simple biometric auth without crypto (for app resume lock screen).
     */
    fun authenticate(
        onSuccess: () -> Unit,
        onFailure: (Int, CharSequence?) -> Unit,
        onError: (Int, CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                SecurityAuditLog.log(activity, "BIOMETRIC_AUTH_SUCCESS")
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                SecurityAuditLog.log(activity, "BIOMETRIC_AUTH_FAILED")
                onFailure(-1, "Authentication failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                SecurityAuditLog.log(activity, "BIOMETRIC_AUTH_ERROR: code=$errorCode")
                onError(errorCode, errString)
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Box")
            .setSubtitle("Authenticate to access your chats")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        UNAVAILABLE,
        NOT_ENROLLED
    }
}
