# Debug Log

This document records build and runtime issues encountered during setup,
with root causes and resolutions. It also defines the conventions for
future fixes and demo changes.

## Scope

- Project: `MyApplication`
- Platforms: Android app + FastAPI backend
- Goal: Stable demo run on Android Studio emulator

## Environment

- Android Gradle Plugin: 9.0.0
- Kotlin: 2.0.21
- Java target: 11
- Room: 2.7.0

## Issue Summary

| ID | Symptom | Root Cause | Resolution | Status |
| --- | --- | --- | --- | --- |
| DBG-001 | `Cannot add extension with name 'kotlin'` | Built-in Kotlin already registered the extension | Disable built-in Kotlin, keep `kotlin-android` plugin | Resolved |
| DBG-002 | `kapt` incompatible with built-in Kotlin | Built-in Kotlin blocks `kapt` | Disable built-in Kotlin, use `kotlin-kapt` | Resolved |
| DBG-003 | `kotlin.sourceSets DSL` not allowed | KSP adds Kotlin source sets under built-in Kotlin | Stop using KSP, use KAPT | Resolved |
| DBG-004 | `CameraController.PREVIEW` unresolved | Constant not exposed by current camera-view version | Remove `PREVIEW` flag | Resolved |
| DBG-005 | Room DAO signature mismatch | Kotlin wildcards in DAO methods | Add `@JvmSuppressWildcards` to DAO | Resolved |
| DBG-006 | Room metadata version mismatch | Room 2.6.1 did not support Kotlin 2.0 metadata | Upgrade Room to 2.7.0 | Resolved |
| DBG-007 | JVM target mismatch (11 vs 21) | Kotlin default target did not match Java target | Set `kotlinOptions.jvmTarget = "11"` | Resolved |
| DBG-008 | `Analyze Image` returns 501/timeout | HF model loading/large image upload and insufficient timeouts | Add HF `wait_for_model`, retries, larger timeouts, and client-side image downscale | Resolved |
| DBG-009 | `Analyze Image` returns 502 | HF API error masked by generic message | Surface HF error details to client and logs | Resolved |
| DBG-010 | HF caption call still times out | HF queue delays and payload size still exceed timeout | Add configurable HF timeouts, retry on timeout, tighter image compression | Resolved |
| DBG-011 | SoruxGPT returns 413 on smoke test | Base64 image payload exceeded gateway size limit | Downscale/compress image before upload in smoke test | Resolved |
| DBG-012 | SoruxGPT smoke test KeyError | JSON schema braces not escaped in prompt formatting | Escape braces in prompt templates | Resolved |

## Issue Details

### DBG-001: Kotlin extension conflict

- Symptom: Build failed when applying `org.jetbrains.kotlin.android`.
- Root cause: Built-in Kotlin in AGP already created the `kotlin` extension.
- Resolution:
  - Disable built-in Kotlin in `gradle.properties`.
  - Apply `kotlin-android` plugin normally.
- Files:
  - `gradle.properties`
  - `build.gradle.kts`
  - `app/build.gradle.kts`

### DBG-002: KAPT incompatible with built-in Kotlin

- Symptom: `org.jetbrains.kotlin.kapt` plugin not compatible with built-in Kotlin.
- Root cause: Built-in Kotlin explicitly blocks `kapt`.
- Resolution:
  - Disable built-in Kotlin.
  - Use `kotlin-kapt` and `kapt(...)` for Room.
- Files:
  - `gradle.properties`
  - `gradle/libs.versions.toml`
  - `build.gradle.kts`
  - `app/build.gradle.kts`

### DBG-003: Kotlin source sets blocked

- Symptom: "Using kotlin.sourceSets DSL is not allowed with built-in Kotlin."
- Root cause: KSP adds generated Kotlin sources via `kotlin.sourceSets`.
- Resolution:
  - Stop using KSP.
  - Use KAPT instead.
- Files:
  - `gradle/libs.versions.toml`
  - `app/build.gradle.kts`

### DBG-004: CameraController.PREVIEW unresolved

