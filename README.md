# 74LS Logic Lab

**Learn digital logic, build circuits, and explore TTL PCB layout.** This repository contains the checked single-file web application and the Android Studio WebView wrapper.

## Project layout

- `index.html` — web app source; this is what GitHub Pages publishes.
- `android/` — Android Studio project. `android/app/src/main/assets/www/index.html` must match the root `index.html`.
- `.github/workflows/ci.yml` — web checks and Android debug APK build on pushes and pull requests.
- `.github/workflows/pages.yml` — deploys the web version from `main` using GitHub Pages.
- `.github/workflows/release.yml` — builds a signed Android APK and attaches it plus the HTML app to a GitHub Release when a version tag is pushed.
- `scripts/` — source checks, synchronized-file checks, release version parsing, and optional Firebase update announcement.

## Set up the public GitHub repository

1. Create a **public** repository named `74LS-Logic-Lab` (or another name you prefer).
2. Upload or push the contents of this folder to the repository's default branch named `main`.
3. In **Settings → Pages**, set **Build and deployment → Source** to **GitHub Actions**.
4. Open the **Actions** tab. The `CI` workflow should run on the first push. It checks inline JavaScript syntax and project consistency, then builds a debug APK artifact.
5. After the Pages workflow succeeds, the web app will be available at `https://YOUR-GITHUB-USERNAME.github.io/74LS-Logic-Lab/` (the exact URL is shown in Actions → Deploy web app → deployment summary).

Public visibility means the source is visible to everyone. It does not automatically grant others a license to reuse it. Choose and add a `LICENSE` file before inviting redistribution or third-party contributions.

## Getting the APK onto a phone

- **For friends and players:** open the repository's **Releases** page, choose the latest release and download `74LS-Logic-Lab-android-vX.Y.Z.apk`. Because the repository is public, no GitHub account is needed. On first install Android will ask to allow "Install unknown apps" for the browser or file manager, and Google Play Protect may show a warning for apps that did not come from the Play Store.
- **Test builds:** Actions → latest green `CI` run → Artifacts → `74ls-logic-lab-debug-apk`. This is a zip containing `app-debug.apk` and expires after 14 days. Downloading workflow artifacts always requires a signed-in GitHub account, even on a public repository, so share the release APK rather than this one.
- A release exists only after the signing secrets below are added and a version tag is pushed. Step-by-step commands for creating the key and adding the secrets are in `docs/FIRST_PUSH.md`.

## Android project setup

Open the `android/` directory in Android Studio. Install Android SDK Platform 36 and use JDK 17. The project currently uses Android Gradle Plugin 8.13.2 and Gradle 8.13.

Before a local Android build, create the ignored project-specific Firebase configuration file from the safe example:

- Windows PowerShell: `Copy-Item android/app/google-services.json.example android/app/google-services.json`
- macOS/Linux: `cp android/app/google-services.json.example android/app/google-services.json`

The CI workflow does this automatically if no Firebase configuration secret is provided. The example is intentionally filled with placeholder values; it is sufficient for compilation, not live sign-in or push notifications. To enable those services in CI/release builds, add the full JSON file contents as the Actions secret `FIREBASE_GOOGLE_SERVICES_JSON`. Do not commit your real local file.

The application ID must remain `com.seventyfourls.logiclab`. To preserve app-private data during a compatible in-place update, keep this ID and the same Android signing key for every installed update; install over the previous app instead of uninstalling or clearing its storage. Android local storage can still be lost if the user uninstalls the app, clears data, changes device, or installs an incompatible origin/schema. Ask users to export their JSON backup before important updates. Cloud cross-device progress sync is not implemented by this repository's local-save mechanism.

## GitHub Actions and releases

### Continuous checks

`CI` runs for pushes, pull requests, and manual dispatch. It:

- parses the full HTML and checks every inline JavaScript block with Node;
- checks that the root web file and Android-embedded copy are byte-for-byte identical;
- checks Android package/configuration files;
- builds `assembleDebug` and uploads `app-debug.apk` as a 14-day workflow artifact.

