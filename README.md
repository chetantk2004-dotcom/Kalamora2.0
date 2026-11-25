# Kalamora – कलम की दुनिया

**Every Word Has a Story.**

Kalamora is a **native Android application** built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room** (offline draft safety), and **Firebase** (Google Sign-In via Jetpack Credential Manager + Cloud Firestore real-time sync).

> **Architecture Note:** Because Kalamora is already a native Android Kotlin/Gradle application (not a React/Vite web app), **Node.js and Capacitor are NOT required** to build or package the Android APK. The Gradle Android project lives directly at the repository root (`app/`).

---

## Key Features

- **Shayari Writing Studio:** Create, edit, autosave, favorite, search, filter by category, toggle privacy, export Unicode/Hindi PDFs, generate high-resolution shareable Shayari PNG cards (`1080x1350`), and share via Android Share Sheet.
- **Multi-Chapter Book Writing Studio:** Manage books with custom metadata and cover accents, write independent chapters with live autosave and rich Markdown-style formatting, reorder chapters, and export full multi-chapter Book PDFs (Cover + Table of Contents + numbered pages) with full Hindi/Devanagari Unicode support.
- **Cloud Sync & Offline Safety:** Real-time Cloud Firestore synchronization per authenticated Google account, paired with local Room draft recovery.
- **Typography & Design:** Warm paper & espresso editorial palette, bundled Playfair Display, Plus Jakarta Sans, and Noto Serif/Sans Devanagari fonts, adaptive Material 3 navigation, custom adaptive launcher icon, and branded splash screen.

---

## Android App Configuration

| Setting | Value |
| --- | --- |
| **App Name** | `Kalamora` |
| **Application ID** | `com.aistudio.kalamora.vqmrxz` |
| **Version** | `1.0.0` (`versionCode = 1`) |
| **Min SDK / Target SDK / Compile SDK** | `24` / `36` / `36.1` |
| **Android Gradle Plugin / Gradle** | `9.1.1` / `9.3.1` |
| **Kotlin** | `2.4.21` |

> **Why `com.aistudio.kalamora.vqmrxz`?** The provisioned Firebase project (`app/google-services.json`) and Google Sign-In OAuth client are bound to `com.aistudio.kalamora.vqmrxz`. If you later migrate to `com.kalamora.app`, add `com.kalamora.app` with your release SHA-1/SHA-256 in Firebase Console and replace `app/google-services.json`.

---

## Requirements (Local Builds)

1. **Java JDK 21** (Eclipse Temurin 21 recommended; JDK 17 minimum)
2. **Gradle 9.3.1** (or Android Studio Ladybug / Meerkat+ which manages Gradle automatically)
3. **Android SDK** with Platform API 36 (minor API level 1) and latest Build-Tools
4. *(Optional)* **Node.js 20+** and **Firebase CLI** — only needed if you want to run the local Firestore security rules test suite (`firestore.test.js`) against the Firestore emulator.

---

## Firebase Configuration

- Client-side Android configuration is already included in `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`.
- Firestore security rules are defined in `firestore.rules` (schema blueprint in `firebase-blueprint.json`).
- **Important for Signed Release Builds:** Google Sign-In verifies your app's signing certificate fingerprint. After creating your release upload keystore (`my-upload-key.jks`), extract its SHA-1 and SHA-256 fingerprints and add them to your Android app in **Firebase Console → Project Settings → Your apps → SHA certificate fingerprints**, then download the updated `app/google-services.json`:
  ```bash
  keytool -list -v -keystore my-upload-key.jks -alias upload
  ```
- **Never commit** private service account JSON files, `.env` files with secrets, or `.jks`/`.keystore` signing files.

---

## Local Development & APK Commands

### 1. Run Unit Tests
```bash
gradle :app:testDebugUnitTest --tests com.example.KalamoraUnitTest
```

### 2. Build a Debug APK Locally
First decode the debug keystore (if `debug.keystore` is not already in the project root):
```bash
tr -d '\n\r ' < debug.keystore.base64 | base64 --decode > debug.keystore
gradle :app:assembleDebug
```
Output:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 3. Generate a Release Signing Keystore (One-Time Setup)
`app/build.gradle.kts` expects the key alias to be **`upload`**:
```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### 4. Build a Signed Release APK Locally
```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"
gradle :app:assembleRelease
```
Output:
```text
app/build/outputs/apk/release/app-release.apk
```

---

## Automated APK Build with GitHub Actions

The workflow file `.github/workflows/android-apk.yml` automatically compiles, tests, verifies signatures, and uploads the APK as a downloadable GitHub Actions artifact.

### Step 1: Push Project to GitHub
1. Export/push this project to a new GitHub repository (via Google AI Studio's **Push to GitHub** / **Export ZIP** option, or `git init && git remote add origin <repo-url> && git push -u origin main`).
2. Confirm that private files (`.env`, `*.jks`, `debug.keystore`, `build/`) are ignored by `.gitignore`.

### Step 2: Configure Release Signing Secrets in GitHub
Encode your `my-upload-key.jks` file to Base64:
- **macOS / Linux:**
  ```bash
  base64 -w 0 my-upload-key.jks > keystore.b64   # Linux
  base64 -i my-upload-key.jks | tr -d '\n'      # macOS
  ```
- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))
  ```

In your GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret Name | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 contents of `my-upload-key.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Your keystore password |
| `ANDROID_KEY_ALIAS` | `upload` |
| `ANDROID_KEY_PASSWORD` | Your key password |

### Step 3: Run the Workflow
- **Signed Release APK:** Push to `main` (or open **Actions → Build Kalamora Android APK → Run workflow → build_type: release**).
  - Produces signed `app/build/outputs/apk/release/app-release.apk`
  - Uploaded artifact name: **`KALAMORA-ANDROID-APK`**
- **Quick Test Debug APK (No Release Keystore Needed):** Open **Actions → Build Kalamora Android APK → Run workflow → build_type: debug**.
  - Produces `app/build/outputs/apk/debug/app-debug.apk` signed with `debug.keystore.base64` (whose SHA-1 is already registered with Firebase Google Sign-In)
  - Uploaded artifact name: **`KALAMORA-ANDROID-DEBUG-APK`**

---

## Downloading & Installing the APK on Your Android Phone

1. Open your GitHub repository → **Actions** tab.
2. Click the latest green (succeeded) **Build Kalamora Android APK** workflow run.
3. Scroll down to **Artifacts** and download **`KALAMORA-ANDROID-APK`** (or `KALAMORA-ANDROID-DEBUG-APK`).
4. Unzip the downloaded `.zip` archive to extract `app-release.apk` (or `app-debug.apk`).
5. Transfer the `.apk` file to your Android phone (or download directly on your phone browser).
6. Tap the `.apk` file to install. If Android asks for permission, enable **Install unknown apps** for your browser/File Manager and confirm installation.
