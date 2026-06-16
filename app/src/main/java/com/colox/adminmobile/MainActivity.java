package com.colox.adminmobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.VibrationEffect;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    public static final String CHANNEL_ID = "coloxadminmobile_reminder";
    private static final int REQ_NOTIFICATION = 1001;
    private static final int REQ_FILE_CHOOSER = 1002;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private boolean pageReady = false;
    private boolean openAddAfterLoad = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }

        webView.setBackgroundColor(Color.rgb(245, 247, 251));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollBarStyle(WebView.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient() {
            
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageReady = true;
                maybeOpenAddFromIntent();
            }
        });
        webView.setWebChromeClient(new AppWebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        setContentView(webView);
        handleIntent(getIntent());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("open_add", false)) {
            openAddAfterLoad = true;
        }
    }

    private void maybeOpenAddFromIntent() {
        if (webView == null || !pageReady || !openAddAfterLoad) return;
        openAddAfterLoad = false;
        webView.postDelayed(() -> webView.evaluateJavascript("window.openAddFromFloating ? window.openAddFromFloating() : (window.openAdd && openAdd(false));", null), 220);
    }

    private static void startFloatingService(Context context) {
        try {
            Intent intent = new Intent(context, FloatingWindowService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Exception ignored) {}
    }

    private void syncFloatingWindowAfterPermission() {
        SharedPreferences sp = getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
        boolean canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        if (sp.getBoolean("float_pending_permission", false) && canDraw) {
            sp.edit().putBoolean("float_pending_permission", false).putBoolean("float_enabled", true).apply();
            startFloatingService(this);
        } else if (sp.getBoolean("float_enabled", false) && canDraw) {
            startFloatingService(this);
        }
        if (webView != null && pageReady) {
            webView.evaluateJavascript("window.refreshFloatingStatus && window.refreshFloatingStatus();", null);
        }
    }

    
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        maybeOpenAddFromIntent();
    }

    
    protected void onResume() {
        super.onResume();
        syncFloatingWindowAfterPermission();
    }

    @Override
    protected void onStop() {
        super.onStop();
        applyPendingLauncherIconThemeIfNeeded();
    }

    private void applyPendingLauncherIconThemeIfNeeded() {
        try {
            SharedPreferences sp = getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            String pending = sp.getString("launcher_icon_theme_pending", "");
            if (pending == null || pending.length() == 0) return;
            applyLauncherIconTheme(this, pending);
        } catch (Exception ignored) {}
    }

    private static String normalizeLauncherTheme(String theme) {
        String normalized = theme == null ? "orange" : theme.trim().toLowerCase();
        switch (normalized) {
            case "green":
            case "blue":
            case "purple":
            case "sakura":
            case "latte":
            case "dark":
            case "minimal":
            case "orange":
                return normalized;
            default:
                return "orange";
        }
    }

    private static String launcherAliasForTheme(String normalized) {
        switch (normalized) {
            case "green": return "LauncherGreen";
            case "blue": return "LauncherBlue";
            case "purple": return "LauncherPurple";
            case "sakura": return "LauncherSakura";
            case "latte": return "LauncherLatte";
            case "dark": return "LauncherDark";
            case "minimal": return "LauncherMinimal";
            case "orange":
            default:
                return "LauncherOrange";
        }
    }

    private static String applyLauncherIconTheme(Context context, String theme) {
        String normalized = normalizeLauncherTheme(theme);
        String alias = launcherAliasForTheme(normalized);
        try {
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            String last = sp.getString("launcher_icon_theme", "orange");
            if (normalized.equals(last)) {
                sp.edit().remove("launcher_icon_theme_pending").apply();
                return "unchanged";
            }
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            String[] aliases = new String[]{"LauncherOrange", "LauncherGreen", "LauncherBlue", "LauncherPurple", "LauncherSakura", "LauncherLatte", "LauncherDark", "LauncherMinimal"};

            // 先启用目标入口，再禁用其他入口，避免桌面短暂找不到 launcher 入口。
            ComponentName target = new ComponentName(pkg, pkg + "." + alias);
            pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String item : aliases) {
                if (item.equals(alias)) continue;
                ComponentName cn = new ComponentName(pkg, pkg + "." + item);
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            sp.edit()
                    .putString("launcher_icon_theme", normalized)
                    .putString("notification_icon_theme", normalized)
                    .remove("launcher_icon_theme_pending")
                    .apply();
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "后台提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("云端后台提醒");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    private class AppWebChromeClient extends WebChromeClient {

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE_CHOOSER);
            } catch (Exception e) {
                MainActivity.this.filePathCallback = null;
                return false;
            }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("提示")
                    .setMessage(message)
                    .setPositiveButton("确定", (d, w) -> result.confirm())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("确认")
                    .setMessage(message)
                    .setPositiveButton("确定", (d, w) -> result.confirm())
                    .setNegativeButton("取消", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            EditText input = new EditText(MainActivity.this);
            input.setSingleLine(false);
            input.setText(defaultValue == null ? "" : defaultValue);
            input.setSelection(input.getText().length());
            int pad = (int) (20 * getResources().getDisplayMetrics().density);
            input.setPadding(pad, pad / 2, pad, pad / 2);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(message == null || message.length() == 0 ? "请输入" : message)
                    .setView(input)
                    .setPositiveButton("确定", (d, w) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("取消", (d, w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
            return true;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) results = new Uri[]{uri};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    public static class AndroidBridge {
        private final Context context;
        private final MainActivity activity;

        AndroidBridge(MainActivity activity) {
            this.activity = activity;
            this.context = activity.getApplicationContext();
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                if (ms < 35) ms = 35;
                if (ms > 160) ms = 160;
                Vibrator vibrator;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    vibrator = vm == null ? null : vm.getDefaultVibrator();
                } else {
                    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                }
                if (vibrator == null || !vibrator.hasVibrator()) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, 255));
                } else {
                    vibrator.vibrate(ms);
                }
            } catch (Exception ignored) {}
        }

        /* internal helper */
        private boolean canDrawOverlay() {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
        }

        private void startFloatingService() {
            MainActivity.startFloatingService(context);
        }

        private void stopFloatingService() {
            try { context.stopService(new Intent(context, FloatingWindowService.class)); } catch (Exception ignored) {}
        }

        private void refreshFloatingNotificationIfRunning() {
            try {
                SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
                if (!sp.getBoolean("float_enabled", false) || !canDrawOverlay()) return;
                Intent intent = new Intent(context, FloatingWindowService.class);
                intent.setAction(FloatingWindowService.ACTION_REFRESH_NOTIFICATION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
                else context.startService(intent);
            } catch (Exception ignored) {}
        }


        @JavascriptInterface
        public String queueLauncherIconTheme(String theme) {
            String normalized = MainActivity.normalizeLauncherTheme(theme);
            try {
                SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
                String last = sp.getString("launcher_icon_theme", "orange");
                if (normalized.equals(last)) {
                    sp.edit().putString("notification_icon_theme", normalized).remove("launcher_icon_theme_pending").apply();
                    refreshFloatingNotificationIfRunning();
                    return "unchanged";
                }
                sp.edit().putString("notification_icon_theme", normalized).putString("launcher_icon_theme_pending", normalized).apply();
                refreshFloatingNotificationIfRunning();
                return "queued";
            } catch (Exception e) {
                return "error";
            }
        }

        @JavascriptInterface
        public String setLauncherIconTheme(String theme) {
            // 保留立即切换接口以兼容旧页面，但当前页面默认使用 queueLauncherIconTheme，
            // 等 App 进入后台后再刷新桌面图标，避免部分系统把用户强制带回桌面。
            return MainActivity.applyLauncherIconTheme(context, theme);
        }

        @JavascriptInterface
        public void setNotificationIconTheme(String theme) {
            String normalized = NotificationIconHelper.normalizeTheme(theme);
            try {
                SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
                sp.edit().putString("notification_icon_theme", normalized).apply();
                refreshFloatingNotificationIfRunning();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void setFloatingColor(String color) {
            if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) return;
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            sp.edit().putString("float_color", color).apply();
            if (sp.getBoolean("float_enabled", false) && canDrawOverlay()) {
                stopFloatingService();
                startFloatingService();
            }
        }

        @JavascriptInterface
        public String getFloatingWindowState() {
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            boolean enabled = sp.getBoolean("float_enabled", false);
            boolean can = canDrawOverlay();
            if (enabled && !can) {
                sp.edit().putBoolean("float_enabled", false).apply();
                enabled = false;
            }
            return enabled ? "on" : (can ? "off" : "need_permission");
        }

        @JavascriptInterface
        public String toggleFloatingWindow() {
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            boolean enabled = sp.getBoolean("float_enabled", false);
            if (enabled) {
                sp.edit().putBoolean("float_enabled", false).putBoolean("float_pending_permission", false).apply();
                stopFloatingService();
                return "disabled";
            }
            if (!canDrawOverlay()) {
                sp.edit().putBoolean("float_pending_permission", true).apply();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
                return "need_permission";
            }
            sp.edit().putBoolean("float_enabled", true).putBoolean("float_pending_permission", false).apply();
            startFloatingService();
            return "enabled";
        }

        @JavascriptInterface
        public void openFloatingPermission() {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {}
        }

        
        @JavascriptInterface
        public void scheduleDailyReminder(String time) {
            if (time == null || !time.matches("^\\d{1,2}:\\d{2}$")) return;
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return;

            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            sp.edit().putBoolean("reminder_enabled", true).putString("reminder_time", String.format("%02d:%02d", hour, minute)).apply();
            schedule(context, hour, minute);
        }

        @JavascriptInterface
        public void cancelDailyReminder() {
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            sp.edit().putBoolean("reminder_enabled", false).apply();
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(reminderIntent(context));
        }

        @JavascriptInterface
        public String exportBackupFile(String filename, String content) {
            if (filename == null || filename.trim().length() == 0) filename = "云端用户管理器 Pro备份.json";
            filename = filename.replaceAll("[\\/:*?\"<>|]", "_");
            if (!filename.endsWith(".json")) filename = filename + ".json";
            if (content == null) content = "";
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "";
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os == null) return "";
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    return "/Download/" + filename;
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(content.getBytes("UTF-8"));
                    fos.close();
                    return file.getAbsolutePath();
                }
            } catch (Exception e) {
                try {
                    File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return "";
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(content.getBytes("UTF-8"));
                    fos.close();
                    return file.getAbsolutePath();
                } catch (Exception ignored) {
                    return "";
                }
            }
        }

        @JavascriptInterface
        public String exportTextFile(String filename, String content) {
            if (filename == null || filename.trim().length() == 0) filename = "云端用户管理器 Pro分析.txt";
            filename = filename.replaceAll("[\\/:*?\"<>|]", "_");
            if (!filename.endsWith(".txt")) filename = filename + ".txt";
            if (content == null) content = "";
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "";
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os == null) return "";
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    return "/Download/" + filename;
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(content.getBytes("UTF-8"));
                    fos.close();
                    return file.getAbsolutePath();
                }
            } catch (Exception e) {
                try {
                    File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return "";
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(content.getBytes("UTF-8"));
                    fos.close();
                    return file.getAbsolutePath();
                } catch (Exception ignored) {
                    return "";
                }
            }
        }


        @JavascriptInterface
        public String exportPngFile(String filename, String base64Png) {
            if (filename == null || filename.trim().length() == 0) filename = "云端用户管理器 Pro分析.png";
            filename = filename.replaceAll("[\\/:*?\"<>|]", "_");
            if (!filename.endsWith(".png")) filename = filename + ".png";
            if (base64Png == null) base64Png = "";
            try {
                byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(MediaStore.Downloads.MIME_TYPE, "image/png");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return "";
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os == null) return "";
                    os.write(bytes);
                    os.close();
                    return "/Download/" + filename;
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.close();
                    return file.getAbsolutePath();
                }
            } catch (Exception e) {
                try {
                    byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
                    File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return "";
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.close();
                    return file.getAbsolutePath();
                } catch (Exception ignored) {
                    return "";
                }
            }
        }


        @JavascriptInterface
        public void sharePngFile(String filename, String base64Png) {
            if (filename == null || filename.trim().length() == 0) filename = "云端用户管理器 Pro分析.png";
            filename = filename.replaceAll("[\\/:*?\"<>|]", "_");
            if (!filename.endsWith(".png")) filename = filename + ".png";
            final String safeName = filename;
            final String data = base64Png == null ? "" : base64Png;
            try {
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, safeName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/云端用户管理器 Pro");
                }
                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return;
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                if (os == null) return;
                os.write(bytes);
                os.close();
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("image/png");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(intent, "分享统计图片");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } catch (Exception e) {
                // 分享失败时不打断用户，前端仍可走保存兜底
            }
        }


        private static String jsonEscape(String text) {
            if (text == null) return "";
            return text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private static String errorJson(String message) {
            return "{\"ok\":false,\"message\":\"" + jsonEscape(message) + "\",\"error\":\"" + jsonEscape(message) + "\"}";
        }

        private static String doPostJson(String urlText, String jsonBody) {
            HttpURLConnection conn = null;
            try {
                if (urlText == null) urlText = "";
                urlText = urlText.trim();
                if (!urlText.startsWith("https://") && !urlText.startsWith("http://")) {
                    return errorJson("接口地址必须以 http:// 或 https:// 开头：" + urlText);
                }
                if (jsonBody == null || jsonBody.trim().length() == 0) jsonBody = "{}";

                URL url = new URL(urlText);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.setRequestProperty("User-Agent", "Colox-Admin-Mobile/1.0.5 AndroidBridge");

                byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(out.length);
                OutputStream os = conn.getOutputStream();
                os.write(out);
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                InputStream is = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                if (is != null) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                    is.close();
                }
                String text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
                if (text.trim().length() == 0) return errorJson("云端返回为空，HTTP " + code);
                if (code < 200 || code >= 400) return errorJson("HTTP " + code + "：" + text);
                return text;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null || msg.trim().length() == 0) msg = e.toString();
                return errorJson("连接云端失败：" + msg);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @JavascriptInterface
        public String ping() {
            return "ok";
        }

        @JavascriptInterface
        public String postJsonSync(String urlText, String jsonBody) {
            return doPostJson(urlText, jsonBody);
        }

        @JavascriptInterface
        public void postJson(String callbackId, String urlText, String jsonBody) {
            final String cb = callbackId == null ? "" : callbackId;
            new Thread(() -> {
                final String response = doPostJson(urlText, jsonBody);
                try {
                    if (activity != null && activity.webView != null) {
                        activity.runOnUiThread(() -> {
                            try {
                                String script = "window.__nativePostJsonCallback && window.__nativePostJsonCallback(\""
                                        + jsonEscape(cb) + "\",\"" + jsonEscape(response) + "\");";
                                activity.webView.evaluateJavascript(script, null);
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        @JavascriptInterface
        public void saveFeedback(String text) {
            if (text == null || text.trim().length() == 0) return;
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            String old = sp.getString("feedback", "");
            String line = System.currentTimeMillis() + "\t" + text.trim() + "\n";
            sp.edit().putString("feedback", old + line).apply();
        }

        @JavascriptInterface
        public String getSavedReminderTime() {
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            return sp.getBoolean("reminder_enabled", false) ? sp.getString("reminder_time", "21:00") : "";
        }

        static void schedule(Context context, int hour, int minute) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
            PendingIntent pi = reminderIntent(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
            }
        }

        static PendingIntent reminderIntent(Context context) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            return PendingIntent.getBroadcast(context, 20260429, intent, flags);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("window.appBack ? window.appBack() : 'false';", value -> {
                if ("false".equals(value) || "null".equals(value)) {
                    MainActivity.super.onBackPressed();
                }
            });
        } else {
            super.onBackPressed();
        }
    }
}
