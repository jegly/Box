# AGENTS.md
# Last completed step: Security hardening + M3 You + Biometric + Encrypted DB + Offline mode + llama.cpp module integration
# Next step to start from: Wire chat DB into ViewModels, wire engine toggle into UI, test build

# Box App – Build Status

## Completed

### 1. Rebrand UI ✅
- App name → "Box" in strings.xml, manifest, settings.gradle
- Disclaimer updated with Box attribution
- Notification channel rebranded
- Version → 2.0.0-box

### 2. SDK Bump ✅
- minSdk = 35 (Android 15+)
- compileSdk = targetSdk = 36 (Android 16)
- Removed locked orientation (Android 16 requirement)

### 3. Security Hardening ✅ (14/14 items)
- MTE: memtagMode="async" in manifest
- FLAG_SECURE: programmatically in MainActivity.onCreate()
- Tapjacking: semantics on ChatView Scaffold
- Backup prevention: allowBackup="false" + hasFragileUserData="true"
- Network security: cleartextTrafficPermitted="false" via network_security_config.xml
- Clipboard protection: SecurityUtils.copyToClipboardSensitive() with isSensitive
- Secure deletion: 3-pass overwrite in SecurityUtils.secureDelete()
- Input sanitization: SecurityUtils.sanitizePrompt()
- PendingIntent: already FLAG_IMMUTABLE (no change needed)
- Predictive back: enableOnBackInvokedCallback="true"
- Security audit log: SecurityAuditLog.kt (encrypted local-only)
- Safer intents: removed orientation lock
- Firebase analytics removed from MainActivity.onResume()
- Sensitive data semantics on chat views

### 4. Material 3 You ✅
- Theme.kt: dynamicColorScheme() from wallpaper
- themes.xml: parent = Theme.Material3.DayNight.NoActionBar, colorDynamic=true
- Dependencies: material3-adaptive-navigation-suite, material3-window-size-class
- Custom colors derived from dynamic scheme for backward compat

### 5. Biometric StrongBox + Encrypted Chat DB ✅
- BiometricHelper.kt: StrongBox AES key with software fallback
- Biometric auth on resume, re-auth on pause
- BoxChatDatabase.kt: Room + SQLCipher encryption
- Conversation + Message entities, DAOs
- DatabaseModule.kt: Hilt DI wiring
- USE_BIOMETRIC permission added

### 6. Offline Mode ✅
- OfflineMode.kt: settings toggle + StateFlow
- DownloadWorker.kt: checks offline mode before downloads
- Settings dialog: toggle switch added

### 7. llama.cpp Module Integration ✅ (structural)
- smollm module copied and adapted (namespace, SDK, JVM target)
- CMakeLists.txt, JNI bridge (SmolLM.kt, smollm.cpp, LLMInference.cpp)
- GGUFReader for model metadata extraction
- LlamaCppEngine.kt: Box wrapper with generate/stop/bench
- InferenceEngineType.kt: auto-detect .tflite vs .gguf
- llama.cpp submodule placeholder created
- Module included in settings.gradle.kts + app depends on :smollm

## Remaining TODO

### Wire chat persistence into ViewModels
- Inject ConversationDao/MessageDao into LlmChatViewModel
- Save messages on send/receive
- Load conversation history on screen entry
- Delete conversations from UI

### Wire engine toggle into UI
- Add engine selector in model config or settings
- Route inference through LlamaCppEngine when GGUF detected
- Show engine type badge on model cards

### Build & Test
- Clone llama.cpp submodule: `git submodule add https://github.com/ggerganov/llama.cpp.git`
- Install NDK 27.2.12479018
- Build debug APK
- Test biometric flow
- Test offline mode toggle
- Test dynamic color theming

### Launcher Icons
- Replace ic_launcher_foreground.xml and ic_launcher_background.xml with Box branding
