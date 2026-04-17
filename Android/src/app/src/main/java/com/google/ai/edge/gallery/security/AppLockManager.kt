package com.google.ai.edge.gallery.security

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Box: App lock manager that observes app lifecycle.
 * Enforces biometric authentication when app comes to foreground.
 */
object AppLockManager {

    private const val PREFS_NAME = "box_settings"
    private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"

    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var isBiometricLockEnabled = false

    fun init(context: Context) {
        val prefs = getPrefs(context)
        isBiometricLockEnabled = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
        
        // Observe app lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun onAppForegrounded() {
                if (isBiometricLockEnabled) {
                    _isUnlocked.value = false
                    SecurityAuditLog.log(context, "APP_LOCKED")
                }
            }
        })
    }

    fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        isBiometricLockEnabled = enabled
        SecurityAuditLog.log(context, "BIOMETRIC_LOCK_${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun isBiometricLockEnabled(): Boolean = isBiometricLockEnabled

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
