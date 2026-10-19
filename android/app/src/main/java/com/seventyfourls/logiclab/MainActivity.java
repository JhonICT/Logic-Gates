package com.seventyfourls.logiclab;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Android WebView shell for the offline-first 74LS Logic Lab. */
public class MainActivity extends Activity {
    private static final String TAG = "74LSLogicLab";
    private static final String UPDATE_TOPIC = "app_updates";
    private static final String CHANNEL_ID = "app_updates";
    private static final int RC_FILE_PICKER = 7101;
    private static final int RC_SAVE_FILE = 7102;
    private static final int RC_POST_NOTIFICATIONS = 7103;
    private static final int RC_GOOGLE_SIGN_IN = 7201;

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingSaveText;
    private String pendingSaveMime;
    private String pendingSaveName;
    private CallbackManager facebookCallbackManager;
    private FirebaseAuth firebaseAuth;
    private String pendingProvider;
    private boolean firebaseReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        createNotificationChannel();
        initFirebaseSafely();
        initFacebook();
        configureWebView();
        requestNotificationPermissionIfNeeded();
        subscribeForReleaseAnnouncements();
    }

    private void initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
            }
            firebaseAuth = FirebaseAuth.getInstance();
            firebaseReady = isRealFirebaseConfigPresent();
            if (firebaseReady) {
                com.google.android.gms.tasks.Task<AuthResult> pending = firebaseAuth.getPendingAuthResult();
                if (pending != null) {
                    pending.addOnSuccessListener(result -> sendAuthResult("yahoo", result.getUser(), true, "Yahoo account connected."))
                            .addOnFailureListener(e -> sendAuthResult("yahoo", null, false, cleanError(e)));
                }
            } else {
                Log.i(TAG, "Firebase placeholder config detected; sign-in and push need project setup.");
            }
        } catch (Exception e) {
            firebaseReady = false;
            Log.w(TAG, "Firebase is not configured yet", e);
        }
    }

    private boolean isRealFirebaseConfigPresent() {
        try {
            int id = getResources().getIdentifier("google_api_key", "string", getPackageName());
            if (id == 0) return false;
            String key = getString(id);
            return key != null && !key.trim().isEmpty() && !key.startsWith("REPLACE_");
        } catch (Exception e) {
            return false;
        }
    }

    private void initFacebook() {
        facebookCallbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override public void onSuccess(LoginResult result) {
                AuthCredential credential = FacebookAuthProvider.getCredential(result.getAccessToken().getToken());
                completeCredentialSignIn("facebook", credential);
            }
            @Override public void onCancel() { sendAuthResult("facebook", null, false, "Facebook sign-in was cancelled."); }
            @Override public void onError(FacebookException error) { sendAuthResult("facebook", null, false, cleanError(error)); }
        });
    }

    private void configureWebView() {
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFEEF2F8);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " 74LSLogicLabAndroid/1.1.0");

        webView.addJavascriptInterface(new AppBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return dispatchExternalUri(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                syncSignedInUserToProfile();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, RC_FILE_PICKER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available on this phone.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView ignored, WebResourceRequest request) {
                        dispatchExternalUri(request.getUrl());
                        popup.postDelayed(popup::destroy, 300);
                        return true;
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView ignored, String url) {
                        try { dispatchExternalUri(Uri.parse(url)); } catch (Exception ignoredError) { }
                        popup.postDelayed(popup::destroy, 300);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
        setContentView(webView);
        // This fixed HTTPS asset origin keeps WebView localStorage stable when the same APK is updated.
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    private boolean dispatchExternalUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if ("https".equals(scheme) && "appassets.androidplatform.net".equals(host)) return false;
        if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        // Never navigate the app WebView to arbitrary file/content/javascript URLs.
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_POST_NOTIFICATIONS);
        }
    }

    private void subscribeForReleaseAnnouncements() {
        if (!firebaseReady) return;
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(UPDATE_TOPIC)
                    .addOnSuccessListener(unused -> Log.i(TAG, "Subscribed to app_updates"))
                    .addOnFailureListener(e -> Log.w(TAG, "Could not subscribe to update notifications", e));
        } catch (Exception e) {
            Log.w(TAG, "FCM initialization was unavailable", e);
        }
    }

    private void syncSignedInUserToProfile() {
        if (!firebaseReady || firebaseAuth == null || webView == null) return;
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            String p = info.getProviderId();
            String key = null;
            if ("google.com".equals(p)) key = "google";
            else if ("yahoo.com".equals(p)) key = "yahoo";
            else if ("facebook.com".equals(p)) key = "facebook";
            if (key != null) {
                sendAuthResult(key, user, true, "Account connected.");
            }
        }
    }

    private void startAccountConnection(String provider) {
        if (!Arrays.asList("google", "yahoo", "facebook").contains(provider)) return;
        if (!firebaseReady || firebaseAuth == null) {
            sendAuthResult(provider, null, false,
                    "Account sign-in is not active yet. Configure app/google-services.json with your own Firebase project, then enable this provider in Firebase Authentication.");
            return;
        }
        if (firebaseAuth.getCurrentUser() != null && hasProvider(firebaseAuth.getCurrentUser(), providerId(provider))) {
            sendAuthResult(provider, firebaseAuth.getCurrentUser(), true, "This provider is already connected.");
            return;
        }
        if ("google".equals(provider)) startGoogleSignIn();
        else if ("facebook".equals(provider)) startFacebookSignIn();
        else startYahooSignIn();
    }

    private String providerId(String provider) {
        if ("google".equals(provider)) return "google.com";
        if ("facebook".equals(provider)) return "facebook.com";
        if ("yahoo".equals(provider)) return "yahoo.com";
        return provider;
    }

    private boolean hasProvider(FirebaseUser user, String providerId) {
        if (user == null) return false;
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (providerId.equals(info.getProviderId())) return true;
        }
        return false;
    }

    private void startGoogleSignIn() {
        try {
            int resId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            String clientId = resId == 0 ? "" : getString(resId);
            if (clientId.trim().isEmpty() || clientId.startsWith("REPLACE_")) {
                sendAuthResult("google", null, false, "Google sign-in needs the Web OAuth client ID from your Firebase-generated google-services.json.");
                return;
            }
            GetSignInIntentRequest request = GetSignInIntentRequest.builder().setServerClientId(clientId).build();
            Identity.getSignInClient(this).getSignInIntent(request)
                    .addOnSuccessListener(result -> {
                        try {
                            startIntentSenderForResult(result.getIntentSender(), RC_GOOGLE_SIGN_IN, null, 0, 0, 0);
                        } catch (Exception e) {
                            sendAuthResult("google", null, false, cleanError(e));
                        }
                    })
                    .addOnFailureListener(e -> sendAuthResult("google", null, false, cleanError(e)));
        } catch (Exception e) {
            sendAuthResult("google", null, false, cleanError(e));
        }
    }

    private void startFacebookSignIn() {
        String appId = getString(R.string.facebook_app_id);
        String token = getString(R.string.facebook_client_token);
        if (appId.startsWith("000000") || token.startsWith("REPLACE_")) {
            sendAuthResult("facebook", null, false, "Facebook sign-in needs your Meta App ID and Client Token in app/src/main/res/values/strings.xml, plus Facebook enabled in Firebase.");
            return;
        }
        try {
            LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
        } catch (Exception e) {
            sendAuthResult("facebook", null, false, cleanError(e));
        }
    }

    private void startYahooSignIn() {
        pendingProvider = "yahoo";
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("yahoo.com");
        // Yahoo uses Firebase's secure OAuth flow; no password is collected in the WebView.
        try {
            FirebaseUser current = firebaseAuth.getCurrentUser();
            com.google.android.gms.tasks.Task<AuthResult> task;
            if (current == null) task = firebaseAuth.startActivityForSignInWithProvider(this, provider.build());
            else task = current.startActivityForLinkWithProvider(this, provider.build());
            task.addOnSuccessListener(result -> sendAuthResult("yahoo", result.getUser(), true, "Yahoo account connected."))
                    .addOnFailureListener(e -> sendAuthResult("yahoo", null, false, cleanError(e)));
        } catch (Exception e) {
            sendAuthResult("yahoo", null, false, cleanError(e));
        }
    }

    private void completeCredentialSignIn(String provider, AuthCredential credential) {
        if (firebaseAuth == null || credential == null) {
            sendAuthResult(provider, null, false, "Authentication is not configured.");
            return;
        }
        try {
            FirebaseUser current = firebaseAuth.getCurrentUser();
            com.google.android.gms.tasks.Task<AuthResult> task = current == null
                    ? firebaseAuth.signInWithCredential(credential)
                    : current.linkWithCredential(credential);
            task.addOnSuccessListener(result -> sendAuthResult(provider, result.getUser(), true, "Account connected."))
                    .addOnFailureListener(e -> sendAuthResult(provider, null, false, cleanError(e)));
        } catch (Exception e) {
            sendAuthResult(provider, null, false, cleanError(e));
        }
    }

    private void sendAuthResult(String provider, FirebaseUser user, boolean success, String message) {
        String email = user == null || user.getEmail() == null ? "" : user.getEmail();
        String displayName = user == null || user.getDisplayName() == null ? "" : user.getDisplayName();
        String js = "if(window.profileNativeAuthResult){window.profileNativeAuthResult(" + JSONObject.quote(provider) + "," +
                JSONObject.quote(email) + "," + JSONObject.quote(displayName) + "," + success + "," + JSONObject.quote(message == null ? "" : message) + ");}";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
            if (!success && message != null && !message.isEmpty()) Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private String cleanError(Exception e) {
        if (e == null) return "Sign-in failed. Check your provider configuration and try again.";
        String msg = e.getLocalizedMessage();
        if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
        return msg.length() > 240 ? msg.substring(0, 237) + "…" : msg;
    }

    private void saveTextFile(String filename, String mimeType, String content) {
        runOnUiThread(() -> {
            pendingSaveName = filename == null || filename.trim().isEmpty() ? "74LS-Logic-Lab-export.txt" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
            pendingSaveMime = mimeType == null || mimeType.trim().isEmpty() ? "text/plain" : mimeType;
            pendingSaveText = content == null ? "" : content;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(pendingSaveMime);
            intent.putExtra(Intent.EXTRA_TITLE, pendingSaveName);
            try {
                startActivityForResult(intent, RC_SAVE_FILE);
            } catch (ActivityNotFoundException e) {
                writeToDownloadsFallback();
            }
        });
    }

    private void writeToDownloadsFallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                throw new IOException("Android file picker is unavailable; no broad storage permission was requested.");
            }
            throw new IOException("Please install a file manager or use the Save As dialog.");
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Save As: " + cleanError(e), Toast.LENGTH_LONG).show();
            notifySaveResult(false, e.getMessage());
        }
    }

    private void notifySaveResult(boolean success, String message) {
        String js = "if(window.profileNativeSaveResult){window.profileNativeSaveResult(" + success + "," + JSONObject.quote(message == null ? "" : message) + ");}";
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
    }

    private void signOut() {
        if (firebaseAuth != null) firebaseAuth.signOut();
        try { LoginManager.getInstance().logOut(); } catch (Exception ignored) { }
        sendAuthResult("google", null, false, "Signed out of the connected Logic Lab account.");
        runOnUiThread(() -> Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show());
    }

    private class AppBridge {
        @JavascriptInterface public void connectAccount(String provider) {
            runOnUiThread(() -> startAccountConnection(provider == null ? "" : provider.trim().toLowerCase()));
        }
        @JavascriptInterface public void requestNotificationPermission() {
            runOnUiThread(MainActivity.this::requestNotificationPermissionIfNeeded);
        }
        @JavascriptInterface public void saveTextFile(String filename, String mimeType, String content) {
            MainActivity.this.saveTextFile(filename, mimeType, content);
        }
        @JavascriptInterface public String getAppVersion() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception e) { return "1.1.0"; }
        }
        @JavascriptInterface public void signOut() { runOnUiThread(MainActivity.this::signOut); }
        @JavascriptInterface public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message == null ? "" : message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (facebookCallbackManager != null) facebookCallbackManager.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_FILE_PICKER) {
            if (fileChooserCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileChooserCallback.onReceiveValue(result);
                fileChooserCallback = null;
            }
            return;
        }
        if (requestCode == RC_SAVE_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new IOException("The selected file could not be opened.");
                    stream.write((pendingSaveText == null ? "" : pendingSaveText).getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                    notifySaveResult(true, "Saved " + pendingSaveName + ".");
                    Toast.makeText(this, "Saved " + pendingSaveName, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    notifySaveResult(false, cleanError(e));
                    Toast.makeText(this, "Save failed: " + cleanError(e), Toast.LENGTH_LONG).show();
                }
            } else notifySaveResult(false, "Save cancelled.");
            pendingSaveText = null;
            pendingSaveMime = null;
            pendingSaveName = null;
            return;
        }
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            if (resultCode != RESULT_OK || data == null) {
                sendAuthResult("google", null, false, "Google sign-in was cancelled.");
                return;
            }
            try {
                SignInCredential signInCredential = Identity.getSignInClient(this).getSignInCredentialFromIntent(data);
                String idToken = signInCredential.getGoogleIdToken();
                if (idToken == null || idToken.isEmpty()) {
                    sendAuthResult("google", null, false, "Google did not return an ID token. Check your Firebase Web OAuth client ID and signing SHA-1.");
                    return;
                }
                AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
                completeCredentialSignIn("google", credential);
            } catch (ApiException e) {
                sendAuthResult("google", null, false, cleanError(e));
            } catch (Exception e) {
                sendAuthResult("google", null, false, cleanError(e));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.evaluateJavascript("try{window.dispatchEvent(new Event('pagehide'))}catch(e){}", null);
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