The debug artifact is for testing, not a public production release. It is unsigned with your private release key and is not a Play Store artifact.

### Web deployment

The `Deploy web app` workflow stages only the root `index.html` and publishes it to GitHub Pages from `main`. Go to **Settings → Pages** and choose **GitHub Actions** as the publishing source.

### Tagged production release

Use semantic version tags in the form `vMAJOR.MINOR.PATCH`, e.g. `v1.1.1` for the next release after the current app version `1.1.0`. Push a tag only after CI passes and the version is ready:

```bash
git tag v1.1.1
git push origin v1.1.1
```

The release workflow converts the tag into Android `versionName` and monotonically increasing-style `versionCode` (`major × 1,000,000 + minor × 1,000 + patch`; minor and patch must each be below 1,000). Do not release an older tag after a newer one. It builds a **signed** APK, then publishes the APK and versioned HTML file to GitHub Releases. If release signing secrets are absent, release creation fails instead of shipping a debug-signed APK.

Required repository Actions secrets for production APK releases:

- `ANDROID_KEYSTORE_BASE64` — Base64-encoded release `.jks`/`.keystore` file.
- `ANDROID_KEYSTORE_PASSWORD` — keystore password.
- `ANDROID_KEY_ALIAS` — release key alias.
- `ANDROID_KEY_PASSWORD` — private key password.

Use the **same keystore/key** for all future APKs that update this app. Store a separate encrypted/offline backup of that keystore; losing it can prevent in-place updates signed with the old certificate. Never commit keystore files, passwords, tokens, or private keys.

Optional Firebase Actions secrets:

- `FIREBASE_GOOGLE_SERVICES_JSON` — JSON contents for your Firebase Android app config. It configures Firebase SDKs but is not a substitute for protecting server-side secrets.
- `FIREBASE_SERVICE_ACCOUNT_JSON` — service-account JSON for the release workflow's optional FCM message. Grant only the permissions needed to send Firebase Cloud Messaging messages.

Optional repository Actions variable:

- `SEND_FCM_ON_RELEASE=true` — after a successful GitHub Release, send a release announcement to the app's `app_updates` topic. Leave unset/false until Firebase Messaging is configured and tested. Players must have a build with working Firebase configuration, be subscribed to the topic, grant Android notification permission where required, and have notification delivery allowed by the OS. The notification announces an update; it does not automatically install the APK.

Use **Settings → Secrets and variables → Actions** to add secrets and variables. Never put secrets in this README, workflow YAML, HTML, or Android resources.

## Progress preservation policy

- Preserve the app ID `com.seventyfourls.logiclab`.
- Keep the production signing key/certificate stable.
- Increase `versionCode` for every new production release; never reuse a code with different app contents.
- Keep the WebView local storage origin and saved-key schema compatible. Test upgrade migration before changing either.
- Keep Profile JSON export/restore working and remind players to back up important work.
- A GitHub source history protects source code only. It does not back up each player's circuit/progress data. Cross-device progress sync needs an authenticated cloud database, user-specific security rules, and a merge/conflict policy; it is not enabled here.

## Useful checks before each release

- CI passes for the exact commit to be tagged.
- Install the signed APK over the previous signed build and confirm circuits, projects, PCB data, credits, and profile persist.
- Export a JSON backup and restore it into a clean test install.
- Test Google/Yahoo/Facebook sign-in only after their Firebase/provider setup is complete.
- Test an `app_updates` notification on real Android versions, including notification permission on Android 13+.
- Confirm the published Pages site and release downloads work on a phone.

## Feature/configuration boundary

The web app and Android wrapper are included. Firebase providers and FCM require your own Firebase/provider configuration. This project does not currently sync saved progress to a cloud database and does not silently install APK updates. GitHub Pages is the public web release; GitHub Releases is the Android distribution page.
