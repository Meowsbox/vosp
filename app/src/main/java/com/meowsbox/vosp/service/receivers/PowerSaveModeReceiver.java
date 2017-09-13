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
import android.os.PowerManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;

public class PowerSaveModeReceiver extends WakefulBroadcastReceiver {
    static final boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();


    public static void register(Context context, PowerSaveModeReceiver powerSaveModeReceiver) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        context.registerReceiver(powerSaveModeReceiver, filter);
    }

    public static void unregister(Context context, PowerSaveModeReceiver powerSaveModeReceiver) {
        if (context == null || powerSaveModeReceiver == null) return;
        context.unregisterReceiver(powerSaveModeReceiver);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SipService sipService = SipService.getInstance();
        if (sipService == null) return; // service not yet running
        if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());
        sipService.onPowerSaveModeChanged(); // notify service
        completeWakefulIntent(intent);
    }
}
