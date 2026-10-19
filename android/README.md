# 74LS Logic Lab — Android Studio project

This project wraps the existing 74LS Logic Lab HTML experience in a native Android WebView shell. It includes the updated profile page, progress backup/restore, native file saving, Firebase Cloud Messaging topic notifications, and provider sign-in hooks for Google, Yahoo, and Facebook.

## Included now

- Offline-first circuit editor, Practice Canvas, PCB Studio, rank/tier systems and the rest of the supplied HTML application.
- Profile screen using an original creator-style layout inspired by modern social profiles (not a pixel-for-pixel TikTok copy).
- Compact, fixed bottom navigation dock inspired by social apps: icon + short label, active-workspace highlight, horizontal swipe on narrow phones, and safe-area spacing; the dock hides during canvas fullscreen.
- Progress auto-save of canvas snapshots, saved projects and PCB state to WebView local storage.
- JSON backup export and restore through Android's system file picker.
- PCB SVG export through Android's system “Save As” dialog.
- Android notification channel and Firebase Cloud Messaging subscription to the `app_updates` topic.
- Native Firebase Authentication sign-in/link flows for Google, Yahoo and Facebook. The page never asks users to enter provider passwords.
- Stable Android application ID: `com.seventyfourls.logiclab`.

## Important: Firebase setup is required for live notifications and account sign-in

`app/google-services.json.example` is a safe placeholder template, not a live Firebase project. The real `app/google-services.json` is intentionally ignored by Git. The build scripts copy the example when the real config is missing. Replace the ignored local copy with your own Firebase config before expecting authentication or push notifications to work. In GitHub Actions, provide the contents through the `FIREBASE_GOOGLE_SERVICES_JSON` repository secret if required.

### 1. Open/build the project

1. Install the current Android Studio stable release and its Android SDK Platform 36. The project uses JDK 17 compatibility; the JDK bundled with Android Studio is suitable.
2. Extract the project ZIP.
3. In Android Studio, choose **Open** and select the `android/` folder (the folder containing `settings.gradle`).
4. Allow Gradle to sync and download dependencies.
5. To build a test APK, use **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The output should be `app/build/outputs/apk/debug/app-debug.apk`.

The environment that produced this project did not contain the Android SDK or Gradle and had no network access to download them, so a compiled APK could not be produced or device-tested here.

### 2. Connect Firebase (needed for accounts + push notifications)

1. Open the [Firebase Console](https://console.firebase.google.com/) and create a project.
2. Add an Android app with the exact package ID `com.seventyfourls.logiclab`.
3. Download the generated `google-services.json` and place it at `app/google-services.json` locally; do not commit it. For release workflows, store its contents in the `FIREBASE_GOOGLE_SERVICES_JSON` Actions secret. Keep the package ID unchanged for future updates.
4. Add the debug signing SHA-1 in Firebase project settings. In Android Studio, run the Gradle `signingReport` task to find it. Add the release SHA-1 as well before publishing signed release APKs.
5. In **Firebase → Authentication → Sign-in method**, enable Google, Yahoo, and Facebook only for providers you have configured.

Official setup references:
- [Add Firebase to your Android project](https://firebase.google.com/docs/android/setup)
- [Google sign-in for Android](https://developers.google.com/identity/sign-in/android/sign-in-identity)
- [Yahoo sign-in for Android](https://firebase.google.com/docs/auth/android/yahoo-oauth)
- [Facebook Login for Android](https://firebase.google.com/docs/auth/android/facebook-login)
- [Firebase Cloud Messaging for Android](https://firebase.google.com/docs/cloud-messaging/android/get-started)

### 3. Configure each sign-in provider safely

**Google**

Enable Google in Firebase Authentication, add the correct SHA-1 signing fingerprint, and make sure Firebase's generated `google-services.json` includes the Web OAuth client ID. Do not put a Google client secret in the app.

**Yahoo**

Enable Yahoo in Firebase Authentication. In Yahoo's developer console, register an OAuth application and select the OpenID Connect `profile` and `email` permissions. Enter Yahoo's client ID and client secret in the Firebase Console provider settings, and register the Firebase redirect domain shown by Firebase. Keep the client secret in Firebase, never in this source code.

**Facebook**

Create a Meta/Facebook developer app and enable Facebook Login. In `app/src/main/res/values/strings.xml`, replace `facebook_app_id`, `facebook_client_token` and `fb_login_protocol_scheme` with values from your own Meta app. In Firebase Authentication, enable Facebook and configure its App ID/secret. Add Firebase's OAuth redirect URL to the Facebook Login Valid OAuth Redirect URIs. Do not put the Facebook App Secret into the APK.

Sign-in buttons intentionally remain inactive until real provider configuration is added; they will show a configuration message rather than collecting passwords.

### 4. Enable and send update notifications

1. In Firebase Console, open **Messaging** and create a notification campaign.
2. Target the app topic named **`app_updates`**.
3. Enter an update title and message (for example, “74LS Logic Lab update available” and a short summary), then send or schedule it.
4. On Android 13+, users must allow notification permission on their phones. The app asks for permission when it first runs.

All installations subscribed to `app_updates` can receive the message. Push notifications do **not** automatically install an APK; users still need the APK/update distribution link. This project currently implements release-announcement notifications, not an unattended updater.

### 5. Protect progress when upgrading

- Android package ID is `com.seventyfourls.logiclab`. Keep it the same for all future builds.
- Production release tags such as `v1.1.1` set the Android `versionName` and `versionCode` automatically in GitHub Actions. For local builds, the Gradle defaults remain `1.1.0` and `1`; do not ship a different app under a reused `versionCode`.
- Install a newer signed APK **over** the existing app. Do not uninstall/clear app storage first. WebView storage uses a stable `https://appassets.androidplatform.net` origin, and the app auto-saves canvas/project/PCB snapshots to that storage.
- In Profile, tap **Export all progress (.json)** before important changes. To move from a standalone browser HTML file into the APK, open the updated HTML in the *same browser profile and file path* that held the old data, export a backup there, and use **Restore from backup** in the APK. Local-file browser storage differs between browsers, so keep the original HTML path/browser profile until you have verified the export.
- Restoring a backup overwrites matching saved keys and reloads the app to apply the backed-up progression. Keep backup files private because they contain circuit/project state.

## Known configuration boundary

Google/Yahoo/Facebook authentication identifies the user after Firebase is configured. This starter project does **not** automatically sync progress to a cloud database between multiple phones; progress protection currently means local persistence across app updates plus portable JSON backup/restore. Cross-device cloud sync would need a database, per-user security rules and an explicit merge/conflict policy.

### GitHub Actions release signing

The public repository deliberately contains no release keystore. To publish a production APK, set `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` as repository Actions secrets. The same keystore must be used for all upgrades to an existing installation.
