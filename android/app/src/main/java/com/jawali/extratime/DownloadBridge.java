package com.jawali.extratime;

import android.app.Activity;
import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebViewClient;
import android.view.ViewGroup;
import android.graphics.Color;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class DownloadBridge {

    private final Activity activity;
    private final WebView webView;

    public DownloadBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    private boolean isTrustedPage() {
        if (webView == null || webView.getUrl() == null) {
            return false;
        }

        Uri uri = Uri.parse(webView.getUrl());

        return "https".equalsIgnoreCase(uri.getScheme())
            && "jawali-extra-time-pro.vercel.app".equalsIgnoreCase(uri.getHost());
    }

    @JavascriptInterface
    public void saveBase64File(String filename, String mimeType, String base64) {
        if (!isTrustedPage()) {
            return;
        }

        if (filename == null || mimeType == null || base64 == null) {
            showMessage("بيانات الملف غير مكتملة");
            return;
        }

        String safeName = filename.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");

        if (safeName.length() > 150) {
            safeName = safeName.substring(0, 150);
        }

        String lowerName = safeName.toLowerCase(Locale.ROOT);
        String lowerMime = mimeType.toLowerCase(Locale.ROOT);

        boolean isExcel = lowerName.endsWith(".xlsx")
            && lowerMime.contains("spreadsheetml");

        boolean isPdf = lowerName.endsWith(".pdf")
            && lowerMime.equals("application/pdf");

        if (!isExcel && !isPdf) {
            showMessage("نوع الملف غير مدعوم");
            return;
        }

        // منع الملفات الكبيرة جدًا من استهلاك ذاكرة التطبيق.
        if (base64.length() > 28 * 1024 * 1024) {
            showMessage("حجم الملف أكبر من الحد المسموح");
            return;
        }

        byte[] bytes;

        try {
            bytes = Base64.decode(base64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            showMessage("تعذر قراءة الملف");
            return;
        }

        if (bytes.length == 0 || bytes.length > 20 * 1024 * 1024) {
            showMessage("حجم الملف غير صالح");
            return;
        }

        final String finalName = safeName;
        final String finalMime = isPdf
            ? "application/pdf"
            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, finalName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, finalMime);
                    values.put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Extra Time"
                    );

                    Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    Uri fileUri = activity.getContentResolver()
                        .insert(collection, values);

                    if (fileUri == null) {
                        throw new Exception("تعذر إنشاء الملف");
                    }

                    try (OutputStream output =
                             activity.getContentResolver().openOutputStream(fileUri)) {
                        if (output == null) {
                            throw new Exception("تعذر فتح الملف للحفظ");
                        }
                        output.write(bytes);
                        output.flush();
                    }
                } else {
                    File downloads = activity.getExternalFilesDir(
                        Environment.DIRECTORY_DOWNLOADS
                    );

                    if (downloads == null) {
                        throw new Exception("مجلد التنزيلات غير متاح");
                    }

                    File directory = new File(downloads, "Extra Time");

                    if (!directory.exists() && !directory.mkdirs()) {
                        throw new Exception("تعذر إنشاء مجلد التنزيلات");
                    }

                    File file = new File(directory, finalName);

                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(bytes);
                        output.flush();
                    }
                }

                showMessage("تم حفظ الملف في Downloads/Extra Time");
            } catch (Exception e) {
                showMessage("تعذر حفظ الملف: " + e.getMessage());
            }
        }).start();
    }


    @JavascriptInterface
    public void printHtmlAsPdf(String title, String html) {
        if (!isTrustedPage()) {
            showMessage("الموقع غير مصرح له بالطباعة");
            return;
        }

        if (html == null || html.isEmpty() || html.length() > 3 * 1024 * 1024) {
            showMessage("حجم تقرير PDF غير صالح");
            return;
        }

        String safeTitle = title == null ? "Extra Time Report" : title;
        safeTitle = safeTitle.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (safeTitle.length() > 100) {
            safeTitle = safeTitle.substring(0, 100);
        }

        final String reportTitle = safeTitle;
        final String reportHtml = html;

        activity.runOnUiThread(() -> {
            try {
                PrintManager printManager =
                    (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);

                if (printManager == null) {
                    showMessage("خدمة الطباعة غير متاحة على الجهاز");
                    return;
                }

                final WebView printWebView = new WebView(activity);
                printWebView.setBackgroundColor(Color.WHITE);
                printWebView.getSettings().setJavaScriptEnabled(false);
                printWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        try {
                            PrintAttributes attributes =
                                new PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .setResolution(
                                        new PrintAttributes.Resolution(
                                            "extra_time_pdf", "PDF", 300, 300
                                        )
                                    )
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build();

                            android.print.PrintDocumentAdapter webAdapter =
                                printWebView.createPrintDocumentAdapter(reportTitle);

                            android.print.PrintDocumentAdapter cleanupAdapter =
                                new android.print.PrintDocumentAdapter() {
                                    @Override
                                    public void onLayout(
                                        android.print.PrintAttributes oldAttributes,
                                        android.print.PrintAttributes newAttributes,
                                        android.os.CancellationSignal cancellationSignal,
                                        LayoutResultCallback callback,
                                        android.os.Bundle extras
                                    ) {
                                        webAdapter.onLayout(
                                            oldAttributes, newAttributes,
                                            cancellationSignal, callback, extras
                                        );
                                    }

                                    @Override
                                    public void onWrite(
                                        android.print.PageRange[] pages,
                                        android.os.ParcelFileDescriptor destination,
                                        android.os.CancellationSignal cancellationSignal,
                                        WriteResultCallback callback
                                    ) {
                                        webAdapter.onWrite(
                                            pages, destination, cancellationSignal, callback
                                        );
                                    }

                                    @Override
                                    public void onFinish() {
                                        try {
                                            webAdapter.onFinish();
                                        } finally {
                                            printWebView.postDelayed(() -> {
                                                android.view.ViewParent parent =
                                                    printWebView.getParent();

                                                if (parent instanceof ViewGroup) {
                                                    ((ViewGroup) parent).removeView(
                                                        printWebView
                                                    );
                                                }

                                                printWebView.stopLoading();
                                                printWebView.destroy();
                                            }, 1000);
                                        }
                                    }
                                };

                            printManager.print(
                                reportTitle,
                                cleanupAdapter,
                                attributes
                            );

                            showMessage("اختر حفظ بصيغة PDF من نافذة الطباعة");
                        } catch (Exception e) {
                            android.util.Log.e("ExtraTimeDownloads", "PDF print failed", e);
                            showMessage("تعذر فتح نافذة طباعة PDF");
                            android.view.ViewParent errorParent = printWebView.getParent();
                            if (errorParent instanceof ViewGroup) {
                                ((ViewGroup) errorParent).removeView(printWebView);
                            }

                            printWebView.stopLoading();
                            printWebView.destroy();
                        }
                    }
                });

                activity.addContentView(
                    printWebView,
                    new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                );

                printWebView.loadDataWithBaseURL(
                    "https://jawali-extra-time-pro.vercel.app/",
                    reportHtml,
                    "text/html",
                    "UTF-8",
                    null
                );
            } catch (Exception e) {
                android.util.Log.e("ExtraTimeDownloads", "Unable to start PDF printing", e);
                showMessage("تعذر بدء طباعة التقرير");
            }
        });
    }

    private void showMessage(String message) {
        activity.runOnUiThread(() ->
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        );
    }
}
