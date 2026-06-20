# RG505 Input Mapper

Root/Magisk controller-to-keyboard/mouse mapper for GammaOS Next / Anbernic RG505.

## What this builds

- Android APK UI for installing/configuring the backend
- Native ARM64 daemon `rg505_mapperd`
- Magisk module assets packaged inside the APK

## GitHub build

1. Create a new GitHub repository.
2. Upload/push all files in this folder.
3. Open the repository on GitHub.
4. Go to **Actions**.
5. Run **Build APK**.
6. Download the artifact named `rg505-input-mapper-debug-apk`.
7. Install `app-debug.apk` on the RG505.
8. Open the app and tap **Install / Update Backend**.

## Initial Starbound setup

The default config maps:

- D-pad to WASD
- right stick / configured `ABS_Z` + `ABS_RZ` to virtual mouse movement

Use the app to apply/restart after Starbound is running if needed.
