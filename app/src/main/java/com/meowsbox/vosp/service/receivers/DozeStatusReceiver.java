/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

public class DozeStatusReceiver extends BroadcastReceiver {
    final static boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();


    public static void register(Context context, DozeStatusReceiver dozeStatusReceiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        context.registerReceiver(dozeStatusReceiver, filter);
    }

    public static void unregister(Context context, DozeStatusReceiver dozeStatusReceiver) {
        if (context == null || dozeStatusReceiver == null) return;
        context.unregisterReceiver(dozeStatusReceiver);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SipService sipService = SipService.getInstance();
        if (sipService == null) return; // service not yet running
        if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());
        if (intent.getAction().equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
            sipService.onDozeStateChanged();
        }
        // else some other intent not handled here
    }
}
