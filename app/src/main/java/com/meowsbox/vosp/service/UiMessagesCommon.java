/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.os.Bundle;

import org.pjsip.pjsua2.pjsip_status_code;

/**
 * UI Message builder
 * Created by dhon on 5/16/2017.
 */

public class UiMessagesCommon {

    static public void showAccountRegFailed(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("login_failed", "Login Failed"));
        bundle.putString("message2", sipService.getI18n().getString("check_settings", "Check settings"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_SETTINGS);
        sipService.showUiMessage(InAppNotifications.TYPE_WARN, bundle);
    }

    static public void showBluetoothScoUnavailable(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("bluetoth_sco_unavailable", "Bluetooth SCO unavailable"));
        sipService.showUiMessage(InAppNotifications.TYPE_INFO, bundle);
    }

    static public void showCallFailed(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("call_failed", "Call Failed"));
        bundle.putString("message2", sipService.getI18n().getString("touch_for_help_or_swipe_to_dismiss", "Touch for help or swipe to dismiss"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_CALL_FAILED);
        sipService.showUiMessage(InAppNotifications.TYPE_CALL_FAILED, bundle);
    }

    static public void showCallFailedWithPjSipStatusCode(SipService sipService, pjsip_status_code sc) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        bundle.putString("message", sipService.getI18n().getString("call_failed", "Call Failed"));
        if (sc == pjsip_status_code.PJSIP_SC_SERVICE_UNAVAILABLE)
            bundle.putString("message2", sipService.getI18n().getString("service_unavailble", "Invalid number or Service Unavailable"));
        if (sc == pjsip_status_code.PJSIP_SC_BAD_REQUEST)
            bundle.putString("message2", sipService.getI18n().getString("invalid_number", "Invalid number"));
        if (sc == pjsip_status_code.PJSIP_SC_ADDRESS_INCOMPLETE)
            bundle.putString("message2", sipService.getI18n().getString("invalid_number", "Invalid number"));
        if (sc == pjsip_status_code.PJSIP_SC_UNDECIPHERABLE)
            bundle.putString("message2", sipService.getI18n().getString("invalid_number", "Invalid number"));
        if (sc == pjsip_status_code.PJSIP_SC_SERVER_TIMEOUT)
            bundle.putString("message2", sipService.getI18n().getString("server_timeout", "SIP server not responding."));
        sipService.showUiMessage(InAppNotifications.TYPE_CALL_FAILED, bundle);
    }

    static public void showConnectProgress(SipService sipService, int progress) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_CONNECT_PROGRESS);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("connecting", "Connecting"));
        switch (progress) {
            case 1:
                bundle.putString("message2", sipService.getI18n().getString("in_progress_1", "waiting for server to respond..."));
                break;
            case 2:
                bundle.putString("message2", sipService.getI18n().getString("in_progress_2", "server not responding, retrying..."));
                break;
            case 3:
                bundle.putString("message2", sipService.getI18n().getString("in_progress_3", "server not responding, retrying again..."));
                break;
        }
        sipService.showUiMessage(InAppNotifications.TYPE_INFO_CONNECT_PROGRESS, bundle);
    }

    static public void showDozeDisableFailed(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("doze_disable_failed", "Failed to disable doze"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_DOZE_DISABLE_HELP);
        sipService.showUiMessage(InAppNotifications.TYPE_INFO, bundle);
    }

    static public void showDozeReqPerm(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("doze_disable_failed", "Failed to disable doze"));
        bundle.putString("message2", sipService.getI18n().getString("root_dump_perm_req", "ROOT or DUMP permission required"));

        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_DOZE_DISABLE_HELP);
        sipService.showUiMessage(InAppNotifications.TYPE_INFO, bundle);
    }

    static public void showDozeWhitelistRecommend(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_DOZE_EXEMPT);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("uim_doze_whitelist", "Missing calls?"));
        bundle.putString("message2", sipService.getI18n().getString("uim_doze_whitelist_m2", "Battery optimization may be blocking calls"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_DOZE_OPTIMIZE_HELP);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        sipService.showUiMessage(InAppNotifications.TYPE_INFO_DOZE_EXEMPT, bundle);
    }

    static public void showInCallNetworkChange(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_INCALL_NETWORK_CHANGE);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("network_changed", "Network Changed"));
        bundle.putString("message2", sipService.getI18n().getString("call_drop_or_misbehave", "Call may drop or misbehave"));
        sipService.showUiMessage(InAppNotifications.TYPE_INFO_INCALL_NETWORK_CHANGE, bundle);
    }

    static public void showInCallTipFindCall(SipService sipService, String PrefKey) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_INCALL_TIP_FIND_CALL);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("tip_find_call", "Tip: To find this call again..."));
        bundle.putString("message2", sipService.getI18n().getString("tip_find_call_cap", "Tap the active call notification or app history button."));
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        bundle.putString(InAppNotifications.KEY_PREF_ON_DISMISS_BOOL, PrefKey);
        sipService.showUiMessage(InAppNotifications.TYPE_INFO_INCALL_TIP_FIND_CALL, bundle);
    }

    static public void showLoginFailed(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("login_failed", "Login Failed"));
        bundle.putString("message2", sipService.getI18n().getString("check_settings", "Check settings"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_SETTINGS);
        sipService.showUiMessage(InAppNotifications.TYPE_WARN_LOGIN_FAIL, bundle);
    }

    static public void showNoNetwork(SipService sipService) {
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("no_network", "No Network"));
        bundle.putString("message2", sipService.getI18n().getString("check_connectivity", "Check connectivity"));
        sipService.showUiMessage(InAppNotifications.TYPE_WARN_NO_NETWORK, bundle);
    }

    static public void showBatterySaveModeEnabled(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_BATTERY_SAVER_ENABLED);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("uim_batterysave", "Missing calls?"));
        bundle.putString("message2", sipService.getI18n().getString("uim_batterysave_2", "Battery saver mode may block incoming calls when battery is low."));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_GO_POWER_SAVE_MODE);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        sipService.showUiMessage(InAppNotifications.TYPE_INFO_BATTERY_SAVER_ENABLED, bundle);
    }

    static public void showProAd(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_PROMO_1);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("promo_ad_title", "Premium Features"));
        bundle.putString("message2", sipService.getI18n().getString("promo_ad_comment", "Colors, advanced settings, more..."));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_GOPRO);
        bundle.putBoolean(InAppNotifications.FLAG_TOUCH_TS_PROMO, true);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        sipService.showUiMessage(InAppNotifications.TYPE_PROMO_1, bundle);
    }

    static public void showRateUsGooglePlay(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_RATEUS_1);
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_RATEUS_2);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("rate_google_play_title", "Rate VOSP on Google Play"));
        bundle.putString("message2", sipService.getI18n().getString("rate_google_play_comment", "Let the world know what you think."));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_GOPLAYAPP);
        bundle.putBoolean(InAppNotifications.FLAG_TOUCH_TS_RATEUS, true);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        sipService.showUiMessage(InAppNotifications.TYPE_RATEUS_2, bundle);
    }

    static public void showRateUsLocal(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_RATEUS_1);
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_RATEUS_2);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("rate_local_title", "Enjoying VOSP?"));
        bundle.putString("message2", sipService.getI18n().getString("rate_local_comment", "Let us know how we are doing."));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_GORATEUS_LOCAL);
        bundle.putBoolean(InAppNotifications.FLAG_TOUCH_TS_RATEUS, true);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        sipService.showUiMessage(InAppNotifications.TYPE_RATEUS_1, bundle);
    }

    static public void showSettingsProblem(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_SETTINGS);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("account_settings_invalid", "Account Settings Invalid"));
        bundle.putString("message2", sipService.getI18n().getString("touch_here_to_check_settings", "Touch here to check settings"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_SETTINGS);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        sipService.showUiMessage(InAppNotifications.TYPE_SETTINGS, bundle);
    }

    static public void showSetupRequired(SipService sipService) {
        sipService.uiMessageDismissByType(InAppNotifications.TYPE_SETTINGS);
        Bundle bundle = new Bundle();
        bundle.putString("message", sipService.getI18n().getString("sip_account_required", "SIP Account Required"));
        bundle.putString("message2", sipService.getI18n().getString("touch_here_to_add_sip_account", "Touch here to add SIP account"));
        bundle.putInt(InAppNotifications.KEY_ONCLICK_HINT, InAppNotifications.VALUE_ONCLICK_HINT_SETTINGS);
        bundle.putBoolean(InAppNotifications.FLAG_STICKY, true);
        bundle.putBoolean(InAppNotifications.FLAG_NO_DISMISS, true);
        sipService.showUiMessage(InAppNotifications.TYPE_SETTINGS, bundle);
    }


}
