<img src="https://raw.githubusercontent.com/jegly/Box/main/images/box-header.svg" alt="Box Header" width="800" />


[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Upstream](https://img.shields.io/badge/upstream-google--ai--edge%2Fgallery-brightgreen)](https://github.com/google-ai-edge/gallery)

**A security-hardened fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) — with Unique Hybrid Features,biometric lock, encrypted chat history, llama.cpp support, and GGUF model import.**

## Disclaimer

**Box is not affiliated with or endorsed by Google LLC in any way.**

This is an independent, community-driven fork of the original Google AI Edge Gallery project.  
All credit goes to Google and the original contributors for their excellent open-source work.

Google branding, logos, and references have been completely removed and replaced.

> **Repository:** [github.com/jegly/box](https://github.com/jegly/box)

## Related

Built [OfflineLLM](https://github.com/jegly/OfflineLLM) first — a privacy-first Android chat app with a [llama.cpp](https://github.com/ggerganov/llama.cpp) backend.

This project (`Box`) forks Google's AI Edge Gallery to create a **hybrid LiteRT / llama.cpp** experience. Integrating llama.cpp here was easier than adding LiteRT to OfflineLLM.

→ Try the [OfflineLLM app](https://github.com/jegly/OfflineLLM) for pure llama.cpp on-device chat.

---

## What is Box?

Box is an Android app for running large language models entirely on-device. It inherits the full feature set of the upstream Google AI Edge Gallery and adds a security-focused layer on top: your conversations are encrypted at rest, the app can lock behind biometrics, and a hard offline mode blocks all network traffic on demand.

On the inference side, Box integrates llama.cpp alongside the upstream LiteRT runtime. This lets you sideload any GGUF model file and choose between CPU, GPU, or NPU acceleration per model — so you are not limited to the curated model list.

#  Box: The Only Android App That Fuses Google's LiteRT with llama.cpp + Biometric Security

**What makes Box unique?** While other on-device LLM apps force you to choose between Google's optimized LiteRT ecosystem (limited model selection) or the open GGUF ecosystem (limited hardware acceleration), **Box runs both side-by-side** — letting you import any GGUF model while keeping LiteRT's NPU acceleration for compatible models.

## Why This Matters (And Why No One Else Does It)

| Feature | Google AI Edge Gallery | llama.cpp-only apps (OfflineLLM) | **Box (This Project)** |
|---------|----------------------|--------------------------------|------------------------|
| LiteRT + NPU acceleration | ✅ | ❌ | ✅ |
| Import any GGUF model | ❌ | ✅ | ✅ |
| Encrypted chat history | ❌ | ❌ | ✅ |
| Biometric app lock | ❌ | ❌ | ✅ |
| Per-model accelerator choice | ❌ | ❌ | ✅ |
| Hard offline mode (airgap) | ❌ | ❌ | ✅ |

**The technical breakthrough:** Box doesn't just bundle both runtimes — it lets you choose **per-model** whether to run on CPU, GPU (via OpenCL/Vulkan), or NPU (via QNN delegate). No other Android app gives you this granular control.

## The Hybrid Architecture Most Developers Think Is Impossible
User's GGUF file → llama.cpp → CPU/GPU
Google's .litertlm → LiteRT → NPU (Qualcomm/MediaTek)
↓
Same chat interface, same encrypted history


Most developers assume you have to pick one inference engine. Box proves otherwise — and adds enterprise-grade security on top.

## Built By Someone Who Already Did The Hard Part

This isn't a theoretical project. I built **OfflineLLM** (pure llama.cpp app) first, then forked Google AI Edge Gallery to add llama.cpp support. The result: an app that inherits Google's polished UI and multimodal features (Ask Image, Audio Scribe, Agent Skills) while adding the open model flexibility that Google's curated allowlist prevents.

## For Security-Conscious Users Running Sensitive Conversations

- SQLCipher AES-256 encrypted Room database
- Biometric re-authentication on every foreground
- Hard offline switch — blocks all network traffic
- Input sanitization before inference AND persistence
- On-device security audit log

**Bottom line:** If you want to run Qwen3.6,Llama 3, Mistral, or any GGUF model *with* the option of NPU acceleration when available — while keeping your conversations encrypted and offline — Box is currently the only Android app that delivers all of that in one package.

---

## What's different from upstream

| Area | Upstream (Google AI Edge Gallery) | Box |
|---|---|---|
| Chat history | In-memory only (lost on close) | Persisted to SQLCipher-encrypted Room DB |
| App lock | None | Optional biometric lock (fingerprint / face) on foreground |
| Offline mode | Always online | Hard offline switch — blocks all network requests |
| Inference engine | LiteRT only | LiteRT + llama.cpp |
| Model import | Download from allowlist | Import local GGUF files |
| Accelerator | Per-model default | User-selectable CPU / GPU / NPU at import time |
| Security audit log | None | On-device log of security-relevant events |
| Prompt sanitisation | None | Input sanitised before inference and persistence |
| Chat resume | None | Conversations resume where you left off |

---

## Core Features

**Biometric App Lock**
Enable an optional biometric lock from Settings. The app re-locks automatically every time it is backgrounded. Unlock via fingerprint or face authentication before any content is shown.

**Encrypted Chat History**
All conversations are stored in a SQLCipher-encrypted Room database. History persists across sessions and is resumable from the Chat History screen in the drawer. Swipe to delete individual conversations, or delete all at once.

**llama.cpp Engine**
Runs GGUF models natively via a bundled llama.cpp submodule. Supports the full range of quantised model formats (Q4_K_M, Q5_K_M, Q8_0, F16, etc.).

**GGUF Model Import**
Import any GGUF model file from local storage. At import time you can set the display name and choose the accelerator (CPU, GPU via OpenCL/Vulkan, or NPU via QNN delegate).

**Accelerator Selection**
Each model can run on CPU, GPU, or NPU independently. The choice is stored per model and used for every inference session.

**Hard Offline Mode**
A toggle in Settings forces the app into a fully airgapped state — all download attempts throw an exception and no network calls are made. Useful in sensitive environments.

**AI Chat** (from upstream)
Multi-turn conversations with on-device LLMs. Supports Thinking Mode on compatible models (Gemma 4 family). Full markdown rendering of responses.

**Ask Image / Audio Scribe** (from upstream)
Multimodal image Q&A and audio transcription using on-device models.

**Agent Skills** (from upstream)
Augment the model with tool use: Wikipedia, maps, visual cards, and community-contributed skill packs loadable from a URL.

**Prompt Lab** (from upstream)
Single-turn prompt sandbox with temperature, top-k, and other parameter controls.

---

## Getting Started

### Requirements

- Android 16+ or higher
- ~4 GB of free storage for a typical quantised LLM

### Build from source

```bash
git clone --recurse-submodules https://github.com/jegly/box
cd box/Android
./gradlew :app:assembleDebug
```

The `--recurse-submodules` flag is required to pull the llama.cpp submodule.

Open `Android/` in Android Studio (Ladybug or newer) and run on a physical device for best performance.

### Loading a GGUF model

1. Copy a `.gguf` file to your device (Downloads folder, USB, etc.)
2. Open the app and tap **Model Manager** in the drawer
3. Tap **Import** and pick your file
4. Set a display name and choose CPU / GPU / NPU
5. Tap **Import** — the model appears in the AI Chat task

---

## Security Architecture

| Mechanism | Details |
|---|---|
| Database encryption | SQLCipher via `androidx.room` — AES-256 at rest |
| Biometric gate | `BiometricPrompt` API, re-prompts on each foreground |
| Offline mode | `OfflineMode` singleton blocks `DownloadWorker` and `OkHttp` calls |
| Prompt sanitisation | `SecurityUtils.sanitizePrompt()` strips control characters before inference and persistence |
| Tapjacking protection | `filterTouchesWhenObscured` set on the chat scaffold |
| Audit log | `SecurityAuditLog` writes security events to a local append-only log |

---

## Technology Stack

- **Kotlin + Jetpack Compose** — UI
- **Hilt** — dependency injection
- **Room + SQLCipher** — encrypted persistence
- **LiteRT (TFLite)** — upstream inference runtime
- **llama.cpp** — GGUF inference (bundled as a git submodule)
- **Firebase Analytics** — anonymous usage stats (can be disabled via Offline Mode)
---

---

## Upstream

This project is a fork of [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery). Upstream improvements are periodically merged. The upstream app is available on [Google Play](https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery) and the [App Store](https://apps.apple.com/us/app/google-ai-edge-gallery/id6749645337).

---

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

---

## Links

- [Box repository](https://github.com/jegly/box)
- [Upstream: google-ai-edge/gallery](https://github.com/google-ai-edge/gallery)
- [llama.cpp](https://github.com/ggml-org/llama.cpp)
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
- [Hugging Face LiteRT Community](https://huggingface.co/litert-community)
