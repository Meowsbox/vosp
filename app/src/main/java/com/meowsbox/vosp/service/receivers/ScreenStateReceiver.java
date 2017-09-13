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

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

public class ScreenStateReceiver extends BroadcastReceiver {
    final static boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();


    public static void register(Context context, ScreenStateReceiver screenStateReceiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(screenStateReceiver, filter);
    }

    public static void unregister(Context context, ScreenStateReceiver screenStateReceiver) {
        if (context == null || screenStateReceiver == null) return;
        context.unregisterReceiver(screenStateReceiver);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SipService sipService = SipService.getInstance();
        if (sipService == null) return; // service not yet running
        if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());

        String action = intent.getAction();
        if (action.equals(Intent.ACTION_SCREEN_ON)) {
            sipService.onScreenStateChanged(true);
        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            sipService.onScreenStateChanged(false);
        } else sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "Unhandled intent action");

    }
}
