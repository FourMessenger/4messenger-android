# 4 Messenger — Android App

A native Android WebView wrapper for [fourmessenger.github.io](https://fourmessenger.github.io), built with **Rust** (JNI native library) and **Java** (Android WebView host), compiled into an APK for Android 8+.

---

## Features

| Feature | Implementation |
|---|---|
| **WebView renderer** | Android `WebView` with full JavaScript/TypeScript support |
| **Camera access** | `CAMERA` permission + `WebChromeClient.onPermissionRequest` |
| **Microphone access** | `RECORD_AUDIO` permission + WebRTC audio capture |
| **File access** | `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` + file chooser |
| **Push notifications** | `POST_NOTIFICATIONS` + `NotificationChannel` (Android 8+) |
| **Rust native library** | JNI `.so` compiled for arm64-v8a, armeabi-v7a, x86_64 |
| **App icon** | Matches the website icon: indigo rounded square with "4" |

---

## Requirements

- Android **8.0 (API 26)** or higher
- Internet connection to load the web app

---

## Installation

### Install via ADB

```bash
adb install FourMessenger-v1.0.0.apk
```

### Sideload (manual install)

1. Transfer `FourMessenger-v1.0.0.apk` to your Android device.
2. Enable **"Install from unknown sources"** in Settings → Security.
3. Open the APK file and follow the installation prompts.

---

## Build from Source

### Prerequisites

- Rust (stable) with Android targets:
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
  ```
- Android SDK (API 26+) and NDK (r25+)
- Java 17
- Gradle 8.4

### Build Rust native library

```bash
cd rust/
cargo build --release --target aarch64-linux-android
cargo build --release --target armv7-linux-androideabi
cargo build --release --target x86_64-linux-android

# Copy to jniLibs
cp target/aarch64-linux-android/release/libfourmessenger.so ../app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libfourmessenger.so ../app/src/main/jniLibs/armeabi-v7a/
cp target/x86_64-linux-android/release/libfourmessenger.so ../app/src/main/jniLibs/x86_64/
```

### Build APK

```bash
export JAVA_HOME=/path/to/java-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleRelease   # Release APK
./gradlew assembleDebug     # Debug APK
```

Output: `app/build/outputs/apk/release/app-release.apk`

---

## Project Structure

```
fourmessenger-android/
├── app/
│   ├── src/main/
│   │   ├── java/io/github/fourmessenger/
│   │   │   ├── MainActivity.java      # WebView host + permissions
│   │   │   ├── NativeLib.java         # JNI bridge to Rust
│   │   │   └── BootReceiver.java      # Notification channel on boot
│   │   ├── res/
│   │   │   ├── mipmap-*/              # App icons (all densities)
│   │   │   ├── values/strings.xml
│   │   │   └── xml/
│   │   │       ├── network_security_config.xml
│   │   │       └── file_paths.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── rust/
│   ├── src/lib.rs                     # Rust JNI native library
│   ├── Cargo.toml
│   └── .cargo/config.toml            # NDK cross-compilation config
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Permissions Requested

| Permission | Purpose |
|---|---|
| `INTERNET` | Load the web app |
| `CAMERA` | Video calls, photo capture |
| `RECORD_AUDIO` | Voice messages, audio calls |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | File sharing (Android 13+) |
| `READ/WRITE_EXTERNAL_STORAGE` | File sharing (Android 8–12) |
| `POST_NOTIFICATIONS` | Message notifications |
| `VIBRATE` | Notification vibration |
| `WAKE_LOCK` | Keep screen on during calls |
| `DOWNLOAD_WITHOUT_NOTIFICATION` | Silent file downloads |

---

## Technical Notes

- The app uses **Android WebView** (Chromium-based) to render the full web application, supporting all modern JavaScript/TypeScript features.
- The **Rust native library** (`libfourmessenger.so`) is compiled via Cargo with the Android NDK cross-compiler and loaded at runtime via JNI. It provides native utility functions (hashing, initialization) and can be extended with additional Rust logic.
- A **JavaScript bridge** (`AndroidBridge`) is injected into the WebView, allowing the web app to call native Android APIs for notifications, file downloads, and device info.
- The `WebChromeClient` handles all WebRTC permission requests (camera, microphone) by forwarding them to Android's runtime permission system.
