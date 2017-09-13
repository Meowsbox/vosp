/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

/**
 * Created by dhon on 1/10/2017.
 */

public class SipServiceMessages {
    public static final int MSG_NULL = 0;
    public static final int MSG_CALL_ANSWER = 1;
    public static final int MSG_CALL_DECLINE = 2;
    public static final int MSG_CALL_HOLD = 3;
    public static final int MSG_CALL_HOLD_RESUME = 4;
    public static final int MSG_CALL_HANGUP = 5;
    public static final int MSG_CALL_MISSED_DISMISS = 6;
    public static final int MSG_CALL_MUTE = 7;
    public static final int MSG_CALL_MUTE_OFF = 8;

    public static final int MSG_SHOW_CALL_LOG= 10;

    public static final int MSG_CALLOUT_DEFAULT = 100;
    public static final int MSG_NEWOUTGOINGCALL = 200;

    public static final int MSG_CALL_INCOMING = 300;

    public static final int MSG_CONNECTIVITY_CHANGED = 1000;
    public static final int MSG_ACCOUNTS_REGISTER_ALL = 1001;
    public static final int MSG_ACCOUNTS_UNREGISTER_ALL = 1002;
    public static final int MSG_ACCOUNTS_REGISTER_SINGLE = 1003;
    public static final int MSG_SCREEN_STATE_CHANGED = 1004;
    public static final int MSG_DOZE_STATE_CHANGED = 1010;

    public static final int MSG_WAKELOCK_ACQUIRE = 1011;
    public static final int MSG_WAKELOCK_RELEASE = 1012;
    public static final String EXTRA_WAKELOCK_TAG = "wakelock_tag";

    public static final int MSG_EP_TRANSPORT_FLUSH = 1100;
    //    public static final int MSG_EP_TRANSPORT_REGEN = 1101;
    public static final int MSG_STACK_RESTART = 1102;
    public static final int MSG_STACK_START = 1103;
    public static final int MSG_STACK_START_AND_REGISTER = 1104;
    public static final int MSG_STACK_STOP = 1105;
    public static final int MSG_SERVICE_STOP = 1106;

    public static final int MSG_WATCHDOG_REG_RETRY = 1200;

    public static final int MSG_REQ_BATTERY_SAVER_EXEMPT = 5000;
}
