# First push to GitHub

This project package is ready to upload, but it has not been published to any GitHub account yet.

## 1. Create the empty public repository

On GitHub, create a **public** repository named `74LS-Logic-Lab`. Do not initialize it with a README, `.gitignore`, or license because these files are already in this package.

## 2. Push this folder

Open a terminal in the extracted `74LSLogicLab_GitHub_Starter` folder and run:

```bash
git init -b main
git add .
git status --short
git commit -m "Initial 74LS Logic Lab web and Android release workflows"
git remote add origin https://github.com/YOUR-USERNAME/74LS-Logic-Lab.git
git push -u origin main
```

Review `git status --short` before committing. The real Firebase config, keystores, APKs, and build outputs are ignored. Do not force-add them.

## 3. Check the first workflows

Open the repository's **Actions** tab. The `CI` workflow should validate the HTML / JavaScript and build a debug APK. Download the `74ls-logic-lab-debug-apk` artifact from a successful run.

For GitHub Pages, open **Settings → Pages** and set the source to **GitHub Actions**. The deployment job publishes the root `index.html` on pushes to `main`.

## 4. Before the first production APK release

1. Create a long-lived Android release keystore and store a protected backup offline. Never upload it to the repository.
2. Add these repository **Actions secrets**: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
3. For live Firebase services, add `FIREBASE_GOOGLE_SERVICES_JSON`. For optional automatic release announcement, add `FIREBASE_SERVICE_ACCOUNT_JSON` and set the repository Actions variable `SEND_FCM_ON_RELEASE` to `true`.
4. Verify CI is green on the commit you intend to release.
5. Tag and push the next version, for example `v1.1.1`. This triggers the signed APK and HTML release job.

If this is the first signed production APK, any previously installed debug APK may be signed with a different key. Android cannot update a differently signed package in place; export the progress JSON and verify that backup before switching from a debug install to the official signing key.
