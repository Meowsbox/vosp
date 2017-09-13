/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.service.Prefs;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogWelcome {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    public static final int FLAG_SEEN_NONCE = 1; // int is stored in pref on dialog dismiss, clear or increment static to show anew
    public final String TAG = this.getClass().getName();
    private Dialog dialog;
    private View.OnClickListener onClickListener;

    public DialogWelcome build(final Context context, final IRemoteSipService sipService) throws RemoteException {
        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = li.inflate(R.layout.dialog_fragment_welcome, null);

        WebView wvWelcome = (WebView) view.findViewById(R.id.wvWelcome);
        wvWelcome.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view.getContext().startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } else {
                    return false;
                }
            }
        });
        final String url_dialog_welcome = sipService.getLocalString("url_dialog_welcome", "file:///android_asset/welcome-en/index.html");
        wvWelcome.loadUrl(url_dialog_welcome);

        wvWelcome.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode != KeyEvent.KEYCODE_BACK) return false;
                if (v instanceof WebView) {
                    if (((WebView) v).canGoBack()) {
                        ((WebView) v).goBack();
                        return true;
                    }
                }
                return false;
            }
        });

        try {
            Button bDismiss = (Button) view.findViewById(R.id.bDismiss);
            bDismiss.setText(sipService.rsGetString("complete_setup", "Complete Setup"));
            bDismiss.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (sipService != null) {
                            sipService.rsSetInt(Prefs.KEY_FLAG_SEEN_WELCOME, FLAG_SEEN_NONCE);
                            sipService.rsSetLong(Prefs.KEY_TS_SEEN_WELCOME, System.currentTimeMillis());
                        }
                    } catch (RemoteException e) {
                        if (DEBUG) e.printStackTrace();
                    }
                    if (onClickListener != null) onClickListener.onClick(v);
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
            return null;
        }


        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        Rect r = new Rect();
        dialog.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);
        view.setMinimumWidth((int) (r.width() * 0.9f));
        view.setMinimumHeight((int) (r.height() * 0.9f));
        dialog.setContentView(view);

        return this;
    }

    public DialogWelcome buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
        build(context, sipService);
        dialog.show();
        return this;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public DialogWelcome setDismissOnClickListener(View.OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
        return this;
    }

    public void show() {
        if (dialog != null) dialog.show();
    }

}
