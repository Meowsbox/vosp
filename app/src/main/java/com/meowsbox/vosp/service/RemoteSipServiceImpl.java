/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.os.Bundle;
import android.os.RemoteException;

import com.meowsbox.vosp.IRemoteSipService;
import com.meowsbox.vosp.IRemoteSipServiceEvents;
import com.meowsbox.vosp.common.Logger;

/**
 * Created by dhon on 1/26/2017.
 */

public class RemoteSipServiceImpl extends IRemoteSipService.Stub {
    public final static boolean DEBUG = SipService.DEBUG;
    public final static boolean DEV = SipService.DEV;
    public final String TAG = this.getClass().getName();

    private Logger gLog;

    public RemoteSipServiceImpl() {
        gLog = SipService.getInstance().getLoggerInstanceShared();
    }


    @Override
    public boolean callAnswer(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callAnswer(callId);
    }

    @Override
    public boolean callDecline(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callDecline(callId);
    }

    @Override
    public boolean callHangup(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callHangup(callId);
    }

    @Override
    public boolean callHold(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callHold(callId);
    }

    @Override
    public boolean callHoldResume(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callHoldResume(callId);
    }

    @Override
    public boolean callMute(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callMute(callId);
    }

    @Override
    public boolean callMuteResume(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callMuteResume(callId);
    }

    @Override
    public boolean callRecord(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callRecord(callId);
    }

    @Override
    public boolean callRecordStop(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().callRecordStop(callId);
    }

