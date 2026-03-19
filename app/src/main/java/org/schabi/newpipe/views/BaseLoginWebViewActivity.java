package org.schabi.newpipe.views;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;

public abstract class BaseLoginWebViewActivity extends AppCompatActivity {

    protected WebView webView;
    private boolean loginHandled = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_webview);

        webView = findViewById(R.id.login_webview);

        final CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        configureWebView();
        webView.setWebViewClient(createWebViewClient());
        loadLoginUrl();
    }

    protected abstract String getLoginUrl();

    protected abstract String getSuccessCookieIndicator();

    protected abstract void configureWebView();

    protected abstract WebViewClient createWebViewClient();

    protected abstract void handleSuccessfulLogin(String cookies);

    protected void loadLoginUrl() {
        webView.loadUrl(getLoginUrl());
    }

    protected void finishWithResult(final Intent intent) {
        if (loginHandled) {
            return;
        }

        loginHandled = true;
        setResult(RESULT_OK, intent);
        runOnUiThread(() -> {
            if (isFinishing()) {
                return;
            }

            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            webView.destroy();
            finish();
        });
    }

    protected class StandardWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(final WebView view, final String url) {
            super.onPageFinished(view, url);
            final String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && cookies.contains(getSuccessCookieIndicator())) {
                handleSuccessfulLogin(cookies);
            }
        }
    }
}
