# First push to GitHub (public repository)

This project package is ready to upload, but it has not been published to any GitHub account yet. These steps create a **public** repository, so the source code, build logs and release APKs are visible to everyone, and your friends can download releases without a GitHub account. Everything you push, including history, is public, so never commit passwords, keystores or your real Firebase config (they are git-ignored already).

## 1. Create the empty public repository

On GitHub, choose **New repository**, name it `74LS-Logic-Lab`, and set visibility to **Public**. Do not add a README, `.gitignore`, or license, because these files are already in this package.

(If you prefer the GitHub CLI, skip to option B in step 2. It creates the repository for you.)

## 2. Push this folder

Open a terminal in the extracted `74LSLogicLab_GitHub_Starter` folder.

**Option A: repository created on the website**

```bash
git init -b main
git add .
git status --short
git commit -m "Initial 74LS Logic Lab web and Android release workflows"
git remote add origin https://github.com/YOUR-USERNAME/74LS-Logic-Lab.git
git push -u origin main
```

**Option B: GitHub CLI** (run `gh auth login` once first). This creates the public repository and pushes in one step:

```bash
git init -b main
git add .
git status --short
git commit -m "Initial 74LS Logic Lab web and Android release workflows"
gh repo create 74LS-Logic-Lab --public --source=. --remote=origin --push
```

Review `git status --short` before committing. The real Firebase config, keystores, APKs, and build outputs are ignored. Do not force-add them.

Public repositories also get a license question: with no `LICENSE` file, others can view and download the project but have no right to reuse the code. Add a `LICENSE` file if you want to allow that.

## 3. Get your first APK (no setup needed)

Open the repository's **Actions** tab. The `CI` workflow validates the HTML / JavaScript and builds a debug APK. When the run is green, open it and download the `74ls-logic-lab-debug-apk` artifact from the bottom of the page. It is a zip containing `app-debug.apk`; unzip it and install the APK on your phone.

Workflow artifacts always require a signed-in GitHub account to download, even on a public repository, so give friends the release APK from step 4 instead of this one.

For the web version, open **Settings → Pages** and set the source to **GitHub Actions**. The deployment job publishes the root `index.html` on pushes to `main`, and the URL is shown in the workflow's deployment summary.

## 4. Signed APK on the Releases page

A tagged release builds a **signed** APK and attaches it to **Releases**. This needs a release key, created once.

### 4a. Create the release keystore

Create it **outside** the project folder, and keep a protected offline backup. If you lose it, you cannot publish updates that install over the old app.

```bash
keytool -genkeypair -v -keystore release.keystore -alias logiclab -keyalg RSA -keysize 2048 -validity 10000
```

- When asked for the key password, press **Return** to use the same password as the keystore. Then `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_PASSWORD` below are the same value.
- On Windows, if `keytool` is not recognised, use the copy bundled with Android Studio, for example `"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`.

Encode it as a single line of Base64 and copy the result:

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

```bash
# macOS
base64 -i release.keystore | pbcopy

# Linux
base64 -w0 release.keystore
```

### 4b. Add the repository secrets

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the Base64 text from the step above |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password you chose |
| `ANDROID_KEY_ALIAS` | `logiclab` (or the alias you used) |
| `ANDROID_KEY_PASSWORD` | the key password (same as the keystore password if you pressed Return) |

For live Firebase sign-in and notifications, also add `FIREBASE_GOOGLE_SERVICES_JSON`. For the optional automatic release announcement, add `FIREBASE_SERVICE_ACCOUNT_JSON` and set the repository Actions variable `SEND_FCM_ON_RELEASE` to `true`.

### 4c. Tag a release

Check that CI is green on the commit you want to release, then:

```bash
git tag v1.1.0
git push origin v1.1.0
```

(`v1.1.0` matches the version in `CHANGELOG.md`. Use the next number for each later release, for example `v1.1.1`.)

The `Publish release` workflow builds the signed APK and creates the release. Open the repository's **Releases** page and download `74LS-Logic-Lab-android-v1.1.0.apk`. Because the repository is public, you can send that Releases page link to your friends; they do not need a GitHub account. The first time, Android will ask them to allow installing apps from their browser or file manager ("Install unknown apps"), and Google Play Protect may show a warning for apps that did not come from the Play Store.

### 4d. If you tested with the debug APK first

A debug APK and a release APK are signed with different keys, and Android cannot update one over the other. Before you install the first signed release, export your progress JSON from the Profile screen in the debug app, install the release APK, then use **Restore from backup**.
