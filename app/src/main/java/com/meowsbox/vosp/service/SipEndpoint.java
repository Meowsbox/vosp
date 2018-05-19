/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Message;
import android.util.Log;

import com.meowsbox.internal.siptest.PjSipTimerWrapper;
import com.meowsbox.vosp.common.LocalStore;
import com.meowsbox.vosp.common.Logger;

import org.pjsip.pjsua2.AudDevManager;
import org.pjsip.pjsua2.CodecInfo;
import org.pjsip.pjsua2.CodecInfoVector;
import org.pjsip.pjsua2.Endpoint;
import org.pjsip.pjsua2.EpConfig;
import org.pjsip.pjsua2.IntVector;
import org.pjsip.pjsua2.IpChangeParam;
import org.pjsip.pjsua2.LogEntry;
import org.pjsip.pjsua2.LogWriter;
import org.pjsip.pjsua2.OnIpChangeProgressParam;
import org.pjsip.pjsua2.OnNatCheckStunServersCompleteParam;
import org.pjsip.pjsua2.OnNatDetectionCompleteParam;
import org.pjsip.pjsua2.OnSelectAccountParam;
import org.pjsip.pjsua2.OnTransportStateParam;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.pjsip_transport_type_e;

/**
 * Created by dhon on 11/1/2016.
 */

public class SipEndpoint {
    public static final boolean DEBUG = SipService.DEBUG;
    public static final int NETWORK_TYPE_MOBILE = 1;
    public static final int NETWORK_TYPE_WIFI = 2;
    public static final int NATIVE_BLOCKING_POLL_INTERVAL = 60000;
    public static final int MEDIA_QUALITY_DEFAULT = 2;
    private static final int PJLIB_LOGGING_LEVEL = 3;
    private static final int PJLIB_LOGGING_LEVEL_DEBUG = 3; // 1-6
    private static final int STACK_RESTART_ON_CREATE_TRANSPORT_FAILURE_MAX = 2;
    public static int KEEP_ALIVE_TCP_MOBILE = 120;
    public static int KEEP_ALIVE_TCP_WIFI = 120;
    public static int KEEP_ALIVE_UDP_MOBILE = 90;
    public static int KEEP_ALIVE_UDP_WIFI = 120;
    public static int KEEP_ALIVE_TCP_CURRENT = KEEP_ALIVE_TCP_WIFI;
    public static int KEEP_ALIVE_UDP_CURRENT = KEEP_ALIVE_UDP_WIFI;
    public static int MEDIA_QUALITY = MEDIA_QUALITY_DEFAULT;
    public final String TAG = this.getClass().getName();
    TransportConfig transportConfig;
    boolean isTransportTcpValid = false;
    boolean isTransportUdpValid = false;
    private Logger gLog;
    private EndPointExtended ep;
    private SipLogWriter sipLogWriter;
    private int sampleRate = 16000; // default sample rate
    private boolean isTransportTCP = true;
    private int tidTcp;
    private int tidUdp;
    private boolean isAlive = false;
    volatile private boolean wdTransport = false; // enable to trigger reregister on any transport shutdown

    public SipEndpoint() {
        gLog = SipService.getInstance().getLoggerInstanceShared();
    }

    public static int getKeepAliveTcpCurrent() {
        return KEEP_ALIVE_TCP_CURRENT;
    }

    public static int getKeepAliveUdpCurrent() {
        return KEEP_ALIVE_UDP_CURRENT;
    }

    public void flushTransport() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "flushTransport");
