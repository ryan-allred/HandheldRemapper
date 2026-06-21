# Handheld Remapper

Handheld Remapper is an Android app for remapping controller input on rooted Android gaming handhelds. It installs a small Magisk-backed native daemon that reads controller events and emits virtual keyboard and mouse input.

The app was developed and tested on the Anbernic RG505 running GammaOS. Other rooted Android handhelds may work if their controls are exposed through Android input events.

## Screenshots

TODO: Add a screenshot of the main preset list with backend status and the currently applied preset.

TODO: Add a screenshot of the preset editor with mappings, preset behavior, and mouse-stick settings.

## Features

- Create and apply multiple remapping presets.
- Map buttons or axis directions to keyboard keys, mouse buttons, mouse movement, and mouse wheel up/down.
- Listen for controller input when creating mappings.
- Learn analog stick axes and range for mouse movement.
- Optionally block the original controller input so games only receive the remapped output.
- Export and import presets as JSON backups.
- Install and update the bundled backend from inside the app.

## Requirements

- Rooted Android handheld.
- Magisk.
- Android 8.0 or newer.
- Controller input exposed through Android input events.

## Downloading An APK

Open the latest successful GitHub Actions run for the `Build APK` workflow and download the APK artifact. Install that APK on the handheld, then open Handheld Remapper and grant root when prompted.

## Building From Source

The GitHub Actions workflow is the recommended build path because it compiles the native ARM64 backend and packages it into the APK.

1. Fork or clone this repository.
2. Push it to GitHub.
3. Open the Actions tab.
4. Run the `Build APK` workflow.
5. Download the generated APK artifact.

For local Android-only builds:

```sh
./gradlew :app:assembleDebug
```

Local builds require an installed Android SDK. The native backend is built by the GitHub workflow.

## Using The App

1. Install the APK on the handheld.
2. Open Handheld Remapper and grant root.
3. Install the backend when prompted.
4. Create or edit a preset.
5. Use the mapping listener to bind controller inputs to keyboard or mouse targets.
6. Use Learn stick if you want analog stick mouse movement.
7. Tap Apply to write the preset and restart the backend.

If you update the backend later, the app will reapply the current preset when possible.

## Notes

This project is still early and currently tested on one device. If a mapping does not behave as expected, use the Debug screen in the app to collect backend status, logs, input devices, and direct wheel-test output.
