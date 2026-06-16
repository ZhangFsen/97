package com.colox.adminmobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.core.content.ContextCompat;

final class NotificationIconHelper {
    private NotificationIconHelper() {}

    static String normalizeTheme(String theme) {
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

    static String readTheme(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences("coloxadminmobile_native", Context.MODE_PRIVATE);
            return normalizeTheme(sp.getString("notification_icon_theme", sp.getString("launcher_icon_theme_pending", sp.getString("launcher_icon_theme", "orange"))));
        } catch (Exception e) {
            return "orange";
        }
    }

    static int iconResForTheme(String theme) {
        switch (normalizeTheme(theme)) {
            case "green": return R.drawable.ic_launcher_green;
            case "blue": return R.drawable.ic_launcher_blue;
            case "purple": return R.drawable.ic_launcher_purple;
            case "sakura": return R.drawable.ic_launcher_sakura;
            case "latte": return R.drawable.ic_launcher_latte;
            case "dark": return R.drawable.ic_launcher_dark;
            case "minimal": return R.drawable.ic_launcher_minimal;
            case "orange":
            default: return R.drawable.ic_launcher_orange;
        }
    }

    static int currentIconRes(Context context) {
        return iconResForTheme(readTheme(context));
    }

    static int colorForTheme(String theme) {
        switch (normalizeTheme(theme)) {
            case "green": return Color.rgb(18, 185, 129);
            case "blue": return Color.rgb(47, 128, 237);
            case "purple": return Color.rgb(124, 58, 237);
            case "sakura": return Color.rgb(255, 111, 174);
            case "latte": return Color.rgb(168, 111, 61);
            case "dark": return Color.rgb(31, 41, 55);
            case "minimal": return Color.rgb(107, 114, 128);
            case "orange":
            default: return Color.rgb(255, 106, 0);
        }
    }

    static int currentColor(Context context) {
        return colorForTheme(readTheme(context));
    }

    static Bitmap currentLargeIcon(Context context) {
        return largeIcon(context, currentIconRes(context));
    }

    private static Bitmap largeIcon(Context context, int resId) {
        try {
            Drawable drawable = ContextCompat.getDrawable(context, resId);
            if (drawable == null) return null;
            int size = (int) (48 * context.getResources().getDisplayMetrics().density + 0.5f);
            if (size <= 0) size = 96;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
