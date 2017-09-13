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
import android.content.IntentFilter;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;

public class ServiceStateReceiver extends WakefulBroadcastReceiver {
    final static boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();

    public static void register(Context context, ServiceStateReceiver serviceStateReceiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
        context.registerReceiver(serviceStateReceiver, filter);
    }

    public static void unregister(Context context, ServiceStateReceiver serviceStateReceiver) {
        if (context == null || serviceStateReceiver == null) return;
        context.unregisterReceiver(serviceStateReceiver);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SipService sipService = SipService.getInstance();
        if (sipService == null) return; // service not yet running
        if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());
        sipService.onAirplaneModeChanged(); // notify service
        completeWakefulIntent(intent);
    }
}
