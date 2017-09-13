/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

/**
 * Styles of service-sourced in-app notifications.
 * Created by dhon on 4/11/2017.
 */

public class InAppNotifications {
    // FLAGS and KEYS
    public static final String FLAG_STICKY = "sticky"; // flag message should be retained by SipService unless dismissed by user
    public static final String FLAG_NO_DISMISS = "no_dismiss"; // flag message can not be dismissed by user
    public static final String FLAG_TOUCH_TS_PROMO = "touch_ts_promo"; // flag message will update tsPromoDismiss on dismiss
    public static final String FLAG_TOUCH_TS_RATEUS = "touch_ts_rateus"; // flag message will update tsRateusDismiss on dismiss
    public static final String FLAG_DISMISS_SAME_TYPE = "dismiss_same_type"; // flag message dismiss will dismiss all other message of same type
    public static final String SMId = "smid"; // message id assigned by service
    public static final String IAN_TYPE = "ian_type";
    public static final String VIEW_TYPE = "view_type";

    public static final String KEY_PREF_ON_DISMISS_BOOL = "key_pref_bool_flag"; // message should set pref to true when swiped away

    // TYPE denotes the base purpose of the notification message
    public static final int TYPE_TEST = 0; // aka undefined
    public static final int TYPE_INFO = 1;
    public static final int TYPE_WARN = 2;
    public static final int TYPE_CALL_FAILED = 3;
    public static final int TYPE_SETTINGS = 4;

    public static final int TYPE_WARN_LOGIN_FAIL = 10;
    public static final int TYPE_WARN_NO_NETWORK = 11;
    public static final int TYPE_INFO_CONNECT_PROGRESS = 20;
    public static final int TYPE_INFO_INCALL_NETWORK_CHANGE = 21;
    public static final int TYPE_INFO_INCALL_TIP_FIND_CALL= 22;
    public static final int TYPE_INFO_DOZE_EXEMPT= 23;
    public static final int TYPE_INFO_BATTERY_SAVER_ENABLED= 24;

    public static final int TYPE_PROMO_1 = 500;

    public static final int TYPE_RATEUS_1 = 600;
    public static final int TYPE_RATEUS_2 = 601;

    // VIEWTYPE denotes the preferred xml view to be used in a notification message
    public static final int VIEWTYPE_TEST = 0; // aka undefined
    public static final int VIEWTYPE_INFO = 1;
    public static final int VIEWTYPE_WARN = 2;
    public static final int VIEWTYPE_CALL_FAILED = 3;
    public static final int VIEWTYPE_SETTINGS = 4;


    public static final int VIEWTYPE_PATRON_1 = 600;
    public static final int VIEWTYPE_RATEUS_1 = 650;
    public static final int VIEWTYPE_RATEUS_2 = 651;


    public static final String KEY_ONCLICK_HINT = "onclick_hint";
    public static final int VALUE_ONCLICK_HINT_TEST = 0; // aka undefined
    public static final int VALUE_ONCLICK_HINT_SETTINGS = 1;
    public static final int VALUE_ONCLICK_HINT_CALL_FAILED = 2; // show generic info about failed calls
    public static final int VALUE_ONCLICK_HINT_GOPRO = 3; // show premium dialog
    public static final int VALUE_ONCLICK_HINT_DOZE_DISABLE_HELP = 4;
    public static final int VALUE_ONCLICK_HINT_GORATEUS_LOCAL = 5; // show rate us local dialog
    public static final int VALUE_ONCLICK_HINT_GOPLAYAPP= 6; // show app on Google Play
    public static final int VALUE_ONCLICK_HINT_DOZE_OPTIMIZE_HELP = 7; // show doze battery optimization disable dialog
    public static final int VALUE_ONCLICK_HINT_GO_POWER_SAVE_MODE= 8;
}
