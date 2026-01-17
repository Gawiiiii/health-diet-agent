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

## Coding Guidelines for Future Changes

Follow these rules for any new UI actions or demo features:

- Keep UI state changes in ViewModel methods. UI should only call ViewModel APIs.
- Store demo/sample data as private constants in the ViewModel.
- Avoid adding business logic directly in Composables.
- Maintain JVM target alignment (Java 11 and Kotlin 11).
- Keep Gradle configuration consistent with disabled built-in Kotlin and KAPT.
- Use ASCII-only strings and identifiers unless non-ASCII is required.
