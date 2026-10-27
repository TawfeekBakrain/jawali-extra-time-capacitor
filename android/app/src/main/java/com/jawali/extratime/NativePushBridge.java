package com.jawali.extratime;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

public final class NativePushBridge {
    private static final String TAG = "ExtraTimePush";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 7102;

    private final FragmentActivity activity;
    private final WebView webView;

    public NativePushBridge(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void register() {
        activity.runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
                );
            }

            FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Unable to obtain FCM token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    if (token == null || token.trim().isEmpty()) {
                        Log.w(TAG, "FCM returned an empty token");
                        return;
                    }

                    String quotedToken = JSONObject.quote(token);
                    webView.post(() -> webView.evaluateJavascript(
                        "if(typeof window.onJawaliFcmToken==='function')" +
                        "{window.onJawaliFcmToken(" + quotedToken + ");}",
                        null
                    ));
                });
        });
    }
}
