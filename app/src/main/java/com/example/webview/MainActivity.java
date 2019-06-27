package com.example.webview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

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
    private String localURL = "file:///android_asset/index.html";
    private String serverURL = localURL;
    private String serverProperty = "serverURL";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // initialize web view
        mWebView = findViewById(R.id.activity_main_webview);
        mWebView.setWebViewClient(new MyWebViewClient());
        mWebView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // load server url
        serverURL = getSharedPreferences("default", MODE_PRIVATE).getString(serverProperty, localURL);
        if (serverURL.equals(localURL)) {
            // ask user for url
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("URL");
            // Set up the input
            final EditText input = new EditText(this);
            // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);
            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    serverURL = input.getText().toString();
                    // save property
                    SharedPreferences.Editor editor = getSharedPreferences("default", MODE_PRIVATE).edit();
                    editor.putString(serverProperty, serverURL);
                    editor.apply();
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            // show dialog
            builder.show();
        }
        // load certificate if it does not exist
        File certFile = new File(getFilesDir(), certFileName);
        if (!certFile.exists()) {
            // ask user to select cert file
            FileChooser certChooser = new FileChooser(this).setFileListener(new FileChooser.FileSelectedListener() {
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
                    }
                }
            });
            // show dialog
            certChooser.setExtension("p12");
            certChooser.showDialog();
        }
        // open link
        mWebView.loadUrl(serverURL);
        // force reload page
        mWebView.reload();
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
