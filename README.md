# Handheld Remapper

Handheld Remapper is an Android app and Magisk-backed native daemon for remapping controller input on Android gaming handhelds.

It provides a preset editor for mapping controller buttons and axes to keyboard keys, mouse buttons, mouse movement, and mouse wheel ticks. It was developed and tested on the Anbernic RG505 running GammaOS.

## Screenshots

TODO: Add a screenshot of the main preset list showing the applied preset and backend status.

TODO: Add a screenshot of the preset editor showing input mappings, the block-original-input toggle, and mouse-stick configuration.

## Features

- Create, edit, save, delete, and apply multiple input presets.
- Export and import the full preset list as a JSON backup.
- Apply presets without creating a new preset until the preset is explicitly saved.
- Map buttons or axis directions to keyboard keys, mouse buttons, and mouse wheel up/down.
- Learn the mouse-look stick axes and range from live controller input.
- Normalize stick movement across different hardware axis ranges.
- Optionally block the original controller input so only remapped keyboard/mouse output reaches the game.
- Install or update the bundled Magisk backend from inside the app.
- Show the currently applied preset and version.

## How It Works

The Android app manages presets and writes the active preset into the Magisk module config. The native backend reads the source controller event device, optionally grabs it with `EVIOCGRAB`, and emits virtual keyboard and mouse HID reports.

The backend module is bundled in the APK under `app/src/main/assets/module`.

## Requirements

- Rooted Android handheld.
- Magisk.
- Android 8.0 or newer.
- A controller input device exposed through Android input events.

The project has been tested on the Anbernic RG505 with GammaOS. Other rooted Android handhelds may work, but may need different source device names or axis selections.

## Building

The easiest way to build a full APK is the GitHub Actions workflow:

1. Push the repo to GitHub.
2. Open the Actions tab.
3. Run the `Build APK` workflow.
4. Download `app-debug.apk` from the workflow artifacts.

The workflow builds the native ARM64 daemon and packages it into the APK.

Local Android builds are also supported:

```sh
./gradlew :app:assembleDebug
```

Local builds require an installed Android SDK. Native daemon compilation is handled by the GitHub workflow.

## Updating Installed Builds

Debug APKs are signed with the checked-in development update key at `app/signing/androidremapper-dev.jks`. This lets new debug builds install over previous debug builds from this repo.

If an older APK was installed with a different debug key, Android may require one uninstall before the first APK signed with this repo key can be installed. After that, updates should install in place.

Version 5 renamed the Android package and Magisk module id from the original device-specific names. Uninstall older builds and remove the old backend module before installing version 5 or newer.

Do not commit `local.properties`; it is machine-specific SDK configuration.

## Using The App

1. Install the APK on the handheld.
2. Open Handheld Remapper and grant root when prompted.
3. Install or update the backend if requested.
4. Create or edit a preset.
5. Use the mapping listener to bind source inputs to target keys or mouse actions.
6. Use Learn stick if you want analog stick mouse movement.
7. Apply the preset.

If the backend is updated, apply the preset again so the daemon restarts with the latest config.
