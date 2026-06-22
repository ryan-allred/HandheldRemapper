# Handheld Remapper

Handheld Remapper is an Android app for remapping controller input on rooted Android gaming handhelds. It installs a small Magisk-backed native daemon that reads controller events and emits virtual keyboard and mouse input.

The app was developed and tested on the Anbernic RG505 running GammaOS. It should work on any Android device with standard controller inputs, but it has not been tested on other devices. If you run into any bugs, feel free to open an issue.

## Screenshots

TODO: Add a screenshot of the main preset list with backend status and the currently applied preset.

TODO: Add a screenshot of the preset editor with mappings, preset behavior, and mouse-stick settings.

## Features

- Input mapping
    - Map buttons or joysticks to:
      - Keyboard keys
      - Mouse buttons
      - Mouse wheel up/down
    - Map an analog stick to mouse movement.
    - Optionally block the original controller input so games only receive the remapped output.
- Preset management
  - Create, edit, and apply multiple remapping presets.
  - Export and import presets as JSON backups.
- Magisk module management
  - Install and update the backend Magisk module via the app.
  - Restart or stop the backend service.

## Requirements

- Rooted Android device with a controller
- Magisk
- Android 8.0 or newer

## Downloading The APK

Download the latest APK from the [releases page](https://github.com/ryan-allred/handheldremapper/releases).

NOTE: It is generally a bad idea to install APKs you find on github, especially if they require root access. For this project, 100% of the code is open source and the APK is built directly via Github Actions. This app should be relatively safe, but you are installing it at your own risk.

## Building From Source

Local builds require an installed Android SDK and Android NDK. Gradle builds the native backend before packaging the APK.

## Using The App

1. Install the APK on the handheld.
2. Open Handheld Remapper and grant root.
3. Install the backend when prompted.
4. Create or edit a preset.
5. Use the mapping listener to bind controller inputs to keyboard or mouse targets.
6. Use Learn stick if you want analog stick mouse movement.
7. Tap Apply to write the preset and restart the backend service.

## Stopping and/or Uninstalling The App
- To stop the remapper, press the stop button in the app.
- To uninstall the app, uninstall the app and remove the module from Magisk.
