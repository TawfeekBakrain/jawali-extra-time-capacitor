package com.jawali.extratime;

import android.os.Bundle;
import android.webkit.WebView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bridge bridge = getBridge();

        if (bridge == null) {
            android.util.Log.e(
                "ExtraTimeWebAuthn",
                "Capacitor bridge is not ready."
            );
            return;
        }

        WebView webView = bridge.getWebView();

        if (webView == null) {
            android.util.Log.e(
                "ExtraTimeWebAuthn",
                "WebView is not available."
            );
            return;
        }

        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_AUTHENTICATION)) {

            try {
                // Stop the initial navigation before configuring WebAuthn.
                webView.stopLoading();

                WebSettingsCompat.setWebAuthenticationSupport(
                    webView.getSettings(),
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                );

                android.util.Log.i(
                    "ExtraTimeWebAuthn",
                    "WebAuthn app support configured; reloading page."
                );

                // Reload so the page can initialize with the new setting.
                webView.post(() -> webView.reload());

            } catch (Exception e) {
                android.util.Log.e(
                    "ExtraTimeWebAuthn",
                    "Failed to configure WebAuthn support.",
                    e
                );
            }

        } else {
            android.util.Log.e(
                "ExtraTimeWebAuthn",
                "This WebView does not support WEB_AUTHENTICATION."
            );
        }
    }
}
