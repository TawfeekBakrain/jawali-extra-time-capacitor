package com.jawali.extratime;

import androidx.fragment.app.FragmentActivity;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.Executor;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public final class NativeBiometricBridge {
    private static final String TAG = "ExtraTimeNativeBio";
    private static final String KEYSTORE = "AndroidKeyStore";

    private final FragmentActivity activity;
    private final WebView webView;
    private final Executor executor;

    public NativeBiometricBridge(FragmentActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.executor = ContextCompat.getMainExecutor(activity);
    }

    private String aliasFor(String accountId) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(accountId.trim().getBytes(StandardCharsets.UTF_8));

        StringBuilder out = new StringBuilder("ExtraTimeBio_");
        for (byte b : hash) {
            out.append(String.format("%02x", b & 0xff));
        }
        return out.toString();
    }

    private JSONObject result(boolean success, String message) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("success", success);
        if (message != null) obj.put("message", message);
        return obj;
    }

    private void sendResult(String callbackId, JSONObject payload) {
        final String callback = JSONObject.quote(callbackId);
        final String json = payload.toString();

        webView.post(() -> webView.evaluateJavascript(
                "window.__extraTimeNativeBioResult(" +
                        callback + "," + json + ");",
                null
        ));
    }

    private void sendError(String callbackId, String message) {
        try {
            sendResult(callbackId, result(false, message));
        } catch (Exception e) {
            Log.e(TAG, "Could not create error response", e);
        }
    }

    private boolean biometricAvailable() {
        BiometricManager manager = BiometricManager.from(activity);
        return manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private Signature prepareSignature(String accountId, boolean create)
            throws Exception {
        String alias = aliasFor(accountId);
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);

        if (create && !store.containsAlias(alias)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, KEYSTORE
            );

            KeyGenParameterSpec.Builder builder =
                    new KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_SIGN |
                                    KeyProperties.PURPOSE_VERIFY
                    )
                    .setAlgorithmParameterSpec(
                            new ECGenParameterSpec("secp256r1")
                    )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                );
            } else {
                builder.setUserAuthenticationValidityDurationSeconds(-1);
            }

            generator.initialize(builder.build());
            generator.generateKeyPair();
            store.load(null);
        }

        if (!store.containsAlias(alias)) {
            throw new IllegalStateException(
                    "لا يوجد تسجيل بصمة لهذا الحساب على هذا الجهاز."
            );
        }

        PrivateKey privateKey = (PrivateKey) store.getKey(alias, null);
        if (privateKey == null) {
            throw new IllegalStateException(
                    "تعذر الوصول إلى مفتاح البصمة."
            );
        }

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        return signature;
    }

    @JavascriptInterface
    public void register(String accountId, String challenge, String callbackId) {
        activity.runOnUiThread(() -> {
            if (!biometricAvailable()) {
                sendError(callbackId,
                        "لا تتوفر بصمة قوية مسجلة على هذا الجهاز.");
                return;
            }

            try {
                Signature signature = prepareSignature(accountId, true);

                KeyStore store = KeyStore.getInstance(KEYSTORE);
                store.load(null);

                java.security.cert.Certificate certificate =
                        store.getCertificate(aliasFor(accountId));

                if (certificate == null) {
                    throw new IllegalStateException(
                            "تعذر استخراج المفتاح العام."
                    );
                }

                String publicKey = Base64.encodeToString(
                        certificate.getPublicKey().getEncoded(),
                        Base64.NO_WRAP
                );

                authenticateAndSign(
                        accountId, challenge, callbackId,
                        signature, publicKey
                );
            } catch (Exception e) {
                Log.e(TAG, "Native registration setup failed", e);
                sendError(callbackId,
                        "تعذر تجهيز تسجيل البصمة: " + e.getMessage());
            }
        });
    }

    @JavascriptInterface
    public void authenticate(
            String accountId, String challenge, String callbackId) {
        activity.runOnUiThread(() -> {
            if (!biometricAvailable()) {
                sendError(callbackId,
                        "لا تتوفر بصمة قوية مسجلة على هذا الجهاز.");
                return;
            }

            try {
                Signature signature = prepareSignature(accountId, false);
                authenticateAndSign(
                        accountId, challenge, callbackId, signature, null
                );
            } catch (Exception e) {
                Log.e(TAG, "Native authentication setup failed", e);
                sendError(callbackId,
                        "تعذر تجهيز التحقق بالبصمة: " + e.getMessage());
            }
        });
    }

    private void authenticateAndSign(
            String accountId,
            String challenge,
            String callbackId,
            Signature signature,
            String publicKey) {

        BiometricPrompt prompt = new BiometricPrompt(
                activity,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult authResult) {
                        super.onAuthenticationSucceeded(authResult);

                        try {
                            Signature authorized =
                                    authResult.getCryptoObject() == null
                                            ? null
                                            : authResult.getCryptoObject()
                                                    .getSignature();

                            if (authorized == null) {
                                sendError(callbackId,
                                        "تعذر إكمال التوقيع الآمن.");
                                return;
                            }

                            authorized.update(
                                    challenge.getBytes(StandardCharsets.UTF_8)
                            );

                            byte[] signed = authorized.sign();

                            JSONObject obj = result(true, null);
                            obj.put("signature", Base64.encodeToString(
                                    signed, Base64.NO_WRAP
                            ));

                            if (publicKey != null) {
                                obj.put("publicKey", publicKey);
                            }

                            sendResult(callbackId, obj);
                        } catch (Exception e) {
                            Log.e(TAG, "Challenge signing failed", e);
                            sendError(callbackId,
                                    "تعذر توقيع طلب التحقق.");
                        }
                    }

                    @Override
                    public void onAuthenticationError(
                            int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        sendError(callbackId,
                                errString == null
                                        ? "تم إلغاء التحقق بالبصمة."
                                        : errString.toString());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Android allows another biometric attempt.
                    }
                }
        );

        BiometricPrompt.PromptInfo info =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Extra Time")
                        .setSubtitle("تحقق من هويتك باستخدام البصمة")
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        )
                        .setNegativeButtonText("إلغاء")
                        .build();

        prompt.authenticate(
                info,
                new BiometricPrompt.CryptoObject(signature)
        );
    }

    @JavascriptInterface
    public void remove(String accountId, String callbackId) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            String alias = aliasFor(accountId);

            if (store.containsAlias(alias)) {
                store.deleteEntry(alias);
            }

            sendResult(callbackId, result(true, null));
        } catch (Exception e) {
            Log.e(TAG, "Could not remove native biometric key", e);
            sendError(callbackId, "تعذر إزالة مفتاح البصمة من الجهاز.");
        }
    }
}
