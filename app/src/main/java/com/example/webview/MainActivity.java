package com.example.webview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import au.com.ninthavenue.patterns.android.dialogs.FileChooser;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity {
    private WebView mWebView;
    private String certFileName = "client.cert";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // initialize web view
        mWebView = findViewById(R.id.activity_main_webview);
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // load certificate if it does not exist
        File certFile = new File(getFilesDir(), certFileName);
        if (!certFile.exists()) {
            new FileChooser(this).setFileListener(new FileChooser.FileSelectedListener() {
                @Override
                public void fileSelected(final File file) {
                    try {
                        FileInputStream in = new FileInputStream(file);
                        FileOutputStream out = openFileOutput(certFileName, Context.MODE_PRIVATE);
                        // transfer bytes from in to out
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        // close streams
                        in.close();
                        out.close();
                    } catch (Exception x) {
                        Log.e(TAG, x.getMessage());
                        x.printStackTrace();
                    }

                }
            }).showDialog();
        }
    }

    class MyWebViewClient extends WebViewClient {
        private X509Certificate[] mCertificates;
        private PrivateKey mPrivateKey;

        private void loadCertificateAndPrivateKey() {
            try {
                InputStream certificateFileStream = openFileInput(certFileName);
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                String password = "";
                // load chain
                keyStore.load(certificateFileStream, password.toCharArray());
                //
                Enumeration<String> aliases = keyStore.aliases();
                String alias = aliases.nextElement();
                // use first certificate
                Key key = keyStore.getKey(alias, password.toCharArray());
                if (key instanceof PrivateKey) {
                    mPrivateKey = (PrivateKey)key;
                    Certificate cert = keyStore.getCertificate(alias);
                    mCertificates = new X509Certificate[1];
                    mCertificates[0] = (X509Certificate)cert;
                }
                // close file
                certificateFileStream.close();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }

        @Override
        public void onReceivedClientCertRequest(WebView view, final ClientCertRequest request) {
            if (mCertificates == null || mPrivateKey == null) {
                loadCertificateAndPrivateKey();
            }
            request.proceed(mPrivateKey, mCertificates);
        }
    }
}
