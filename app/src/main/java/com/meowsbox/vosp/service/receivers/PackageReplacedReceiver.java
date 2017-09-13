/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.meowsbox.vosp.service.SipService;

public class PackageReplacedReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            completeWakefulIntent(intent);
            return;
        }
        String action = intent.getAction();
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Intent serviceIntent = new Intent(context, SipService.class);
            serviceIntent.putExtra(SipService.EXTRA_ON_PKG_REPLACED, true);
            context.startService(serviceIntent);
        }
        completeWakefulIntent(intent);
    }
}