//        try {
//            ep.hangupAllCalls();
//            if (isTransportTcpValid) {
////                ep.transportGetInfo(tidTcp).setUsageCount(0);
//                ep.transportClose(tidTcp);
//            }
//            if (isTransportUdpValid) {
////                ep.transportGetInfo(tidUdp).setUsageCount(0);
//                ep.transportClose(tidUdp);
//            }
//            if (!createTransport()) {
//                gLog.l(TAG ,Logger.lvError, "FATAL, restart - failed to create transport");
//                Message message = new Message();
//                message.arg1 = SipServiceMessages.MSG_STACK_RESTART;
//                SipService.getInstance().queueCommand(message);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        Message message = new Message();
        message.arg1 = SipServiceMessages.MSG_STACK_RESTART;
        SipService.getInstance().queueCommand(message);
    }

    public AudDevManager getAudDevManager() {
        return ep.audDevManager();
    }

    public void init() {
        SipService.getInstance().getNotificationController().setForegroundMessage(null, SipService.getInstance().getI18n().getString("starting", "starting"), null);
        wdTransport = true;
        try {
            boolean useDozeWa = SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_DOZE_WORKAROUND_ENABLE, false);
            gLog.l(TAG, Logger.lvDebug, "useDozeWa Supported " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M));
            gLog.l(TAG, Logger.lvDebug, "useDozeWa Enabled " + useDozeWa);
            if (useDozeWa)
                PjSipTimerWrapper.getInstance().enableSwitchOnScreenState(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M); // specifically enable screen state switching on supported platforms
            else
                PjSipTimerWrapper.getInstance().enableSwitchOnScreenState(false);
            if (ep == null) ep = new EndPointExtended();
            ep.libCreate();
            // Initialize endpoint
            EpConfig epConfig = new EpConfig();

            epConfig.getLogConfig().setLevel(DEBUG ? PJLIB_LOGGING_LEVEL_DEBUG : PJLIB_LOGGING_LEVEL);
            epConfig.getLogConfig().setWriter(sipLogWriter = new SipLogWriter()); // SipLogWriter lifecycle is tied to endpoint

            epConfig.getUaConfig().setMaxCalls(4);

            String stunServer = SipService.getInstance().getLocalStore().getString(Prefs.KEY_STUN_SERVER, "");
            if (!stunServer.isEmpty()) {
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Stun Server: " + stunServer);
                StringVector ss = new StringVector();
                ss.add("stun.meowsbox.com");
                epConfig.getUaConfig().setStunServer(ss);
                ss.delete();
            }

            epConfig.getMedConfig().setSndClockRate(sampleRate);
            MEDIA_QUALITY = SipService.getInstance().getLocalStore().getInt(Prefs.KEY_MEDIA_QUALITY, MEDIA_QUALITY_DEFAULT);
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "Media Quality: " + MEDIA_QUALITY);
            epConfig.getMedConfig().setQuality(MEDIA_QUALITY);
            epConfig.getMedConfig().setNoVad(true);
            epConfig.getMedConfig().setEcOptions(0);

            ep.libInit(epConfig);
            epConfig.delete();

            // Create SIP transport. Error handling sample is shown
            final LocalStore localStore = SipService.getInstance().getLocalStore();
            int rc = localStore.getInt(Prefs.KEY_STACK_RESTART_ON_CREATE_TRANSPORT_FAILURE_COUNT, 0);
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Create transport failure count: " + rc);
            if (!createTransport()) {
                gLog.l(TAG, Logger.lvDebug, "FATAL, restart - failed to create transport");
                if (rc > STACK_RESTART_ON_CREATE_TRANSPORT_FAILURE_MAX) {
                    gLog.l(TAG, Logger.lvDebug, "Could not create transport - too many failures!");
                } else {
                    localStore.setInt(Prefs.KEY_STACK_RESTART_ON_CREATE_TRANSPORT_FAILURE_COUNT, ++rc);
                    Message message = new Message();
                    message.arg1 = SipServiceMessages.MSG_STACK_RESTART;
                    SipService.getInstance().queueCommand(message, 1000);
                }
            } else {
                localStore.setInt(Prefs.KEY_STACK_RESTART_ON_CREATE_TRANSPORT_FAILURE_COUNT, 0);
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Create transport failure count RESET");
            }

            // Start the library
            ep.libStart();
            setCodecPriorities();

            if (DEBUG) debugCodecPrint();

            isAlive = true;
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
        }
    }

    public boolean isAlive() {
        return isAlive;
    }

    public boolean isTransportTCP() {
        return isTransportTCP;
    }

    public void networkTypeChange(int type) {
        updateKeepAliveIntervals(type);
        try {
            ep.tcpKaInterval(KEEP_ALIVE_TCP_CURRENT);
        } catch (Exception e) {
            if (DEBUG) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
            }
        }
    }

    public void regenTransport() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "regenTransport");

        try {
            final IpChangeParam ipChangeParam = new IpChangeParam();
            ipChangeParam.setRestartListener(true);
            ipChangeParam.setRestartLisDelay(5000);
            ep.handleIpChange(ipChangeParam);
            ipChangeParam.delete();
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
    }

    public void setSampleRate(int sampleRate) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, sampleRate);
        this.sampleRate = sampleRate;
    }

    /**
     * Call to unload PJSIP library and cleanup the endpoint. Init can be called afterwards to recreate the library instance and endpoint.
     */
    void destroy() {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "destroy");
        isAlive = false;
        wdTransport = false;
        try {
            ep.hangupAllCalls();
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
        try {
            final IntVector intVector = ep.transportEnum();
            for (int i = 0; i < intVector.size(); i++) {
                ep.transportClose(intVector.get(i));
            }
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);

        }
        // per pjsip docs, must call libDestroy BEFORE delete
        try {
            ep.libDestroy();
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
        try {
            ep.delete();
        } catch (Exception e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
        ep = null;
    }

    /**
     * Override to block finalize.
     */
    @Override
    protected void finalize() throws Throwable {
    }

    boolean isPjsipThread() {
        boolean b = ep.libIsThreadRegistered();
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, b);
        return b;
    }

    private boolean createTransport() {
        boolean result = true;
        try {
            Integer networkType = SipService.getInstance().getNetworkType();
            if (networkType != null) updateKeepAliveIntervals(networkType);
            ep.tcpKaInterval(KEEP_ALIVE_TCP_CURRENT); // note, equiv UDP KA is setting is in the Account NatConfig

            try {
                // create TCP transport
                isTransportTcpValid = false;
                transportConfig = new TransportConfig();
                tidTcp = ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, transportConfig);// TCP transport requires the sip UI includes transport=TCP
                transportConfig.delete();
                isTransportTcpValid = true;
                isTransportTCP = true;
            } catch (Exception e) {
                result = false;
                gLog.l(TAG, Logger.lvVerbose, e);
            }

            try {
                // create UDP transport
                isTransportUdpValid = false;
                transportConfig = new TransportConfig();
                tidUdp = ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, transportConfig);
                transportConfig.delete();
                isTransportUdpValid = true;
            } catch (Exception e) {
                result = false;
                gLog.l(TAG, Logger.lvVerbose, e);
            }
        } catch (Exception e) {
            result = false;
            gLog.l(TAG, Logger.lvVerbose, e);
        }
        return result;
    }

    private void debugCodecPrint() {
        try {
            CodecInfoVector codecInfoVector = ep.codecEnum();
            for (int i = 0; i < codecInfoVector.size(); i++) {
                CodecInfo codecInfo = codecInfoVector.get(i);
                gLog.l(TAG, Logger.lvVerbose, codecInfo.getCodecId() + " " + (codecInfo.getPriority() > 0 ? codecInfo.getPriority() : "disabled"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setCodecPriorities() {
        try {
            CodecInfoVector codecInfoVector = ep.codecEnum();
            for (int i = 0; i < codecInfoVector.size(); i++) {
                CodecInfo codecInfo = codecInfoVector.get(i);
                if (codecInfo.getCodecId().contains("G722")) // G722 disable
                    ep.codecSetPriority(codecInfo.getCodecId(), (short) 0);
                if (codecInfo.getCodecId().contains("GSM")) // GSM low
                    ep.codecSetPriority(codecInfo.getCodecId(), (short) 1);
                if (codecInfo.getCodecId().contains("SILK")) // SILK above normal
                    ep.codecSetPriority(codecInfo.getCodecId(), (short) 192);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateKeepAliveIntervals(int networkType) {
        switch (networkType) {
            case ConnectivityManager.TYPE_WIFI:
                KEEP_ALIVE_TCP_CURRENT = SipService.getInstance().getLocalStore().getInt(Prefs.KEY_KA_TCP_WIFI, KEEP_ALIVE_TCP_WIFI);
                KEEP_ALIVE_UDP_CURRENT = SipService.getInstance().getLocalStore().getInt(Prefs.KEY_KA_TCP_WIFI, KEEP_ALIVE_TCP_WIFI);
                break;
            case ConnectivityManager.TYPE_MOBILE:
            default:
                KEEP_ALIVE_TCP_CURRENT = SipService.getInstance().getLocalStore().getInt(Prefs.KEY_KA_TCP_MOBILE, KEEP_ALIVE_TCP_MOBILE);
                KEEP_ALIVE_UDP_CURRENT = SipService.getInstance().getLocalStore().getInt(Prefs.KEY_KA_TCP_MOBILE, KEEP_ALIVE_TCP_MOBILE);
                break;
        }

        if (DEBUG) {
            gLog.l(TAG, Logger.lvVerbose, "KEEP_ALIVE_TCP_CURRENT " + KEEP_ALIVE_TCP_CURRENT);
            gLog.l(TAG, Logger.lvVerbose, "KEEP_ALIVE_UDP_CURRENT " + KEEP_ALIVE_UDP_CURRENT);
        }
    }

    class SipLogWriter extends LogWriter {
        StringBuilder sb = new StringBuilder();

        @Override
        public void write(LogEntry entry) {
            sb.setLength(0);
            sb.append(entry.getLevel());
            sb.append("L ");
            sb.append(entry.getMsg());
            sb.append("\r\n"); //need to append CRLF
            if (gLog != null) gLog.l(TAG, Logger.lvVerbose, sb.toString());
            else Log.i("pjsip", sb.toString());
            entry.delete();
        }
    }

    class EndPointExtended extends Endpoint {
        @Override
        public void onTransportState(OnTransportStateParam prm) {
            if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "onTransportState " + prm.getState().toString());
            super.onTransportState(prm);
        }

        @Override
        public void onIpChangeProgress(OnIpChangeProgressParam prm) {
            if (DEBUG) {
                gLog.l(TAG, Logger.lvVerbose, "onIpChangeProgress op" + prm.getOp().toString());
                gLog.l(TAG, Logger.lvVerbose, "onIpChangeProgress getRegInfo" + prm.getRegInfo().toString());
            }
        }
    }

}
