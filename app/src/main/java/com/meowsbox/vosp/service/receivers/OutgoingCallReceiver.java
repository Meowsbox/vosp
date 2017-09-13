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
import android.os.Bundle;
import android.os.Message;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.Prefs;
import com.meowsbox.vosp.service.SipService;
import com.meowsbox.vosp.service.SipServiceMessages;

public class OutgoingCallReceiver extends BroadcastReceiver {
    final static boolean DEBUG = SipService.DEBUG;
    public final String TAG = this.getClass().getName();


    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction() == Intent.ACTION_NEW_OUTGOING_CALL) {
            SipService sipService = SipService.getInstance();
            if (sipService == null) return; // service not yet running
            if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());

            if (!sipService.getLocalStore().getBoolean(Prefs.KEY_HANDLE_OUTGOING_ALL, true)) return; // ignore outgoing

            String phoneNumber = getResultData(); // Extract phone number reformatted by previous receivers

            if (phoneNumber == null)
                phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER); // No reformatted number, use the original
            if (phoneNumber == null) return;
            if (isOrderedBroadcast()) setResultData(null);


            // Pass call data to SipService as a command
            Message message = new Message();
            message.arg1 = SipServiceMessages.MSG_NEWOUTGOINGCALL;
            Bundle extras = new Bundle();
            extras.putString(Intent.EXTRA_PHONE_NUMBER, phoneNumber);
            message.setData(extras);
            sipService.queueCommand(message);
        } else if (intent.getAction() == "com.meowsbox.internal.vosp.dialerutil") {
            SipService sipService = SipService.getInstance();
            if (sipService == null) return; // service not yet running
            if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, intent.getAction());
            String phoneNumber = getResultData(); // Extract phone number reformatted by previous receivers

            if (phoneNumber == null)
                phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER); // No reformatted number, use the original
            if (phoneNumber == null) return;
            if (isOrderedBroadcast()) setResultData(null);


            // Pass call data to SipService as a command
            Message message = new Message();
            message.arg1 = SipServiceMessages.MSG_NEWOUTGOINGCALL;
            Bundle extras = new Bundle();
            extras.putString(Intent.EXTRA_PHONE_NUMBER, phoneNumber);
            message.setData(extras);
            sipService.queueCommand(message);
        }
    }
}
