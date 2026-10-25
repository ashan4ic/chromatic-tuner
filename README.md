# 🎸 Chromatic Tuner

A chromatic guitar tuner for Android, developed with Kotlin and Jetpack Compose.

The goal is to build a lightweight, accurate and modern tuner with real-time pitch detection, a responsive interface and offline functionality.

## Project Status

**In Development — Initial Android Setup**

The Android project has been initialized, and the first debug APK builds successfully.

The current version contains the starter application interface. Audio processing and tuning functionality have not yet been implemented.

## Planned Features

- [ ] Chromatic pitch detection for all 12 notes
- [ ] Real-time microphone input
- [ ] Frequency detection in Hz
- [ ] Tuning accuracy display in cents
- [ ] Animated tuning indicator
- [ ] Standard guitar tuning mode (E–A–D–G–B–E)
- [ ] Adjustable reference frequency (A4 = 440 Hz)
- [ ] Dark-themed user interface
- [ ] Offline operation

## Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Build System:** Gradle (Kotlin DSL)
- **Platform:** Android
- **Minimum Android Version:** Android 8.0 (API 26)
- **Compile SDK:** Android API 36

## Building the Project

### Requirements

- JDK 17
- Android SDK Platform 36
- Android SDK Build Tools
- Android SDK Platform Tools
- Git

Set the `ANDROID_HOME` environment variable to your local Android SDK directory.

### Build Debug APK

Clone the repository, open the project directory in a terminal, and run:

`.\gradlew.bat assembleDebug`

The generated APK will be located at:

`app/build/outputs/apk/debug/app-debug.apk`

### Install via ADB

Connect an Android device with USB debugging enabled and run:

`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Development Roadmap

1. Project setup and first successful Android build
2. Tuner interface implementation
3. Microphone input and audio capture
4. Pitch detection algorithm
5. Chromatic note recognition and tuning accuracy
6. UI improvements and real-device testing

## Development Environment

The project is developed using Visual Studio Code, PowerShell, Git and GitHub, without requiring Android Studio.