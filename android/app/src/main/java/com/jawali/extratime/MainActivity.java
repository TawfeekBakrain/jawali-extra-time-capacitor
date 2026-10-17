package com.jawali.extratime;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private NetworkNoticeManager networkNoticeManager;

    private static final String TAG = "ExtraTimeWebAuthn";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        networkNoticeManager = new NetworkNoticeManager(this);
        networkNoticeManager.start();

        Bridge bridge = getBridge();

        if (bridge == null) {
            Log.e(TAG, "Capacitor bridge is not ready.");
            return;
        }

        WebView webView = bridge.getWebView();

        if (webView == null) {
            Log.e(TAG, "WebView is not available.");
            return;
        }

        if (!WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_AUTHENTICATION)) {
            Log.e(TAG, "WEB_AUTHENTICATION is not supported by this WebView.");
            return;
        }

        try {
            WebSettingsCompat.setWebAuthenticationSupport(
                webView.getSettings(),
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            );

            int support = WebSettingsCompat.getWebAuthenticationSupport(
                webView.getSettings()
            );

            Log.i(TAG, "WebAuthn support level: " + support);

            if (support != WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP) {
                Log.e(TAG, "WebAuthn app support was not enabled.");
                return;
            }

            webView.post(() -> {
                Log.i(TAG, "Reloading page after WebAuthn initialization.");
                webView.reload();
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to enable WebAuthn support.", e);
        }
    }

    @Override
    public void onDestroy() {
        if (networkNoticeManager != null) {
            networkNoticeManager.stop();
        }

        super.onDestroy();
    }

}
