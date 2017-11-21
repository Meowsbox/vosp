/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */
// IRemoteSipService.aidl
package com.meowsbox.vosp;

import com.meowsbox.vosp.IRemoteSipServiceEvents;

// Declare any non-default types here with import statements

interface IRemoteSipService {

    boolean callAnswer(int callId);
    boolean callDecline(int callId);
    boolean callHangup(int callId);
    boolean callHold(int callId);
    boolean callHoldResume(int callId);
    boolean callMute(int callId);
    boolean callMuteResume(int callId);
    boolean callRecord(int callId);
    boolean callRecordStop(int callId);
    int callOutDefault(String number);
    void setAudioRoute(int audioRoute);
    int getAudioRoute();
    int getServiceId();
    int getSipCallState(int callId);
    boolean callIsMute(int callId);
    boolean callIsRecord(int callId);
    boolean callIsOutgoing(int callId);
    boolean eventSubscribe(IRemoteSipServiceEvents clientEventCallback, int sipCallId);
    boolean eventUnSubscribe(IRemoteSipServiceEvents clientEventCallback, int sipCallId);
    boolean callPutDtmf(int callId, String digit);
    void audioRouteSpeakerToggle();
    boolean isBlueoothScoAvailable();
    String getSipCallExtension(int callId);
    String getSipCallRemoteDisplayName(int callId);
    String getLocalString(String key, String defaultValue);
    void stats_onActivityVisible();
    void ignoreIncoming();
    boolean hasActiveCalls();

    boolean rsCommit(boolean immediate);
    boolean delKey(String key);
    boolean rsIsKeyExist(String key);
    boolean rsGetBoolean(String key, boolean defaultValue);
    byte[] rsGetBytes(String key, in byte[] defaultValue);
    float rsGetFloat(String key, float defaultValue);
    int rsGetInt(String key, int defaultValue);
    long rsGetLong(String key, long defaultValue);
    String rsGetString(String key, String defaultValue);
    void rsSetBoolean(String key, boolean value);
    void rsSetBytes(String key, in byte[] bytes);
    void rsSetFloat(String key, float value);
    void rsSetInt(String key, int value);
    void rsSetLong(String key, long value);
    void rsSetString(String key, String value);

    String getAppExtStoragePath();

    String getDeviceId();

    Bundle getLicensingStateBundle();
    Bundle getSkuInfoBundle();
    int licensingBuyPrem();
    int licensingBuyPatron();
    void googleBillingOnResult(in Bundle data);

    void accountChangesComitted();

    void toast(String text, int timeLength);
    void toastLocal(String key, String defaultValue, int timeLength);

    void messageDismiss(int messageId);

    void startStack();
    void stopStack();
    void exit();


}
