package io.github.fourmessenger;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
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
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "FourMessenger";
    private static final String APP_URL = "https://fourmessenger.github.io";
    private static final String NOTIFICATION_CHANNEL_ID = "fourmessenger_channel";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_CHOOSER_REQUEST_CODE = 200;
    private static final int CAMERA_REQUEST_CODE = 300;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private PermissionRequest pendingPermissionRequest;

    // Load the Rust native library
    static {
        try {
            System.loadLibrary("fourmessenger");
            Log.i(TAG, "Rust native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Rust native library not found, continuing without it: " + e.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen, no title bar
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Create notification channel
        createNotificationChannel();

        // Request all permissions upfront
        requestAllPermissions();

        // Set up WebView
        setupWebView();
    }

    private void setupWebView() {
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // TypeScript support (via JS engine)
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Media / Camera / Mic
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Modern web features
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // User agent
        String originalUA = settings.getUserAgentString();
        settings.setUserAgentString(originalUA + " FourMessenger/1.0");

        // Cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // Add JavaScript bridge for native features
        webView.addJavascriptInterface(new NativeBridge(this), "AndroidBridge");

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://fourmessenger.github.io") ||
                    url.startsWith("http://fourmessenger.github.io")) {
                    return false; // Load in WebView
                }
                // Open external URLs in browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot open URL: " + url);
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, "Page loaded: " + url);
                // Inject notification permission bridge
                view.evaluateJavascript(
                    "if (typeof Notification !== 'undefined') { " +
                    "  Notification.requestPermission = function() { " +
                    "    return Promise.resolve('granted'); " +
                    "  }; " +
                    "}", null);
            }
        });

        // WebChromeClient for camera, mic, file, notifications
        webView.setWebChromeClient(new WebChromeClient() {

            // Handle permission requests (camera, mic, notifications)
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                Log.i(TAG, "Permission request: " + java.util.Arrays.toString(request.getResources()));
                pendingPermissionRequest = request;

                List<String> androidPerms = new ArrayList<>();
                for (String resource : request.getResources()) {
                    if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        androidPerms.add(Manifest.permission.CAMERA);
                    } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        androidPerms.add(Manifest.permission.RECORD_AUDIO);
                    }
                }

                if (!androidPerms.isEmpty()) {
                    boolean allGranted = true;
                    for (String perm : androidPerms) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, perm)
                                != PackageManager.PERMISSION_GRANTED) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        request.grant(request.getResources());
                        pendingPermissionRequest = null;
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this,
                            androidPerms.toArray(new String[0]),
                            PERMISSION_REQUEST_CODE);
                    }
                } else {
                    // Grant other permissions (notifications, etc.)
                    request.grant(request.getResources());
                    pendingPermissionRequest = null;
                }
            }

            // File chooser for file uploads
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;

                Intent chooserIntent = fileChooserParams.createIntent();
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

                // Create temp file for camera
                try {
                    File photoFile = createImageFile();
                    cameraImageUri = FileProvider.getUriForFile(MainActivity.this,
                        getPackageName() + ".fileprovider", photoFile);
                    cameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri);
                } catch (Exception e) {
                    Log.e(TAG, "Error creating camera file: " + e.getMessage());
                    cameraIntent = null;
                }

                Intent[] extraIntents = cameraIntent != null ? new Intent[]{cameraIntent} : new Intent[0];

                Intent finalIntent = Intent.createChooser(chooserIntent, "Choose File");
                finalIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

                startActivityForResult(finalIntent, FILE_CHOOSER_REQUEST_CODE);
                return true;
            }

            // Geolocation
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            // Console messages from JS
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                Log.d(TAG, "JS Console [" + consoleMessage.messageLevel() + "]: " +
                    consoleMessage.message() + " (line " + consoleMessage.lineNumber() + ")");
                return true;
            }

            // Progress
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    Log.i(TAG, "Page fully loaded");
                }
            }
        });

        // Enable WebView debugging in debug builds
        WebView.setWebContentsDebuggingEnabled(true);

        // Load the app
        webView.loadUrl(APP_URL);
    }

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "4 Messenger Notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications from 4 Messenger");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestAllPermissions() {
        List<String> permissions = new ArrayList<>();

        String[] requiredPerms = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
        };

        for (String perm : requiredPerms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(perm);
            }
        }

        // Storage permissions based on API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] mediaPerms = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            };
            for (String perm : mediaPerms) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(perm);
                }
            }
        } else {
            String[] storagePerms = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            };
            for (String perm : storagePerms) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(perm);
                }
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                permissions.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (pendingPermissionRequest != null) {
                pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                pendingPermissionRequest = null;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;

            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                } else if (cameraImageUri != null) {
                    results = new Uri[]{cameraImageUri};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            cameraImageUri = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }

    // JavaScript bridge for native Android features
    public class NativeBridge {
        private final Context context;

        NativeBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void showNotification(String title, String body) {
            try {
                android.app.NotificationManager manager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

                if (manager != null) {
                    manager.notify((int) System.currentTimeMillis(), builder.build());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing notification: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        }

        @JavascriptInterface
        public boolean isAndroid() {
            return true;
        }

        @JavascriptInterface
        public void downloadFile(String url, String filename) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(filename);
                request.setDescription("Downloading via 4 Messenger");
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, filename);

                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(context, "Downloading " + filename, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error downloading file: " + e.getMessage());
            }
        }
    }
}
