/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.os.Message;

import com.meowsbox.vosp.DialerApplication;
import com.meowsbox.vosp.common.LocalStore;
import com.meowsbox.vosp.common.Logger;

import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AccountInfo;
import org.pjsip.pjsua2.AccountNatConfig;
import org.pjsip.pjsua2.AuthCredInfo;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.OnInstantMessageParam;
import org.pjsip.pjsua2.OnRegStartedParam;
import org.pjsip.pjsua2.OnRegStateParam;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsua_call_flag;
import org.pjsip.pjsua2.pjsua_stun_use;

import java.util.concurrent.ExecutionException;

/**
 * Created by dhon on 11/1/2016.
 */

public class SipAccount extends Account {
    public final static boolean DEBUG = SipService.DEBUG;
    public static final int WATCHDOG_DELAY = 30000;
    public static final int ACCOUNT_CREATE_OK = 0;
    public static final int ACCOUNT_CREATE_ERR_NOT_ENABLED = 1;
    public static final int ACCOUNT_CREATE_ERR_NOT_VALID = 2;
    static final char OPTION_DELIM = ';';
    static final String OPTION_TRANPORT_TCP = "transport=tcp";
    private final static int retryCountLimit = 3;
    private final static int WD_REG_TIMEOUT = 30000;
    public final String TAG = this.getClass().getName();
    private String ACCOUNT_USER_NAME;
    private String ACCOUNT_SIP_SERVER;
    private String ACCOUNT_SIP_SERVER_OUTBOUND_PROXY;
    private String ACCOUNT_SIP_SERVER_PORT;
    private String ACCOUNT_SIP_SERVER_OUTBOUND_PROXY_PORT;
    private String ACCOUNT_REG_EXPIRE;
    private String ACCOUNT_MEDIA_PORT_BEGIN;
    private String ACCOUNT_MEDIA_PORT_END;
    private String ACCOUNT_SECRET;
    private AccountConfig accountConfig = null;
    private AuthCredInfo authCredInfo = null;
    private Logger gLog;
    private SipService sipService = null;
    private boolean isEnabled = true;
    private String accountName = "default";
    private volatile boolean isWatchDogEnabled = false;
    private volatile int retryCount = 0;
    private int watchDogId = -1;
    //    public boolean init() {
//        debug_populateDefaultAccount();
//        if (accountConfig == null) createAccount();
//        try {
//            create(accountConfig);
//        } catch (Exception e) {
//            if (DEBUG) gLog.l(TAG ,Logger.lvDebug, e);
//            return false;
//        }
//        return true;
//    }
    private boolean useTcp = false;
    private boolean useStun = true;
    private boolean useIce = true;

    SipAccount(SipService sipService, AccountConfig accountConfig) {
        super();
        this.sipService = sipService;
        gLog = SipService.getInstance().getLoggerInstanceShared();
        if (accountConfig != null) this.accountConfig = accountConfig;
    }

    public String getSipUriFromExtension(String extension) {
        StringBuilder sb = new StringBuilder();
        sb.append("sip:");
        sb.append(extension);
        sb.append("@");
        sb.append(ACCOUNT_SIP_SERVER);
        sb.append(":");
        sb.append(ACCOUNT_SIP_SERVER_PORT);
        if (useTcp) {
            sb.append(OPTION_DELIM);
            sb.append(OPTION_TRANPORT_TCP);
        }
        return sb.toString();

    }

