package org.schabi.newpipe.views;

import android.content.Intent;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

import java.util.HashMap;
import java.util.Map;

public class BiliBiliLoginWebViewActivity extends BaseLoginWebViewActivity {

    @Override
    protected String getLoginUrl() {
        return "https://passport.bilibili.com/login";
    }

    @Override
    protected String getSuccessCookieIndicator() {
        return "SESSDATA=";
    }

    @Override
    protected void configureWebView() {
        final WebSettings settings = webView.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        final Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 "
                + "Safari/537.36");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,"
                + "image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.5");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("DNT", "1");
        headers.put("Connection", "keep-alive");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");

        webView.loadUrl(getLoginUrl(), headers);
    }

    @Override
    protected WebViewClient createWebViewClient() {
        return new StandardWebViewClient();
    }

    @Override
    protected void handleSuccessfulLogin(final String cookies) {
        final Intent intent = new Intent();
        intent.putExtra("cookies", cookies);
        finishWithResult(intent);
    }

    @Override
    protected void loadLoginUrl() {
        // Loaded in configureWebView with custom headers.
    }
}
