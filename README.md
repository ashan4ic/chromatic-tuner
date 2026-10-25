# Chromatic Tuner 🎸

A minimalist chromatic guitar tuner for Android, built with **Kotlin** and **Jetpack Compose**.

> **Status: v0.1 — UI prototype.** The tuner screen and launcher icon are implemented, but sound analysis is not available yet. All pitch readings are demo values.

## v0.1 — Studio Dark UI

The first version introduces the approved dark interface:

- Chromatic tuner screen with a graphite background and mint-green accents
- Note and octave display (`E2`)
- Frequency display (`84.24 Hz`)
- Pitch deviation scale from `-50` to `+50` cents
- Highlighted marker and tuning hint (currently using demo data: `+38 cents`)
- Visual placeholders for tuning mode, A4 reference frequency, microphone, and settings
- Custom launcher icon
- Layout designed for different phone heights

**Not implemented yet:** microphone capture, real-time pitch detection, automatic note recognition, and interactive tuning settings. The microphone is off in v0.1.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose / Material 3
- **Build:** Gradle with Kotlin DSL
- **Platform:** Android (minimum API 26; compile SDK 36)
- **Development:** VS Code, PowerShell, Git, and GitHub

## Build

Requirements:

- JDK 17
- Android SDK Platform 36 and compatible Android SDK Build Tools
- `ANDROID_HOME` configured to point to your Android SDK

Clone the repository from GitHub and, in its root directory, run:

```powershell
git switch feature/tuner-ui
.\gradlew.bat assembleDebug
```

Until the UI changes are merged into `main`, use `feature/tuner-ui` to build v0.1.

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To install it on a connected Android device with USB debugging enabled:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## Roadmap

- [x] Set up Android project and Gradle build
- [x] Build Studio Dark tuner UI
- [x] Add custom launcher icon
- [ ] Capture audio from the microphone
- [ ] Detect fundamental frequency and chromatic notes
- [ ] Display live tuning deviation in cents
- [ ] Add working mode and reference-frequency controls
- [ ] Test pitch detection and calibration on physical devices

## Development

UI work is developed in the `feature/tuner-ui` branch before being merged into `main`.

This is a learning project focused on Android development, Kotlin, and Git workflows.