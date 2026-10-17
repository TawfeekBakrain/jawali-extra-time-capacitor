package com.jawali.extratime;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import java.util.Locale;

public final class NetworkNoticeManager {

    private final BridgeActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private WebView webView;

    private boolean started = false;
    private boolean initialized = false;
    private boolean wasOnline = false;
    private boolean wasOffline = false;

    private String currentState = "unknown";

    public NetworkNoticeManager(BridgeActivity activity) {
        this.activity = activity;
    }

    public void start() {
        if (started || activity.isFinishing()) {
            return;
        }

        started = true;

        ensureWebViewReady();

        connectivityManager = (ConnectivityManager)
                activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {
                inspectCurrentNetwork();
            }

            @Override
            public void onCapabilitiesChanged(
                    Network network,
                    NetworkCapabilities capabilities) {

                inspectCapabilities(capabilities);
            }

            @Override
            public void onLost(Network network) {
                handler.post(() -> {
                    if (!started) {
                        return;
                    }

                    initialized = true;
                    wasOnline = false;
                    wasOffline = true;
                    currentState = "offline";

                    showNotice(
                            "لا يوجد اتصال بالإنترنت",
                            "#b42318",
                            0
                    );
                });
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(
                    networkCallback
            );

            inspectCurrentNetwork();

        } catch (Exception exception) {
            android.util.Log.e(
                    "ExtraTimeNetwork",
                    "Unable to register network callback",
                    exception
            );
        }
    }

    private void ensureWebViewReady() {
        if (!started || activity.isFinishing()) {
            return;
        }

        if (activity.getBridge() != null) {
            WebView candidate = activity.getBridge().getWebView();

            if (candidate != null) {
                boolean newlyAttached = webView != candidate;
                webView = candidate;

                if (newlyAttached) {
                    inspectCurrentNetwork();
                }
                return;
            }
        }

        handler.postDelayed(this::ensureWebViewReady, 500);
    }

    private void inspectCurrentNetwork() {
        if (!started || connectivityManager == null) {
            return;
        }

        Network network = connectivityManager.getActiveNetwork();

        NetworkCapabilities capabilities = network == null
                ? null
                : connectivityManager.getNetworkCapabilities(network);

        if (capabilities == null) {
            handler.post(() -> setOffline());
            return;
        }

        inspectCapabilities(capabilities);
    }

    private void inspectCapabilities(NetworkCapabilities capabilities) {
        boolean hasInternet =
                capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                && capabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                );

        boolean weak = hasInternet && isWeakConnection(capabilities);

        handler.post(() -> {
            if (!started) {
                return;
            }

            if (!hasInternet) {
                setOffline();
                return;
            }

            boolean firstCheck = !initialized;
            boolean restored = initialized && wasOffline;

            initialized = true;
            wasOnline = true;
            wasOffline = false;

            if (restored) {
                currentState = "online";
                showNotice(
                        "تمت استعادة الاتصال بالإنترنت",
                        "#16803c",
                        3000
                );

                if (weak) {
                    handler.postDelayed(() -> {
                        if (started && wasOnline
                                && "online".equals(currentState)) {
                            currentState = "weak";
                            showNotice(
                                    "اتصال الإنترنت ضعيف",
                                    "#d99a00",
                                    4000
                            );
                        }
                    }, 3000);
                }

            } else if (firstCheck) {
                if (weak) {
                    currentState = "weak";
                    showNotice(
                            "اتصال الإنترنت ضعيف",
                            "#d99a00",
                            4000
                    );
                } else {
                    currentState = "online";
                }

            } else if (weak && !"weak".equals(currentState)) {
                currentState = "weak";
                showNotice(
                        "اتصال الإنترنت ضعيف",
                        "#d99a00",
                        4000
                );

            } else if (!weak && "weak".equals(currentState)) {
                currentState = "online";
                hideNotice();
            }
        });
    }

    private boolean isWeakConnection(NetworkCapabilities capabilities) {
        int downstream =
                capabilities.getLinkDownstreamBandwidthKbps();

        int upstream =
                capabilities.getLinkUpstreamBandwidthKbps();

        boolean slowDownstream =
                downstream > 0 && downstream < 1500;

        boolean slowUpstream =
                upstream > 0 && upstream < 500;

        return slowDownstream || slowUpstream;
    }

    private void setOffline() {
        if (!started) {
            return;
        }

        initialized = true;
        wasOnline = false;
        wasOffline = true;
        currentState = "offline";

        showNotice(
                "لا يوجد اتصال بالإنترنت",
                "#b42318",
                0
        );
    }

    private void showNotice(
            String message,
            String background,
            long durationMillis) {

        if (activity.isFinishing()) {
            return;
        }

        if (webView == null) {
            ensureWebViewReady();
            return;
        }

        String safeMessage = message
                .replace("\\", "\\\\")
                .replace("'", "\\'");

        String script = String.format(
                Locale.US,
                "(function(){"
                + "if(!document.body)return false;"
                + "var b=document.getElementById('jawaliConnectionNoticeV2');"
                + "if(!b){"
                + "b=document.createElement('div');"
                + "b.id='jawaliConnectionNoticeV2';"
                + "b.style.cssText="
                + "'position:fixed;top:8px;left:8px;right:8px;"
                + "z-index:2147483647;padding:12px;text-align:center;"
                + "border-radius:12px;color:white;font-weight:bold;"
                + "box-shadow:0 4px 16px #0003;"
                + "box-sizing:border-box';"
                + "document.body.appendChild(b);"
                + "}"
                + "b.textContent='%s';"
                + "b.style.background='%s';"
                + "b.style.display='block';"
                + "if(window.__jawaliNoticeTimer)"
                + "clearTimeout(window.__jawaliNoticeTimer);"
                + "if(%d>0){"
                + "window.__jawaliNoticeTimer=setTimeout(function(){"
                + "var n=document.getElementById('jawaliConnectionNoticeV2');"
                + "if(n)n.remove();"
                + "},%d);"
                + "}"
                + "return true;"
                + "})();",
                safeMessage,
                background,
                durationMillis,
                durationMillis
        );

        activity.runOnUiThread(() -> {
            if (started && webView != null && !activity.isFinishing()) {
                webView.evaluateJavascript(script, result -> {
                    if (started && !activity.isFinishing()
                            && "false".equals(result)) {
                        handler.postDelayed(
                                () -> showNotice(
                                        message,
                                        background,
                                        durationMillis
                                ),
                                500
                        );
                    }
                });
            }
        });
    }

    private void hideNotice() {
        if (activity.isFinishing()) {
            return;
        }

        if (webView == null) {
            ensureWebViewReady();
            return;
        }

        activity.runOnUiThread(() -> {
            if (!started || webView == null) {
                return;
            }

            webView.evaluateJavascript(
                    "(function(){"
                    + "var n=document.getElementById("
                    + "'jawaliConnectionNoticeV2');"
                    + "if(n)n.remove();"
                    + "if(window.__jawaliNoticeTimer)"
                    + "clearTimeout(window.__jawaliNoticeTimer);"
                    + "})();",
                    null
            );
        });
    }

    public void stop() {
        started = false;

        handler.removeCallbacksAndMessages(null);

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
            } catch (Exception ignored) {
                // Callback may already have been unregistered.
            }
        }

        networkCallback = null;
        connectivityManager = null;
        webView = null;
    }
}
