# Aura Keyboard (Android)

A native Android keyboard you can use every day. It is private by design: the app has no internet permission.

## Features
- Full QWERTY with shift and caps lock (double-tap shift), numbers and symbols, and a second symbols page.
- Optional number row, key height setting, light, dark or system theme.
- Long-press any key for accents and symbols, with key preview pop-ups.
- Word suggestions from a built-in word list plus words you type often. Nothing leaves the device.
- Auto-capitalize at the start of sentences, double space for a period, no space before punctuation after a suggestion.
- Slide on the space bar to move the cursor. Backspace repeats when held, and deletes words when held longer or swiped left.
- Clipboard history (20 clips, pin the ones you keep). Sensitive copies are skipped on Android 13 and newer.
- Emoji drawer with recents, voice typing, and the right action key (Go, Search, Send, Next, Done).
- Password fields turn off suggestions, learning and key previews.

## Build
GitHub Actions builds a debug APK on every push to `main` and publishes it under **Releases > Assets**.
To build locally, open the folder in Android Studio and run the `app` configuration, or run `gradle :app:assembleDebug` with Gradle 8.9 and JDK 17.

## Turn it on
1. Install the APK and open **Aura Keyboard**.
2. Tap **Open keyboard settings** and switch Aura Keyboard on.
3. Tap **Choose keyboard** and pick Aura Keyboard.

## Known limits
- English (India) layout only. No gesture typing, autocorrect or next-word prediction yet.
- Not tested on real devices when this was written. The first run may need small fixes.