    @Override
    public int callOutDefault(String number) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        SipService sipService = SipService.getInstance();
        if (sipService == null) return -1;
        int sipCallId = sipService.callOutDefault(number);
        return sipCallId;
    }

    @Override
    public void setAudioRoute(int audioRoute) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        SipService.getInstance().setAudioRoute(audioRoute);
    }

    @Override
    public int getAudioRoute() throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().getAudioRoute();
    }

    @Override
    public int getServiceId() throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().getServiceId();
    }

    @Override
    public int getSipCallState(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().getSipCallState(callId);
    }

    @Override
    public boolean callIsMute(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().getSipCallMuteState(callId);
    }

    @Override
    public boolean callIsRecord(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().getSipCallRecordState(callId);
    }

    @Override
    public boolean callIsOutgoing(int callId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().getSipCallIsOutgoing(callId);
    }

    @Override
    public boolean eventSubscribe(IRemoteSipServiceEvents clientEventCallback, int sipCallId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        SipService.getInstance().eventSubscribeRemote(clientEventCallback, sipCallId);
        return true;
    }

    @Override
    public boolean eventUnSubscribe(IRemoteSipServiceEvents clientEventCallback, int sipCallId) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        SipService.getInstance().eventsUnsubscribeRemote(clientEventCallback, sipCallId);
        return true;
    }

    @Override
    public boolean callPutDtmf(int callId, String digit) throws RemoteException {
        if (DEV) gLog.l(Logger.lvVerbose);
        return SipService.getInstance().putSipCallDtmf(callId, digit);
    }

    @Override
    public void audioRouteSpeakerToggle() throws RemoteException {
        SipService.getInstance().audioRouteSpeakerToggle();
    }

    @Override
    public boolean isBlueoothScoAvailable() throws RemoteException {
        return SipService.getInstance().isBlueoothScoAvailable();
    }

    @Override
    public String getSipCallExtension(int callId) throws RemoteException {
        return SipService.getInstance().getSipCallExtension(callId);
    }

    @Override
    public String getSipCallRemoteDisplayName(int callId) throws RemoteException {
        return SipService.getInstance().getSipCallRemoteDisplayName(callId);
    }

    @Override
    public String getLocalString(String key, String defaultValue) throws RemoteException {
        return SipService.getInstance().getI18n().getString(key, defaultValue);
    }

    @Override
    public void stats_onActivityVisible() throws RemoteException {
        SipService.getInstance().getStatsProvider().onActivityVisible();

    }

    @Override
    public void ignoreIncoming() throws RemoteException {
        SipService.getInstance().ringAndVibe(false);
    }

    @Override
    public boolean hasActiveCalls() throws RemoteException {
        return SipService.getInstance().hasActiveCalls();
    }

    @Override
    public boolean rsCommit(boolean immediate) throws RemoteException {
        return SipService.getInstance().getLocalStore().commit(immediate);
    }

    @Override
    public boolean delKey(String key) throws RemoteException {
        return SipService.getInstance().getLocalStore().delKey(key);
    }

    @Override
    public boolean rsIsKeyExist(String key) throws RemoteException {
        return SipService.getInstance().getLocalStore().isKeyExist(key);
    }

    @Override
    public boolean rsGetBoolean(String key, boolean defaultValue) throws RemoteException {
        return SipService.getInstance().getLocalStore().getBoolean(key, defaultValue);
    }

    @Override
    public byte[] rsGetBytes(String key, byte[] defaultValue) throws RemoteException {
        return SipService.getInstance().getLocalStore().getBytes(key, defaultValue);
    }

    @Override
    public float rsGetFloat(String key, float defaultValue) throws RemoteException {
        return SipService.getInstance().getLocalStore().getFloat(key, defaultValue);
    }

    @Override
    public int rsGetInt(String key, int defaultValue) throws RemoteException {
        return SipService.getInstance().getLocalStore().getInt(key, defaultValue);
    }

    @Override
    public long rsGetLong(String key, long defaultValue) throws RemoteException {
        return SipService.getInstance().getLocalStore().getLong(key, defaultValue);
    }

    @Override
    public String rsGetString(String key, String defaultValue) throws RemoteException {
        return SipService.getInstance().getLocalStore().getString(key, defaultValue);
    }

    @Override
    public void rsSetBoolean(String key, boolean value) throws RemoteException {
        SipService.getInstance().getLocalStore().setBoolean(key, value);
    }

    @Override
    public void rsSetBytes(String key, byte[] bytes) throws RemoteException {
        SipService.getInstance().getLocalStore().setBytes(key, bytes);
    }

    @Override
    public void rsSetFloat(String key, float value) throws RemoteException {
        SipService.getInstance().getLocalStore().setFloat(key, value);
    }

    @Override
    public void rsSetInt(String key, int value) throws RemoteException {
        SipService.getInstance().getLocalStore().setInt(key, value);
    }

    @Override
    public void rsSetLong(String key, long value) throws RemoteException {
        SipService.getInstance().getLocalStore().setLong(key, value);
    }

    @Override
    public void rsSetString(String key, String value) throws RemoteException {
        SipService.getInstance().getLocalStore().setString(key, value);
    }

    @Override
    public String getAppExtStoragePath() throws RemoteException {
        return SipService.getInstance().getAppExtStoragePath();
    }

    @Override
    public String getDeviceId() throws RemoteException {
        return SipService.getInstance().getDeviceId();
    }

    @Override
    public Bundle getLicensingStateBundle() throws RemoteException {
        return SipService.getInstance().getLicensingStateBundle();
    }

    @Override
    public Bundle getSkuInfoBundle() throws RemoteException {
        return SipService.getInstance().getSkuInfoBundle();
    }

    @Override
    public int licensingBuyPrem() throws RemoteException {
        return SipService.getInstance().getLicensing().buyPrem();
    }

    @Override
    public int licensingBuyPatron() throws RemoteException {
        return SipService.getInstance().getLicensing().buyPatron();
    }

    @Override
    public void googleBillingOnResult(Bundle data) throws RemoteException {
        SipService.getInstance().getLicensing().googleBillingOnResult(data);
    }

    @Override
    public void accountChangesComitted() throws RemoteException {
        SipService.getInstance().accountChangesCommit();
    }

    @Override
    public void toast(String text, int timeLength) throws RemoteException {
        SipService.getInstance().toast(text, timeLength);
    }

    @Override
    public void toastLocal(String key, String defaultValue, int timeLength) throws RemoteException {
        SipService.getInstance().toastLocal(key, defaultValue, timeLength);
    }

    @Override
    public void messageDismiss(int serviceMessageId) throws RemoteException {
        SipService.getInstance().uiMessageDismiss(serviceMessageId);
    }

    @Override
    public void startStack() throws RemoteException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void stopStack() throws RemoteException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void exit() throws RemoteException {
        SipService.getInstance().queueExit();
    }

}
