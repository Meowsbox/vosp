/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.graphics.Color;

import com.meowsbox.vosp.common.LocalStore;

import java.util.Locale;

/**
 * Created by dhon on 8/2/2016.
 */
public class Prefs {
    /**
     * Start service on boot
     */
    public static final String KEY_ONBOOT_ENABLE = "pref_onboot_enable";
    /**
     * Acquire WifiLock when using Wifi, may be needed for devices without mobile data fallback
     */
    public static final String KEY_WIFI_LOCK_ENABLE = "pref_wifi_lock_enable";
    /**
     * Android Doze workaround - schedules all native timers as user alarms.
     */
    public static final String KEY_DOZE_WORKAROUND_ENABLE = "pref_doze_wa";
    /**
     * Keep alive interval for mobile
     */
    public static final String KEY_KA_TCP_MOBILE = "pref_ka_tcp_mobile";
    /**
     * Keep alive interval for wifi
     */
    public static final String KEY_KA_TCP_WIFI = "pref_ka_tcp_wifi";
    /**
     * Enable to disable the associated user account.
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_ENABLED = "enabled";
    /**
     * Friendly name for account to be displayed in UI
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_NAME = "name";
    /**
     * SIP user name for account login
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_USER = "user";
    /**
     * SIP secret for account login
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_SECRET = "secret";
    /**
     * SIP server host IP or URL without specified port.
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_SERVER = "server";
    /**
     * Outbound SIP Proxy IP or URL without specified port.
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_SERVER_OUT_PROXY = "server_out_proxy";
    /**
     * SIP server port
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_PORT = "port";
    /**
     * SIP outbound proxy server port
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_PORT_OUT_PROXY = "port_out_proxy";
    /**
     * Enable to prefer TCP transport, otherwise defaults to UDP.
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_TCP = "tcp";
    /**
     * SIP expiry interval
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_REG_EXPIRE = "reg_expire";

    /**
     * Enable STUN where server is available. RFC 5389
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_USE_STUN = "use_stun";

    /**
     * RTP port range start
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_MEDIA_PORT_BEGIN = "media_port_begin";
    /**
     * RTP port range end
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_MEDIA_PORT_END = "media_port_end";

    /**
     * Media RX Audio Pre-Amp. Float value, 0f mute, 1f neutral. Note values above 1f may cause clipping.
     */
    public static final String KEY_MEDIA_PREAMP_RX = "media_preamp_rx";

    /**
     * Media TX Audio Pre-Amp. Float value, 0f mute, 1f neutral. Note values above 1f may cause clipping.
     */
    public static final String KEY_MEDIA_PREAMP_TX = "media_preamp_tx";

    /**
     * Media RX Audio Pre-Amp default value.
     */
    public static final float KEY_MEDIA_PREAMP_RX_DEFAULT = 2f;

    /**
     * Media RX Audio Pre-Amp default value.
     */
    public static final float KEY_MEDIA_PREAMP_TX_DEFAULT = 1.5f;

    /**
     * Use ICE (interactive connectivity establishment) RFC 5245
     * Use getAccountKey method interact with his preference.
     */
    public static final String KEY_ACCOUNT_SUF_USE_ICE = "use_ice";
    public static final String KEY_DOZE_DISABLE = "pref_doze_disable";
    public static final String KEY_DOZE_DISABLE_SU = "pref_doze_disable_su";
    public static final String KEY_STUN_SERVER = "pref_stun_server";
    public static final String KEY_HANDLE_OUTGOING_ALL = "pref_handle_outgoing_all";
    public static final String KEY_UI_COLOR_PRIMARY = "pref_ui_color_primary";

    public static final String KEY_VIBRATE_ON_RING = "pref_vibrate_on_ring";

    public static final String KEY_SHOW_ALTERNATE_LAUNCHERS = "pref_show_alternate_launchers";

    public static final String KEY_MEDIA_QUALITY = "pref_media_quality";

    public static final String KEY_UI_COLOR_NOTIF_DARK = "pref_ui_color_notif_dark";

    public static final String KEY_CALL_RECORD_AUTO = "pref_call_record_auto";

    public static final String KEY_TIP_SEEN_FIND_CALL = "pref_tip_seen_find_call";

    /**
     * Default UI Color: Primary
     */
    public static final int DEFAULT_UI_COLOR_PRIMARY = Color.parseColor("#009688");

    /**
     * Nonce flag for user has been shown welcome dialog
     */
    public static final String KEY_FLAG_SEEN_WELCOME = "pref_flag_seen_welcome";

    /**
     * Timestamp when user clicked to continue at welcome dialog
     */
    public static final String KEY_TS_SEEN_WELCOME = "pref_ts_seen_welcome";

    /**
     * Timestamp when user clicked to accept or decline call recording legal notice dialog
     */
    public static final String KEY_TS_SEEN_CALL_RECORD_LEGAL= "pref_ts_seen_call_record_legal";

    /**
     * User result of call recording legal notice dialog
     */
    public static final String KEY_BOOL_ACCEPT_CALL_RECORD_LEGAL= "pref_bool_accept_call_record_legal";

    /**
     * Timestamp when user completed the local app rating dialog
     */
    public static final String KEY_FLAG_RATE_LOCAL_TS = "pref_ts_rate_local_completed";
    /**
     * int local app rating assigned by user
     */
    public static final String KEY_FLAG_RATE_LOCAL = "pref_int_rate_local";

    /**
     * Timestamp when user clicked to rate app on Google Play
     */
    public static final String KEY_FLAG_RATE_PLAYAPP_TS = "pref_ts_rate_playapp_completed";

    /**
     * Key prefix for account specific preferences to be used by getAccountKey method to create a valid preference lookup key.
     */
    private static final String KEY_ACCOUNT_PREFIX = "pref_account";


    public static final String KEY_STACK_RESTART_ON_CREATE_TRANSPORT_FAILURE_COUNT= "pref_stackrestartoncreatetransportfailurecount";

    /**
     * Generate the storage key pointing to the account data required.
     *
     * @param accountIndex
     * @param specific
     * @return
     */
    public static String getAccountKey(int accountIndex, String specific) {
        StringBuilder sb = new StringBuilder();
        sb.append(KEY_ACCOUNT_PREFIX);
        sb.append('_');
        sb.append(String.format(Locale.US, "%3d", accountIndex));
        sb.append('_');
        sb.append(specific);
        return sb.toString();
    }

    /**
     * Call this method to initialize and rsCommit default preferences on first run.
     * Note that any preferences not set here will default to the caller method's default value.
     *
     * @param localStore
     */
    public static void initPrefs(LocalStore localStore) {
        localStore.setInt(KEY_UI_COLOR_PRIMARY, DEFAULT_UI_COLOR_PRIMARY);
        localStore.setFloat(KEY_MEDIA_PREAMP_RX, KEY_MEDIA_PREAMP_RX_DEFAULT);
        localStore.setBoolean(KEY_ONBOOT_ENABLE, true);
        localStore.setBoolean(KEY_WIFI_LOCK_ENABLE, false);
        localStore.setBoolean(KEY_HANDLE_OUTGOING_ALL, true);
        localStore.setBoolean(KEY_VIBRATE_ON_RING, true);
        localStore.setBoolean(KEY_DOZE_WORKAROUND_ENABLE, true);
        localStore.setBoolean(KEY_SHOW_ALTERNATE_LAUNCHERS, false);
        localStore.commit(true);
    }

}
