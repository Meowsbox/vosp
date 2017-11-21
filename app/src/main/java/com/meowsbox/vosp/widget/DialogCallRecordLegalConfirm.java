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

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.service.Prefs;


/**
 * Created by dhon on 6/22/2017.
 */

public class DialogCallRecordLegalConfirm {
    public static final boolean DEBUG = DialerApplication.DEBUG;
    public final String TAG = this.getClass().getName();

    private AlertDialog dialog;

    public DialogCallRecordLegalConfirm build(final Context context, final IRemoteSipService sipService) throws RemoteException {

        final ContextThemeWrapper contextWrapper = new ContextThemeWrapper(context, R.style.Theme_AppCompat_Light_Dialog_Alert);
        final AlertDialog.Builder builder = new AlertDialog.Builder(contextWrapper);
        builder.setMessage(sipService.getLocalString("legal_call_record", "You agree to accept all liability and responsibility for the use of call recording in compliance with applicable: domestic and international laws, regulations, rules, and policies."));
        builder.setTitle(sipService.getLocalString("call_recording", "Call Recording"))
                .setPositiveButton(sipService.getLocalString("agree", "Agree"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (sipService == null) return;
                        try {
                            sipService.rsSetLong(Prefs.KEY_TS_SEEN_CALL_RECORD_LEGAL, System.currentTimeMillis());
                            sipService.rsSetBoolean(Prefs.KEY_BOOL_ACCEPT_CALL_RECORD_LEGAL, true);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton(sipService.getLocalString("decline", "Decline"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        try {
                            sipService.rsSetLong(Prefs.KEY_TS_SEEN_CALL_RECORD_LEGAL, System.currentTimeMillis());
                            sipService.rsSetBoolean(Prefs.KEY_BOOL_ACCEPT_CALL_RECORD_LEGAL, false);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
        dialog = builder.create();
        return this;
    }

    public DialogCallRecordLegalConfirm buildAndShow(final Context context, final IRemoteSipService sipService) throws RemoteException {
        build(context, sipService);
        if (dialog != null) dialog.show();
        return this;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    public void show() {
        if (dialog != null) dialog.show();
    }

}
