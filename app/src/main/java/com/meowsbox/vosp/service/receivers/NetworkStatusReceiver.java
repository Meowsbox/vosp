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
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

public class NetworkStatusReceiver extends WakefulBroadcastReceiver {
    final static boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();


    public static void register(Context context, NetworkStatusReceiver networkStatusReceiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(networkStatusReceiver, filter);
    }

    public static void unregister(Context context, NetworkStatusReceiver networkStatusReceiver) {
        if (context == null || networkStatusReceiver == null) return;
        context.unregisterReceiver(networkStatusReceiver);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SipService sipService = SipService.getInstance();
        if (sipService == null) return; // service not yet running
        if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());
        final Bundle intentExtras = intent.getExtras();
        sipService.onNetworkStateChanged(intentExtras); // notify service
        completeWakefulIntent(intent);
    }
}
