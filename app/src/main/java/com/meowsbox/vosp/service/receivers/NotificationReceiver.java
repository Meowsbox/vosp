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

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.SipService;
import com.meowsbox.vosp.service.SipServiceMessages;

/**
 * Created by dhon on 11/7/2016.
 */

public class NotificationReceiver extends BroadcastReceiver {
    public final static boolean DEBUG = SipService.DEBUG;
    public static final String ACTION_IGNORE_INCOMING_CALL = "com.meowsbox.internal.vosp.ACTION_IGNORE_INCOMING_CALL";
    public static final String ACTION_DECLINE_INCOMING_CALL = "com.meowsbox.internal.vosp.ACTION_DECLINE_INCOMING_CALL";
    public static final String ACTION_ANSWER_VOICE_INCOMING_CALL = "com.meowsbox.internal.vosp.ACTION_ANSWER_VOICE_INCOMING_CALL";
    public static final String ACTION_HOLD_ONGOING_CALL = "com.meowsbox.internal.vosp.ACTION_HOLD_ONGOING_CALL";
    public static final String ACTION_HOLD_RESUME_ONGOING_CALL = "com.meowsbox.internal.vosp.ACTION_HOLD_RESUME_ONGOING_CALL";
    public static final String ACTION_HANG_UP_ONGOING_CALL = "com.meowsbox.internal.vosp.ACTION_HANG_UP_ONGOING_CALL";
    public static final String ACTION_DISMISS_MISSED_CALL = "com.meowsbox.internal.vosp.ACTION_DISMISS_MISSED_CALL";
    public static final String ACTION_SHOW_CALL_LOG = "com.meowsbox.internal.vosp.ACTION_SHOW_CALL_LOG";
    public final String TAG = this.getClass().getName();
    Logger gLog = new Logger(DialerApplication.LOGGER_VERBOSITY);

    @Override
    public void onReceive(Context context, Intent intent) {
        SipService sipService = SipService.getInstance();
        if (sipService == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "sipService NULL"); // intent lost
            return;
        }
        if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "onReceive");

        final String action = intent.getAction();
        switch (action) {
            case ACTION_ANSWER_VOICE_INCOMING_CALL:
                // no need to cancel notification - it will be updated
                callAnswer(sipService, intent);
                break;
            case ACTION_DECLINE_INCOMING_CALL:
                callDecline(sipService, intent);
                break;
            case ACTION_HOLD_ONGOING_CALL:
                callHold(sipService, intent);
                break;
            case ACTION_HOLD_RESUME_ONGOING_CALL:
                callHoldResume(sipService, intent);
                break;
            case ACTION_HANG_UP_ONGOING_CALL:
                callHangup(sipService, intent);
                break;
            case ACTION_IGNORE_INCOMING_CALL:
                callIgnore(sipService, intent);
                break;
            case ACTION_DISMISS_MISSED_CALL:
                callMissedDismiss(sipService, intent);
                break;
            case ACTION_SHOW_CALL_LOG:
                showCallLog(sipService,intent);
                break;
            default:
                if (DEBUG) sipService.getLoggerInstanceShared().l(TAG, Logger.lvDebug, "Unhandled action: " + action);
        }
    }

    private void callAnswer(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
//        sipService.callAnswer(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_ANSWER;
        message.setData(extras);
        sipService.queueCommand(message);
    }

    private void callDecline(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
//        sipService.callDecline(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_DECLINE;
        message.setData(extras);
        sipService.queueCommand(message);
    }

    private void callHangup(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
//        sipService.callHangup(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_HANGUP;
        message.setData(extras);
        sipService.queueCommand(message);
    }

    private void callHold(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
//        sipService.callHold(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_HOLD;
        message.setData(extras);
        sipService.queueCommand(message);
    }

    private void callHoldResume(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
//        sipService.callHoldResume(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_HOLD_RESUME;
        message.setData(extras);
        sipService.queueCommand(message);
    }

    private void callIgnore(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
        sipService.ringAndVibe(false);
    }

    private void callMissedDismiss(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_MISSED_DISMISS;
        message.setData(extras);
        sipService.queueCommand(message);
    }

    private void showCallLog(SipService sipService, Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) return;
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_SHOW_CALL_LOG;
        message.setData(extras);
        sipService.queueCommand(message);
    }


}
