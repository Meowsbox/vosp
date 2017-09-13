/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.RemoteException;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogPremiumRequiredColorPicker {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    public final String TAG = this.getClass().getName();

    private AlertDialog dialog;

    public DialogPremiumRequiredColorPicker build(final Context context, final IRemoteSipService sipService) throws RemoteException {
        LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = li.inflate(R.layout.dialog_pro_req_color, null);
        ((TextView) view.findViewById(R.id.tvCap)).setText(sipService.getLocalString("help_premium_feature_colorpicker", "Select from a range of material colors. Upgrade to unlock this and other features."));

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        final AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
        builder.setView(view);
        builder.setTitle(sipService.getLocalString("premium_features", "Premium Features"))

                .setPositiveButton(sipService.getLocalString("upgrade", "Upgrade"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        try {
                            new DialogPremiumUpgrade().buildAndShow(context, sipService);
                        } catch (RemoteException e) {
                            if (DEBUG) e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton(sipService.getLocalString("close", "Close"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog = builder.create();
        return this;
    }

    public DialogPremiumRequiredColorPicker buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
        build(context, sipService);
        dialog.show();
        return this;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public void show() {
        if (dialog != null) dialog.show();
    }

}
