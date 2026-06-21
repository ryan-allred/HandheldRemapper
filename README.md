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
GitHub APKs are signed with the checked-in development update key and use the GitHub run number as the Android `versionCode`, so future artifacts can update the installed app in place.

## On device

Install the APK on the RG505, grant root, and tap Install or Update. Create/edit a preset, tap Apply, then restart if needed after launching the game.
If you installed an older artifact signed by a random debug key, Android may require one final uninstall before the first APK signed with this repo key can be installed. After that, updates should install over the previous app.
