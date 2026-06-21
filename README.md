# RG505 Input Mapper

Android app + Magisk backend for GammaOS/Anbernic RG505 input remapping.

## Current UI

- Installs or updates the Magisk backend when needed.
- Shows the currently applied preset and version.
- Supports versioned presets. Saving does not increment the version. Applying saves, increments only if the preset changed since the last applied version, writes backend config, and restarts the mapper.
- Preset editor supports joystick-to-mouse learning, advanced mouse options, and arbitrary key/mouse mappings.
- Target fields use autocomplete for common keyboard and mouse outputs.

## Build on GitHub

Push this repo to GitHub and run the `Build APK` workflow from the Actions tab. The workflow compiles the native ARM64 daemon and builds `app-debug.apk`.

## On device

Install the APK on the RG505, grant root, and tap Install or Update. Create/edit a preset, tap Apply, then restart if needed after launching the game.
