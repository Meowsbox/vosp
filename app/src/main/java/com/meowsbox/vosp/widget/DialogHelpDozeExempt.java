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
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.ContextThemeWrapper;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogHelpDozeExempt {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    public final String TAG = this.getClass().getName();

    private AlertDialog dialog;

    public DialogHelpDozeExempt build(final Context context, final IRemoteSipService sipService) throws RemoteException {

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        final AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
        builder.setMessage(sipService.getLocalString("help_doze_exempt", "Android automatically blocks apps from waking the device after a set period, incoming calls during this time will be lost. \r\n\r\nPlease set the VOSP app to NOT OPTIMIZED."));
        builder.setTitle(sipService.getLocalString("battery_optimization_blocking_calls", "Android Battery Optimization"))
                .setPositiveButton(sipService.getLocalString("battery_settings", "Battery Settings"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (Build.VERSION.SDK_INT < 23)
                            return; // dialog should never have been shown in the first place
                        final Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                        context.startActivity(intent);
                    }
                })
                .setNegativeButton(sipService.getLocalString("later", "Later"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        dialog = builder.create();
        return this;
    }

    public DialogHelpDozeExempt buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
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
