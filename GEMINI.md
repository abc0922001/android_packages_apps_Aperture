# Aperture

## Project Overview
Aperture is the default camera application for LineageOS. It is a modern camera app built using AndroidX Camera (CameraX) and Media3 libraries, designed to provide a rich photography and videography experience. It supports standard photo/video capture, QR code scanning, and secure lock screen access.

**Key Technologies:**
- **Language:** Kotlin
- **Build System:** Gradle (Kotlin DSL)
- **Minimum SDK:** 26 (Android 8.0)
- **Target/Compile SDK:** 36 (Android 15)
- **Libraries:** AndroidX Camera (Camera2, Core, Video, View, Extensions), Media3 (ExoPlayer), Coil (Image Loading), ZXing (Barcode Scanning).

## Directory Structure
- `app/`: The main application source code.
  - `src/main/java/org/lineageos/aperture/`: Kotlin source files.
  - `src/main/res/`: Resources (layouts, drawables, values).
  - `libs/`: Local library dependencies (forks or specific versions of AndroidX libraries).
- `lens_launcher/`: Module for launching Google Lens or similar external lens activities.
- `rro_overlays/`: Runtime Resource Overlays for framework customization.
- `.github/workflows/`: CI/CD configurations.

## Building and Running

### Prerequisites
- JDK 17
- Android SDK

### Build Commands
To build the debug APK:
```bash
./gradlew assembleDebug
```
The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### AOSP Integration
This project is designed to be part of the LineageOS build system (AOSP). It uses a custom plugin to generate `Android.bp` files.
To generate the `Android.bp` file:
```bash
./gradlew app:generateBp
```

## Development Conventions

*   **Code Style:**
    *   Follows standard Kotlin coding conventions.
    *   Uses SPDX license headers for copyright notices.
*   **Version Control:**
    *   The project uses `libs.versions.toml` (Gradle Version Catalog) for managing dependencies in `gradle/libs.versions.toml`.
*   **Linting:**
    *   Strict linting rules are applied via `app/lint.xml`.
*   **Entry Points:**
    *   `CameraActivity`: The main entry point for the launcher.
    *   `CaptureActivity`: Handles external intents for image/video capture.
    *   `SecureCameraActivity`: Handles camera access from the lock screen.
