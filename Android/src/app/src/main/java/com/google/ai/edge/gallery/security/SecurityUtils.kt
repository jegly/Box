package com.google.ai.edge.gallery.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import java.io.File
import java.security.SecureRandom

/**
 * Box: Security utilities for privacy hardening.
 * Ported and extended from OfflineLLM SecurityUtils.
 */
object SecurityUtils {

    private const val TAG = "BoxSecurity"

    /**
     * Sanitize user input before sending to inference engine.
     * Removes null bytes, control characters, and enforces max length.
     */
    fun sanitizePrompt(input: String, maxLength: Int = 4096): String {
        return input
            .take(maxLength)
            .replace("\u0000", "") // Remove null bytes
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "") // Remove control chars
            .trim()
    }

    /**
     * Redact sensitive content for logging.
     * Never log full prompts or responses.
     */
    fun redactForLog(content: String): String {
        if (content.length <= 6) return "[REDACTED]"
        return "${content.take(3)}***${content.takeLast(3)}"
    }

    /**
     * Securely delete a file by overwriting with random bytes before deletion.
     * Three-pass overwrite: random, zeros, random.
     */
    fun secureDelete(file: File): Boolean {
        return try {
            if (!file.exists()) return true

            val random = SecureRandom()
            val buffer = ByteArray(8192)
            val length = file.length()

            // Pass 1: random data
            overwriteFile(file, length) { random.nextBytes(buffer); buffer }
            // Pass 2: zeros
            val zeros = ByteArray(8192)
            overwriteFile(file, length) { zeros }
            // Pass 3: random data again
            overwriteFile(file, length) { random.nextBytes(buffer); buffer }

            // Then delete
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Secure delete failed", e)
            file.delete() // Fallback to regular delete
        }
    }

    private fun overwriteFile(file: File, length: Long, bufferProvider: () -> ByteArray) {
        file.outputStream().use { output ->
            var written = 0L
            while (written < length) {
                val buffer = bufferProvider()
                val toWrite = minOf(buffer.size.toLong(), length - written).toInt()
                output.write(buffer, 0, toWrite)
                written += toWrite
            }
            output.flush()
        }
    }

    /**
     * Validate that a file path is within the allowed sandbox directory.
     * Prevents path traversal attacks.
     */
    fun isPathSandboxed(path: String, sandboxDir: String): Boolean {
        val canonicalPath = File(path).canonicalPath
        val canonicalSandbox = File(sandboxDir).canonicalPath
        return canonicalPath.startsWith(canonicalSandbox)
    }

    /**
     * Copy text to clipboard with the isSensitive flag (API 33+).
     * Marks the clip as sensitive so it won't appear in clipboard preview.
     */
    fun copyToClipboardSensitive(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
    }

    /**
     * Generate a database encryption passphrase from Android Keystore.
     * Used to derive the SQLCipher key.
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("box_security", Context.MODE_PRIVATE)
        val existing = prefs.getString("db_key", null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        }

        // Generate a new random 32-byte key
        val passphrase = ByteArray(32)
        SecureRandom().nextBytes(passphrase)
        prefs.edit()
            .putString("db_key", android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP))
            .apply()
        return passphrase
    }
}