    public int initAccountFromPref(int accountPrefIndex) {
        LocalStore ls = sipService.getLocalStore();
        boolean accountEnabled = ls.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_ENABLED), false);
        if (!accountEnabled) {
            gLog.l(TAG, Logger.lvDebug, "ACCOUNT_CREATE_ERR_NOT_ENABLED");
            return ACCOUNT_CREATE_ERR_NOT_ENABLED;
        }
        accountName = ls.getString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_NAME), "");
        ACCOUNT_USER_NAME = ls.getString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USER), "");
        ACCOUNT_SECRET = ls.getString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SECRET), "");
        ACCOUNT_SIP_SERVER = ls.getString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SERVER), "");
        ACCOUNT_SIP_SERVER_PORT = String.valueOf(ls.getInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_PORT), 5060));
        ACCOUNT_SIP_SERVER_OUTBOUND_PROXY = ls.getString(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_SERVER_OUT_PROXY), "");
        ACCOUNT_SIP_SERVER_OUTBOUND_PROXY_PORT = String.valueOf(ls.getInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_PORT_OUT_PROXY), 5060));
        ACCOUNT_MEDIA_PORT_BEGIN = String.valueOf(ls.getInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_MEDIA_PORT_BEGIN), 5000)); // asterisk default 5000
        ACCOUNT_MEDIA_PORT_END = String.valueOf(ls.getInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_MEDIA_PORT_END), 31000)); // asterisk default 31000
        ACCOUNT_REG_EXPIRE = String.valueOf(ls.getInt(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_REG_EXPIRE), 300));
        useStun = ls.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USE_STUN), true);
        useIce = ls.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_USE_ICE), false);
        useTcp = ls.getBoolean(Prefs.getAccountKey(0, Prefs.KEY_ACCOUNT_SUF_TCP), false);
        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "Name " + accountName);
            gLog.l(TAG, Logger.lvVerbose, "User " + ACCOUNT_USER_NAME);
            if (DialerApplication.DEV) gLog.l(TAG, Logger.lvVerbose, "Secret " + ACCOUNT_SECRET);
            gLog.l(TAG, Logger.lvVerbose, "Server " + ACCOUNT_SIP_SERVER);
            gLog.l(TAG, Logger.lvVerbose, "Port " + ACCOUNT_SIP_SERVER_PORT);
            gLog.l(TAG, Logger.lvVerbose, "Server Outbound Proxy " + (ACCOUNT_SIP_SERVER_OUTBOUND_PROXY.isEmpty() ? "NOT USED" : ACCOUNT_SIP_SERVER_OUTBOUND_PROXY));
            gLog.l(TAG, Logger.lvVerbose, "Outbound Proxy Port " + ACCOUNT_SIP_SERVER_OUTBOUND_PROXY_PORT);
            gLog.l(TAG, Logger.lvVerbose, "Expire " + ACCOUNT_REG_EXPIRE);
            gLog.l(TAG, Logger.lvVerbose, "TCP " + useTcp);
        }

        if (accountConfig == null) createAccount();
        try {
            create(accountConfig);
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
            return ACCOUNT_CREATE_ERR_NOT_VALID;
        }
        return ACCOUNT_CREATE_OK;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean refreshIfRequired() {
        if (!isEnabled()) return false;
        AccountInfo info = null;
        try {
            info = getInfo();
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Refreshing registration..." + getId());
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "getRegExpiresSec " + info.getRegExpiresSec());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (info != null && info.getRegIsActive()) return false;
        queueRetryRegistration();
        return true;
    }

    /**
     * Override to block finalize.
     * BUGFIX: Java SipAccounts contain pointer references to their native PJSIP Account instances and are reused as needed. The JVM will lazily call finalize causing any reused native instance to be inadvertently deleted.
     */
    @Override
    protected void finalize() {
        //super.finalize();
    }

    @Override
    public synchronized void delete() {
        if (DEBUG) {
            SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "delete");
//            new Exception().printStackTrace();
        }
        super.delete();
    }

    @Override
    public void setRegistration(boolean renew) throws Exception {
        wdCancelSetReg(); // cancel existing wd
        // schedule new wd
        watchDogId = sipService.getScheduledRun().schedule(WD_REG_TIMEOUT, new Runnable() {
            final int accid = getId();

            @Override
            public void run() {
                try {
                    SipService.getInstance().runOnServiceThread(new Runnable() {
                        @Override
                        public void run() {
                            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "WD_REG_TIMEOUT queueRetryRegistration");
                            SipAccount sipAccountById = SipService.getInstance().getSipAccountById(accid);
                            UiMessagesCommon.showConnectProgress(sipService, 1);
                            sipAccountById.queueRetryRegistration();
                        }
                    });
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        super.setRegistration(renew);
    }

    @Override
    public void onIncomingCall(OnIncomingCallParam prm) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "======== Incoming call ======== ");
//        SipCall sipCall = new SipCall(this, prm.getCallId()); // instance self handled

        sipService.ringAndVibe(true); // initiate ring and vibe as early as possible, will be terminated by SipCall state events

        sipService.insertCall(prm.getCallId(), new SipCall(this, prm.getCallId())); // insert call into table
        // queue incoming call message
        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_CALL_INCOMING;
        message.arg2 = prm.getCallId();
        sipService.queueCommand(message);
//        sipService.onCallIncoming(sipCall);
    }

    @Override
    public void onRegStarted(OnRegStartedParam prm) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onRegStarted");
        super.onRegStarted(prm);
    }

    @Override
    public void onRegState(OnRegStateParam prm) {
        pjsip_status_code code = prm.getCode();
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, code.toString());
        if (code == pjsip_status_code.PJSIP_SC_OK) {
            retryCount = 0;
            wdCancelSetReg();
            sipService.getNotificationController().setForegroundMessage(null, accountName + " " + sipService.getI18n().getString("connected", "connected"), null);
            sipService.uiMessageDismissByType(InAppNotifications.TYPE_WARN_LOGIN_FAIL); // remove any previously shown messages about failed login
            sipService.uiMessageDismissByType(InAppNotifications.TYPE_WARN_NO_NETWORK); // remove any previously shown messages about no network connectivity
            sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_CONNECT_PROGRESS); // remove any previously shown messages about connection progress
            return;
        } else if (code == pjsip_status_code.PJSIP_SC_FORBIDDEN || code == pjsip_status_code.PJSIP_SC_UNAUTHORIZED) {
            retryCount = 0;
            wdCancelSetReg();
            sipService.getNotificationController().setForegroundMessage(null, accountName + " " + sipService.getI18n().getString("login_failed", "login failed"), null);
            sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_CONNECT_PROGRESS); // remove any previously shown messages about connection progress
            UiMessagesCommon.showLoginFailed(sipService);
            return;
        } else if (code == pjsip_status_code.PJSIP_SC_BAD_GATEWAY) {
            retryCount = 0;
            wdCancelSetReg();
            sipService.getNotificationController().setForegroundMessage(null, accountName + " " + sipService.getI18n().getString("settings_or_network_problem", "settings or network problem"), null);
            sipService.uiMessageDismissByType(InAppNotifications.TYPE_INFO_CONNECT_PROGRESS); // remove any previously shown messages about connection progress
            UiMessagesCommon.showSettingsProblem(sipService);
        } else {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "retrying registration...");
            if (retryCount > 0)
                sipService.getNotificationController().setForegroundMessage(null, accountName + " " + sipService.getI18n().getString("retrying", "retrying"), sipService.getI18n().getString("attempt", "attempt") + retryCount);
            else
                sipService.getNotificationController().setForegroundMessage(null, accountName + " " + sipService.getI18n().getString("retrying", "retrying"), null);
            queueRetryRegistration();
        }
        try {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "getRegIsActive " + getInfo().getRegIsActive());
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "isValid " + isValid());
        } catch (Exception e) {
            e.printStackTrace();
        }
        sipService.onAccountRegStateChanged(getId());
    }

    @Override
    public void onInstantMessage(OnInstantMessageParam prm) {
        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "======== Incoming pager ======== ");
            gLog.l(TAG, Logger.lvVerbose, "From     : " + prm.getFromUri());
            gLog.l(TAG, Logger.lvVerbose, "To       : " + prm.getToUri());
            gLog.l(TAG, Logger.lvVerbose, "Contact  : " + prm.getContactUri());
            gLog.l(TAG, Logger.lvVerbose, "Mimetype : " + prm.getContentType());
            gLog.l(TAG, Logger.lvVerbose, "Body     : " + prm.getMsgBody());
        }

    }

    void resetRetryCount() {
        retryCount = 0;
    }

    private void createAccount() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "createAccount");
        accountConfig = new AccountConfig();
        accountConfig.setIdUri(getIdUri());
        accountConfig.getRegConfig().setRegistrarUri(getRegisterUri());
        accountConfig.getRegConfig().setTimeoutSec(Long.parseLong(ACCOUNT_REG_EXPIRE));
        accountConfig.getRegConfig().setRetryIntervalSec(0);
        accountConfig.getRegConfig().setRandomRetryIntervalSec(0);
        accountConfig.getRegConfig().setRegisterOnAdd(false);

        if (!ACCOUNT_SIP_SERVER_OUTBOUND_PROXY.isEmpty()) {
            final StringVector proxies = accountConfig.getSipConfig().getProxies();
            proxies.clear();
            proxies.add(getOutboundProxyUri());
        }

        accountConfig.getIpChangeConfig().setReinviteFlags(pjsua_call_flag.PJSUA_CALL_UPDATE_CONTACT.swigValue());
        accountConfig.getIpChangeConfig().setShutdownTp(false);

        accountConfig.getMediaConfig().getTransportConfig().setPort(Long.parseLong(ACCOUNT_MEDIA_PORT_BEGIN));
        accountConfig.getMediaConfig().getTransportConfig().setPortRange(Long.parseLong(ACCOUNT_MEDIA_PORT_END));

        AccountNatConfig natConfig = accountConfig.getNatConfig();
        natConfig.setUdpKaIntervalSec(sipService.getSipEndpoint().getKeepAliveUdpCurrent());

        if (!useStun) natConfig.setSipStunUse(pjsua_stun_use.PJSUA_STUN_USE_DISABLED);
        if (useIce) natConfig.setIceEnabled(true);

        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "ICE " + natConfig.getIceEnabled());
            gLog.l(TAG, Logger.lvVerbose, "TURN " + natConfig.getTurnEnabled());
            gLog.l(TAG, Logger.lvVerbose, "STUN " + natConfig.getSipStunUse().toString());
            gLog.l(TAG, Logger.lvVerbose, "IPc Hangup " + accountConfig.getIpChangeConfig().getHangupCalls());
            gLog.l(TAG, Logger.lvVerbose, "IPc ShutdownTp " + accountConfig.getIpChangeConfig().getShutdownTp());
            gLog.l(TAG, Logger.lvVerbose, "IPc ReinviteFlags " + accountConfig.getIpChangeConfig().getReinviteFlags());
        }

        authCredInfo = new AuthCredInfo("digest", "*", ACCOUNT_USER_NAME, 0, ACCOUNT_SECRET);
        accountConfig.getSipConfig().getAuthCreds().add(authCredInfo);
    }

    private String getIdUri() {
        StringBuilder sb = new StringBuilder();
        sb.append("sip:");
        sb.append(ACCOUNT_USER_NAME);
        sb.append("@");
        sb.append(ACCOUNT_SIP_SERVER);
        return sb.toString();
    }

    private String getOutboundProxyUri() {
        StringBuilder sb = new StringBuilder();
        sb.append("sip:");
        sb.append(ACCOUNT_SIP_SERVER_OUTBOUND_PROXY);
        sb.append(":");
        sb.append(ACCOUNT_SIP_SERVER_OUTBOUND_PROXY_PORT);
        if (useTcp) {
            sb.append(OPTION_DELIM);
            sb.append(OPTION_TRANPORT_TCP);
        }
        return sb.toString();
    }

    /**
     * Generate URI for sip registration. Will vary depending on endpoint transport
     *
     * @return
     */
    private String getRegisterUri() {
        StringBuilder sb = new StringBuilder();
        sb.append("sip:");
        sb.append(ACCOUNT_SIP_SERVER);
        sb.append(":");
        sb.append(ACCOUNT_SIP_SERVER_PORT);
        if (useTcp) {
            sb.append(OPTION_DELIM);
            sb.append(OPTION_TRANPORT_TCP);
        }
        return sb.toString();
    }

    private void queueRetryRegistration() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "queueRetryRegistration");
        if (!sipService.isNetworkAvailable()) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Abort - No Network Connectivity");
            retryCount = 0; // reset counter
            return;
        }
        if (!sipService.isStackReady()) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Abort - Stack not Ready");
            retryCount = 0; // reset counter
            return;
        }

        UiMessagesCommon.showConnectProgress(sipService, 2);
        retryCount++;
        if (retryCount <= retryCountLimit) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "retryCount " + retryCount);
            Message message = new Message();
            message.arg1 = SipServiceMessages.MSG_ACCOUNTS_REGISTER_SINGLE;
            message.arg2 = getId();
            sipService.queueCommand(message, 1000);
        } else {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "retryCount exceeded limit");
            UiMessagesCommon.showConnectProgress(sipService, 3);
            Message message = new Message();
            message.arg1 = SipServiceMessages.MSG_EP_TRANSPORT_FLUSH;
            message.arg2 = getId();
            sipService.queueCommand(message);

            message = new Message();
            message.arg1 = SipServiceMessages.MSG_ACCOUNTS_REGISTER_SINGLE;
            message.arg2 = getId();
            sipService.queueCommand(message, 1000);
        }

    }

    /**
     * Watchdog: cancels the future callback on registration status.
     */
    private void wdCancelSetReg() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "wdCancelSetReg");
        if (watchDogId >= 0) sipService.getScheduledRun().cancel(watchDogId);
        watchDogId = -1;
    }
}
