# Aura Keyboard OS

Warm-minimalist keyboard web app, a small Node.js API, and an Android keyboard source file.

## Folders
- `web/AuraKeyboard-App.html` : the standalone keyboard app. Open it in a browser or host it on GitHub Pages.
- `server/` : Express API (`aura-keyboard-api.js`), its OpenAPI 3.0 spec (`aura-openapi.json`) and `package.json`.
- `android/AuraKeyboardIME.kt` : Kotlin `InputMethodService`. Add it to an Android Studio project (setup steps are in the file header). Not compiled or tested.

## Run the API
    cd server
    npm install
    npm start        # http://localhost:8787/v1/health

## Push to GitHub from Termux
    pkg update && pkg install -y git unzip
    termux-setup-storage
    cd ~/storage/downloads
    unzip aura-keyboard-os.zip
    cd aura-keyboard-os
    git init -b main
    git add .
    git commit -m "Add Aura Keyboard OS"
    git remote add origin https://github.com/YOUR_USER/YOUR_REPO.git
    git push -u origin main

GitHub asks for your username and a personal access token (not your password) when you push over HTTPS.

To publish the web app, enable GitHub Pages for the `main` branch in the repo settings, then open
`https://YOUR_USER.github.io/YOUR_REPO/web/AuraKeyboard-App.html`.
# Tstkbrd
