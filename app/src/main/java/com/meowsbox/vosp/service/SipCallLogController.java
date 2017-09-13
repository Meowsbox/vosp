/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.common.Logger;

/**
 * Created by dhon on 12/14/2016.
 */

public class SipCallLogController {
    public final static boolean DEBUG = SipService.DEBUG;
    public final static int CALL_TYPE_OUTGOING = 1;
    public final static int CALL_TYPE_OUTGOING_BUSY = 2;
    public final static int CALL_TYPE_INCOMING = 3;
    public final static int CALL_TYPE_INCOMING_MISSED = 4;
    public final static int CALL_TYPE_INCOMING_DECLINED = 5;
    public final static int CALL_TYPE_INCOMING_BUSY = 6;
    public final String TAG = this.getClass().getName();
    private Logger gLog;
    private SipService sipService = null;
    private Context serviceContext = null;

    public SipCallLogController(SipService sipService) {
//        if (DEBUG) gLog.lBegin();
        gLog = SipService.getInstance().getLoggerInstanceShared();
        this.sipService = sipService;
        serviceContext = sipService.getServiceContext();
//        if (DEBUG) gLog.lEnd();
    }

    void putCallLog(int sipCallResult, long callStart, long callDuration, String name, String number) {
        switch (sipCallResult) {
            case CALL_TYPE_OUTGOING:
            case CALL_TYPE_OUTGOING_BUSY:
                putToCallLog(CallLog.Calls.OUTGOING_TYPE, callStart, callDuration, name, number);
                break;
            case CALL_TYPE_INCOMING_BUSY:
            case CALL_TYPE_INCOMING_MISSED:
                putToCallLog(CallLog.Calls.MISSED_TYPE, callStart, callDuration, name, number);
                break;
            case CALL_TYPE_INCOMING:
            case CALL_TYPE_INCOMING_DECLINED:
                putToCallLog(CallLog.Calls.INCOMING_TYPE, callStart, callDuration, name, number);
                break;
            default:
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Unhandled callType");
                break;
        }
    }

    private void putToCallLog(int callLogType, long callStart, long durationSeconds, String name, String number) {
        ContentValues cv = new ContentValues();
        cv.put(CallLog.Calls.TYPE, callLogType);
        cv.put(CallLog.Calls.DATE, callStart);
        cv.put(CallLog.Calls.NEW, 1); // flag not yet seen by user?
        cv.put(CallLog.Calls.DURATION, durationSeconds);
        if (name != null) cv.put(CallLog.Calls.CACHED_NAME, name);
        if (number != null) cv.put(CallLog.Calls.NUMBER, number);
        serviceContext.getContentResolver().insert(CallLog.Calls.CONTENT_URI, cv);
    }
}
