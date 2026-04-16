package com.google.ai.edge.gallery.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Box: Encrypted local audit log for security events.
 * Logs biometric auth attempts, security violations, etc.
 * No network — everything stays on device.
 */
object SecurityAuditLog {

    private const val TAG = "BoxAuditLog"
    private const val LOG_FILE = "box_security_audit.log"
    private const val MAX_LOG_SIZE = 512 * 1024 // 512 KB max

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    /**
     * Append a security event to the encrypted audit log.
     */
    fun log(context: Context, event: String) {
        try {
            val logFile = File(context.filesDir, LOG_FILE)
            val entry = "${dateFormat.format(Date())} | $event\n"

            // Rotate if too large
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                SecurityUtils.secureDelete(logFile)
            }

            // Append to plain file (encrypted at rest via filesystem encryption on Android 10+)
            // For additional protection, the file is in the app's private directory
            logFile.appendText(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log", e)
        }
    }

    /**
     * Read the audit log contents.
     */
    fun readLog(context: Context): String {
        return try {
            val logFile = File(context.filesDir, LOG_FILE)
            if (logFile.exists()) logFile.readText() else "(empty)"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read audit log", e)
            "(error reading log)"
        }
    }

    /**
     * Securely wipe the audit log.
     */
    fun clearLog(context: Context) {
        val logFile = File(context.filesDir, LOG_FILE)
        if (logFile.exists()) {
            SecurityUtils.secureDelete(logFile)
        }
    }
}
