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
            android.util.Log.w(
                "ExtraTimeWebAuthn",
                "Capacitor bridge is not ready."
            );
            return;
        }

        WebView webView = bridge.getWebView();

        if (webView == null) {
            android.util.Log.w(
                "ExtraTimeWebAuthn",
                "WebView is not available."
            );
            return;
        }

        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_AUTHENTICATION)) {

            WebSettingsCompat.setWebAuthenticationSupport(
                webView.getSettings(),
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            );

            android.util.Log.i(
                "ExtraTimeWebAuthn",
                "WebAuthn app support enabled."
            );

        } else {
            android.util.Log.w(
                "ExtraTimeWebAuthn",
                "This WebView does not support WEB_AUTHENTICATION."
            );
        }
    }
}