- Symptom: `CameraController.PREVIEW` not found at compile time.
- Root cause: Preview flag is not exposed by the current `camera-view` version.
- Resolution: Enable only `IMAGE_CAPTURE`; preview stays bound by controller.
- Files:
  - `app/src/main/java/com/example/myapplication/ui/screens/CaptureScreen.kt`

### DBG-005: Room DAO signature mismatch

- Symptom: Generated `AnalysisDao_Impl` did not override DAO methods.
- Root cause: Kotlin wildcard types created JVM signature mismatch.
- Resolution: Add `@JvmSuppressWildcards` to `AnalysisDao`.
- Files:
  - `app/src/main/java/com/example/myapplication/data/local/AnalysisDao.kt`

### DBG-006: Room metadata version mismatch

- Symptom: "Provided Metadata instance has version 2.2.0, maximum supported 2.0.0."
- Root cause: Room 2.6.1 did not support Kotlin 2.0 metadata.
- Resolution: Upgrade Room to 2.7.0.
- Files:
  - `gradle/libs.versions.toml`

### DBG-007: JVM target mismatch

- Symptom: Kotlin and Java tasks used different JVM targets (21 vs 11).
- Root cause: Kotlin target default differed from Java `compileOptions`.
- Resolution: Set Kotlin `jvmTarget` to 11.
- Files:
  - `app/build.gradle.kts`

### DBG-008: Hugging Face timeout / 501 errors

- Symptom: `Analyze Image (Cloud)` returns 501 or times out.
- Root cause:
  - HF Inference API cold-start/model loading.
  - Large images causing slow inference and network timeouts.
  - Missing HF token or model configuration.
- Resolution:
  - Increase client and server timeouts.
  - Enable `wait_for_model` and retry in HF requests.
  - Downscale images to max 768px and compress to JPEG quality 75 before upload.
- Files:
  - `app/src/main/java/com/example/myapplication/AppContainer.kt`
  - `app/src/main/java/com/example/myapplication/data/image/ImagePreprocessor.kt`
  - `app/src/main/java/com/example/myapplication/viewmodel/CaptureViewModel.kt`
  - `server/main.py`

### DBG-009: 502 error detail visibility

- Symptom: App only showed "502 Bad Gateway" without reason.
- Root cause: HF error details were not surfaced to client.
- Resolution:
  - Attach HF status and error text to response detail.
  - Display HTTP error body in the app.
- Files:
  - `server/main.py`
  - `app/src/main/java/com/example/myapplication/viewmodel/CaptureViewModel.kt`

### DBG-010: HF caption timeouts

- Symptom: HF caption request timed out and the app reported "OpenAI not configured."
- Root cause: HF inference queue exceeded the fixed timeout for image uploads.
- Resolution:
  - Add per-endpoint timeout env vars and retry on timeout.
  - Compress images more aggressively before upload.
  - Increase client read/write timeouts.
- Files:
  - `server/main.py`
  - `app/src/main/java/com/example/myapplication/data/image/ImagePreprocessor.kt`
  - `app/src/main/java/com/example/myapplication/AppContainer.kt`

### DBG-011: SoruxGPT 413 on image upload

- Symptom: SoruxGPT image caption request returned HTTP 413.
- Root cause: Base64 data URL was too large for the gateway.
- Resolution:
  - Downscale and JPEG-compress images in the SoruxGPT smoke test.
  - Adjust `--max-size` and `--quality` to control payload size.
- Files:
  - `server/soruxgpt_smoke_test.py`

### DBG-012: SoruxGPT smoke test KeyError

- Symptom: `KeyError: '"menu_items"'` while building prompt.
- Root cause: Python `str.format` treated JSON braces as format tokens.
- Resolution: Escape JSON braces in prompt templates with `{{` and `}}`.
- Files:
  - `server/soruxgpt_smoke_test.py`
  - `server/main.py`

## Coding Guidelines for Future Changes

Follow these rules for any new UI actions or demo features:

- Keep UI state changes in ViewModel methods. UI should only call ViewModel APIs.
- Store demo/sample data as private constants in the ViewModel.
- Avoid adding business logic directly in Composables.
- Maintain JVM target alignment (Java 11 and Kotlin 11).
- Keep Gradle configuration consistent with disabled built-in Kotlin and KAPT.
- Use ASCII-only strings and identifiers unless non-ASCII is required.
