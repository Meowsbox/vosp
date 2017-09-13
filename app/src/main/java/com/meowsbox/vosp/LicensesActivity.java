/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class LicensesActivity extends Activity {
    public final static boolean DEBUG = DialerApplication.DEBUG;
    private WebView wvAboutMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) actionBar.hide();
        setContentView(R.layout.activity_licenses);
        wvAboutMain = (WebView) findViewById(R.id.wvLicenses);
        WebViewClient webClient = new WebViewClient();
        wvAboutMain.setWebViewClient(webClient);
        wvAboutMain.loadUrl("file:///android_asset/licenses-en/index.html"); // only available in English for legal
    }

}
