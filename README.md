# Noir Glass Keyboard

An Android Input Method Editor (IME) inspired by the supplied reference image: a near-black rounded glass panel, muted ivory lettering, a compact top utility strip, three QWERTY rows, shift/delete end keys, and a wide space/return row.

## Build locally

Requirements: Android SDK Platform 35, JDK 17, and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Install and enable

1. Install the APK and open **Noir Glass Keyboard**.
2. Tap **Enable Noir Glass Keyboard** and enable it in Android's Input Method settings.
3. Return to the app and tap **Choose keyboard / Try it**.
4. Select **Noir Glass Keyboard**. You can switch to it later from the system keyboard picker.

The keyboard processes text locally and does not request Internet, microphone, contacts, or storage permissions. The microphone-like toolbar glyph is decorative; voice input is not implemented.

## GitHub Actions release

Push this project to a GitHub repository, then create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow in `.github/workflows/release.yml` builds and signs an installable APK, uploads it as a workflow artifact, and attaches it to the GitHub Release for the tag. The APK is also written to `release/NoirGlassKeyboard.apk` during the workflow. `workflow_dispatch` can build an artifact without publishing a Release.

**Signing note:** By default, the workflow generates a temporary signing key for each run. For repeatable app updates, add these repository secrets: `RELEASE_KEYSTORE_BASE64` (the base64-encoded persistent `.jks` file), `RELEASE_KEY_PASSWORD` (keystore/key password), and optionally `RELEASE_KEY_ALIAS` (defaults to `noirglass`). Without those secrets, each run is signed with a new temporary key, so a later APK cannot update an earlier install without uninstalling it first.
