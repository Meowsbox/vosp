/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.utility.UriSip;

import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.AudDevManager;
import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.AudioMediaRecorder;
import org.pjsip.pjsua2.Call;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfo;
import org.pjsip.pjsua2.CallMediaInfoVector;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.CallSetting;
import org.pjsip.pjsua2.Media;
import org.pjsip.pjsua2.OnCallMediaStateParam;
import org.pjsip.pjsua2.OnCallStateParam;
import org.pjsip.pjsua2.pjmedia_type;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsua_call_flag;
import org.pjsip.pjsua2.pjsua_call_media_status;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.meowsbox.vosp.DialerApplication.DEV;

/**
 * Wrapper for PJSIP Call. Abstracts access to native Call methods, permits retained reference after native delete.
 * Note SipCall states differ from PJSIP Call states.
 */
public class SipCall {
    public final static boolean DEBUG = SipService.DEBUG;
    public static final int SIPSTATE_NOTFOUND = -1; // ERROR: sip call not found, ie deleted or not yet present
    public static final int SIPSTATE_IDLE = 0; // initial state after construction
    public static final int SIPSTATE_PROGRESS_IN = 1; // inbound progress
    public static final int SIPSTATE_PROGRESS_OUT = 2; // outbound progress
    public static final int SIPSTATE_ACCEPTED = 3; // call accepted
    public static final int SIPSTATE_HOLD = 4; // call accepted and on hold
    public static final int SIPSTATE_DISCONNECTED = 5; // remote disconnect and local hungup, audio dependencies closed
    private static final int callOutLimit = 3;
    public final String TAG = this.getClass().getName();
    private Logger gLog;
    private RingbackGenerator ringbackGenerator;
    private PjsipCall pjsipCall; // reference to native PJSIP Call class
    private int sipStateCurrent = 0; // current call state
    private boolean isOutgoing = false; // is an outgoing call
    private long statCallStartTime = 0;
    private long statCallStopTime = 0;
    private boolean isMute = false;
    private boolean isAccepted = false;
    private Account account;
    private int callOutAttempt = 0;
    private volatile boolean isWakeLocked = false;
    private boolean wasDeclined = false;
    private volatile boolean isRecording = false;
    private boolean autoRecord = true;

    public SipCall(Account account, int pjsipCallId) {
        gLog = SipService.getInstance().getLoggerInstanceShared();
        pjsipCall = new PjsipCall(account, pjsipCallId);
        this.account = account;
        updateRecordAuto();
    }

    public SipCall(Account account) {
        gLog = SipService.getInstance().getLoggerInstanceShared();
        pjsipCall = new PjsipCall(account);
        this.account = account;
        updateRecordAuto();
    }

    public int getId() {
        if (pjsipCall == null) return -1;
        return pjsipCall.getId();
    }

    public long getStatCallStartTime() {
        return statCallStartTime;
    }

    public long getStatCallStopTime() {
        return statCallStopTime;
    }

    public boolean isAccepted() {
        return isAccepted;
    }

    public boolean isAutoRecord() {
        return autoRecord;
    }

    public void setAutoRecord(boolean autoRecord) {
        this.autoRecord = autoRecord;
    }

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        isMute = mute;
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setRecording(boolean recording) {
        isRecording = recording;
    }

    public void setWasDeclined(boolean wasDeclined) {
        this.wasDeclined = wasDeclined;
    }

    public boolean wasDeclined() {
        return wasDeclined;
    }

    /**
     * Native PJSIP Call Delete. This instance is safe to dereference after calling this method.
     */
    void delete() {
        if (ringbackGenerator.isPlaying()) ringbackGenerator.stop();
        if (sipStateCurrent != SIPSTATE_DISCONNECTED)
            putHangup(); // ensure call dependencies are properly closed (ie audio, etc)
        wakeLockControl(false, "pjsipCallId " + pjsipCall.getId());
        pjsipCall.delete();
        pjsipCall = null;
    }

    String getExtension() {
        if (pjsipCall == null) return null;
        try {
            CallInfo info = pjsipCall.getInfo();
            String remoteUri = info.getRemoteUri();
            if (remoteUri == null) return null;
            return UriSip.getUser(UriSip.stripContactUri(remoteUri));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    CallInfo getInfo() throws Exception {
        return pjsipCall.getInfo();
    }

    String getRemoteDisplayName() {
        if (pjsipCall == null) return null;
        try {
            CallInfo info = pjsipCall.getInfo();
            return UriSip.getContactDisplayName(info.getRemoteUri());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Returns the current abstract SIP state of this call. This is the NOT the PJSIP call state, but closely related.
     *
     * @return
     */
    int getSipState() {
        return sipStateCurrent;
    }

    void setSipState(int state) {
        sipStateCurrent = state;
    }

    /**
     * Accept INVITE with SIP 200 and begin call...
     * Immediately transitions to SIPSTATE_ACCEPTED
     *
     * @return
     */
    boolean putAnswer() {
        if (pjsipCall == null) return false;
        if (ringbackGenerator != null) ringbackGenerator.stop(); // stop ringbackGenerator early
        sipStateCurrent = SIPSTATE_ACCEPTED;
        isAccepted = true;
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        try {
            pjsipCall.answer(prm);
            statCallStartTime = System.currentTimeMillis();
            prm.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Send INTVITE to begin call
     * Immediately transitions to STATE_PROGRESS_OUT
     *
     * @param uri
     * @return
     */
    int putCall(String uri) {
        if (pjsipCall == null) return -1;
        isOutgoing = true; // set outgoing flag aka UAC role

        try { // check if account is active
            if (!account.getInfo().getRegIsActive()) {
                account.setRegistration(true);
                if (callOutAttempt < callOutLimit) {
                    callOutAttempt++;
                    return putCall(uri);
                } else return -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }

        sipStateCurrent = SIPSTATE_PROGRESS_OUT;
        CallOpParam prm = new CallOpParam();
        try {
            pjsipCall.makeCall(uri, prm);
            statCallStartTime = System.currentTimeMillis();
            prm.delete();
            return pjsipCall.getId();
        } catch (Exception e) {
            isOutgoing = false;
            sipStateCurrent = SIPSTATE_DISCONNECTED;
            SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvDebug, e);
            return -1;
        }
    }

    /**
     * Reply INVITE with SIP 603 and decline call
     * Immediately transitions to SIPSTATE_DISCONNECTED
     *
     * @return
     */
    boolean putDecline() {
        if (pjsipCall == null) return false;
        sipStateCurrent = SIPSTATE_DISCONNECTED;
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_DECLINE);
        try {
            stopMediaImmediate(pjsipCall);
            pjsipCall.hangup(prm);
            statCallStopTime = System.currentTimeMillis();
            prm.delete();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Send BYE to end call
     * Immediately transitions to SIPSTATE_DISCONNECTED
     *
     * @return
     */
    boolean putHangup() {
        if (pjsipCall == null) return false;
        sipStateCurrent = SIPSTATE_DISCONNECTED;
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        try {
            if (ringbackGenerator != null && ringbackGenerator.isPlaying()) ringbackGenerator.stop();
            stopMediaImmediate(pjsipCall);
            pjsipCall.hangup(prm);
            statCallStopTime = System.currentTimeMillis();
            prm.delete();
            wakeLockControl(false, "pjsipCallId " + pjsipCall.getId());
        } catch (Exception e) {
            SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvDebug, e);
            return false;
        }
        return true;
    }

    /**
     * Put accepted call on hold.
     * Immediately transitions to SIPSTATE_HOLD
     *
     * @return
     */
    boolean putHold() {
        if (pjsipCall == null) return false;
        if (sipStateCurrent != SIPSTATE_ACCEPTED) return false;
        sipStateCurrent = SIPSTATE_HOLD;
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        try {
            pjsipCall.setHold(prm);
            prm.delete();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Resume call on hold
     *
     * @return
     */
    boolean putHoldResume() {
        if (pjsipCall == null) return false;
        if (sipStateCurrent != SIPSTATE_HOLD) return false;
        sipStateCurrent = SIPSTATE_ACCEPTED;
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        CallSetting opt = prm.getOpt();
        opt.setFlag(pjsua_call_flag.PJSUA_CALL_UNHOLD.swigValue());
        opt.setAudioCount(1);
        opt.setVideoCount(0);
        try {
            pjsipCall.reinvite(prm);
            prm.delete();
            isMute = false; // resuming from hold will unmute call
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    boolean putInCallDTMF(String digits) {
        if (pjsipCall == null) return false;
        if (sipStateCurrent != SIPSTATE_ACCEPTED) return false;
        try {
            pjsipCall.dialDtmf(digits);
            return true;
        } catch (Exception e) {
            SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvError, e);
            return false;
        }

    }

    boolean putMute(boolean isMute) {
        if (pjsipCall == null) return false;
        if (sipStateCurrent != SIPSTATE_ACCEPTED) return false;

        CallInfo info = null;
        try {
            info = pjsipCall.getInfo();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        this.isMute = isMute;
        for (int i = 0; i < info.getMedia().size(); i++) {
            Media media = pjsipCall.getMedia(i);
            CallMediaInfo mediaInfo = info.getMedia().get(i);
            if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO && media != null
                    && mediaInfo.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                AudioMedia audioMedia = AudioMedia.typecastFromMedia(media);
                try {
                    AudDevManager mgr = SipService.getInstance().getSipEndpoint().getAudDevManager();
                    if (isMute) {
                        mgr.getCaptureDevMedia().stopTransmit(audioMedia);
                    } else {
                        mgr.getCaptureDevMedia().startTransmit(audioMedia);
                    }
                } catch (Exception e) {
//                    Log.e("Exception caught while connecting audio media to sound device.", e.toString());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * ACK INVITE with SIP 180 Ringing.
     * Signals the terminating caller to generate local ringing tones.
     * Immediately transitions to STATE_PROGRESS
     *
     * @return
     */
    boolean putProgressIn() {
        if (pjsipCall == null) return false;
        sipStateCurrent = SIPSTATE_PROGRESS_IN;
        CallOpParam op = new CallOpParam();
        op.setStatusCode(pjsip_status_code.PJSIP_SC_RINGING);
        try {
            pjsipCall.answer(op);
            op.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean putRecord(final boolean record) {
        if (pjsipCall == null) return false;
        if (sipStateCurrent != SIPSTATE_ACCEPTED) return false;
        if (!isRecording) return pjsipCall.recordStart();
        else return pjsipCall.recordStop();
    }

    /**
     * Immediately stop all call media and recording regardless of call state. Useful for handling network change or interruption: PJSIP may not reliably terminate call immediately leading to poor UX.
     *
     * @param call
     */
    private void stopMediaImmediate(PjsipCall call) {
        if (call == null) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, "stopMediaImmediate PjsipCall NULL");
            return;
        }
        try {
            final CallInfo callInfo = call.getInfo();
            if (callInfo == null) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "stopMediaImmediate callInfo NULL");
                return;
            }
            final CallMediaInfoVector cmiv = callInfo.getMedia();
            for (int i = 0; i < cmiv.size(); i++) {
                CallMediaInfo cmi = cmiv.get(i);
                if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO) {
                    Media m = call.getMedia(i);
                    AudioMedia am = AudioMedia.typecastFromMedia(m);
                    AudDevManager audDevManager = SipService.getInstance().getSipEndpoint().getAudDevManager();
                    audDevManager.getCaptureDevMedia().stopTransmit(am);
                    am.stopTransmit(audDevManager.getPlaybackDevMedia());
                }

            }
        } catch (Exception e) {
            if (DEBUG) {
                gLog.l(TAG, Logger.lvDebug, e);
                e.printStackTrace();
            }
        }
        putRecord(false);
    }

    private void updateRecordAuto() {
        autoRecord = SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_CALL_RECORD_AUTO, false);
    }

    private void wakeLockControl(boolean acquire, String tag) {
        if (acquire) {
            if (isWakeLocked) return;
            isWakeLocked = true;
            SipService.getInstance().wlSipService(acquire, tag, null);
        } else {
            if (!isWakeLocked) return;
            isWakeLocked = false;
            SipService.getInstance().wlSipService(acquire, tag, null);

        }
    }

    /**
     * Our extended PJSIP Call class. Do not expose methods here outside the containing class.
     * Code within this class should always be executed from the PJSIP thread.
     * Avoid blocking the PJSIP thread with event call backs and other long running tasks.
     */
    class PjsipCall extends Call {

        volatile private AudioMediaRecorder audioMediaRecorder = null;

        public PjsipCall(Account account, int pjsipCallId) {
            super(account, pjsipCallId);
        }

        public PjsipCall(Account account) {
            super(account);
        }

        @Override
        public void onCallState(OnCallStateParam prm) {
            try {
                final CallInfo ci = getInfo();
                final int pjsipCallId = ci.getId(); // get pjsipCallId before native call delete
                pjsip_inv_state pjcallstate = ci.getState();
                SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvVerbose, "pjcallstate " + pjcallstate.toString());

                if (pjcallstate != pjsip_inv_state.PJSIP_INV_STATE_NULL && pjcallstate != pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED)
                    wakeLockControl(true, "pjsipCallId " + pjsipCall.getId());

                if (pjcallstate == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                    recordStop();
                    if (ringbackGenerator != null) ringbackGenerator.stop();
                    SipService.getInstance().ringAndVibe(false);
                    sipStateCurrent = SIPSTATE_DISCONNECTED;
                    statCallStopTime = System.currentTimeMillis();
                    SipService.getInstance().pushToCallLog(pjsipCallId);
                    if (!isAccepted && !isOutgoing) SipService.getInstance().pushMissedCall(pjsipCallId);// missed call
                    SipService.getInstance().getStatsProvider().onCallTimeAdd(ci.getTotalDuration().getSec());
                    SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvDebug, "getLastReason " + ci.getLastReason());
                    SipService.getInstance().getLoggerInstanceShared().l(TAG, Logger.lvDebug, "getLastStatusCode " + ci.getLastStatusCode());
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            pjsip_status_code sc = ci.getLastStatusCode();
                            if (sc == pjsip_status_code.PJSIP_SC_SERVICE_UNAVAILABLE | sc == pjsip_status_code.PJSIP_SC_BAD_REQUEST | sc == pjsip_status_code.PJSIP_SC_ADDRESS_INCOMPLETE | sc == pjsip_status_code.PJSIP_SC_UNDECIPHERABLE | sc == pjsip_status_code.PJSIP_SC_SERVER_TIMEOUT) {
                                UiMessagesCommon.showCallFailedWithPjSipStatusCode(SipService.getInstance(), ci.getLastStatusCode());
                            }
                            SipService.getInstance().onCallEventDisconnected(pjsipCallId); // notify service
                        }
                    }).start();
                    // at this point sipCall is no longer in sipCalls list
//                    putHangup(); // hang up our end of call and close audio dependencies
                    wakeLockControl(false, "pjsipCallId " + pjsipCall.getId());// call has been disconnected, release wakeLockPartial
                } else if (pjcallstate == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
                    sipStateCurrent = SIPSTATE_ACCEPTED;
                    if (ringbackGenerator != null) ringbackGenerator.stop();
                    SipService.getInstance().ringAndVibe(false);
                    SipService.getInstance().pushEventOnCallStateChanged(pjsipCallId); // update audio route
                } else if (pjcallstate == pjsip_inv_state.PJSIP_INV_STATE_CALLING) {
                    if (ringbackGenerator == null) ringbackGenerator = new RingbackGenerator();
                    ringbackGenerator.start();
                }
            } catch (Exception e) {
                if (DEBUG) {
                    e.printStackTrace();
                    gLog.l(TAG, Logger.lvDebug, e);
                }
            }
        }

        @Override
        public void onCallMediaState(OnCallMediaStateParam prm) {
            CallInfo ci;
            try {
                ci = getInfo();
            } catch (Exception e) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                return;
            }
            CallMediaInfoVector cmiv = ci.getMedia();
            gLog.l(TAG, Logger.lvDebug, "cmiv size " + cmiv.size());

            for (int i = 0; i < cmiv.size(); i++) {
                gLog.l(TAG, Logger.lvDebug, "cmiv " + i);
                CallMediaInfo cmi = cmiv.get(i);
                if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO)
                    gLog.l(TAG, Logger.lvDebug, "pjsua_call_media_status " + cmi.getStatus().toString());
                if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                        (cmi.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE || cmi.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)) {
                    // unfortunately, on Java too, the returned Media cannot be downcasted to AudioMedia
                    Media m = getMedia(i);
                    AudioMedia am = AudioMedia.typecastFromMedia(m);
                    // connect ports
                    try {
                        AudDevManager audDevManager = SipService.getInstance().getSipEndpoint().getAudDevManager();
                        audDevManager.getCaptureDevMedia().startTransmit(am);
                        am.startTransmit(audDevManager.getPlaybackDevMedia());
                        float preampRx = SipService.getInstance().getLocalStore().getFloat(Prefs.KEY_MEDIA_PREAMP_RX, Prefs.KEY_MEDIA_PREAMP_RX_DEFAULT);
                        am.adjustTxLevel(preampRx); // am is the stream perspective, thus tx = to speaker
                        float preampTx = SipService.getInstance().getLocalStore().getFloat(Prefs.KEY_MEDIA_PREAMP_TX, Prefs.KEY_MEDIA_PREAMP_TX_DEFAULT);
                        am.adjustRxLevel(preampTx); // am is the stream perspective, thus rx = from mic
                        if (DEBUG)
                            gLog.l(TAG, Logger.lvVerbose, "AM Level RX TX " + am.getRxLevel() + " " + am.getTxLevel());
                    } catch (Exception e) {
                        if (DEBUG) {
                            e.printStackTrace();
                            gLog.l(TAG, Logger.lvDebug, e);
                        }
                        continue;
                    }
                    if (autoRecord) {
                        recordStart();
                        final int pjsipCallId = ci.getId();
                        SipService.getInstance().pushEventOnCallRecordStateChanged(pjsipCallId);
                    }
                }
            }
        }

        /**
         * Start recording media streams to file immediately.
         *
         * @return TRUE = success or already recording
         */
        public boolean recordStart() {
            if (isRecording || audioMediaRecorder != null) {
                if (DEV) gLog.l(TAG, Logger.lvVerbose, "Recording: recordStart ignored");
                return true;
            }
            if (!SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_BOOL_ACCEPT_CALL_RECORD_LEGAL, false)) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Recording: Aborted, legal not accepted.");
                return false;
            }
            if (!checkStorageFolder()) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Recording: Permission denied or SDCARD unavailable");
                UiMessagesCommon.showCallRecordExternalStorageProblem(SipService.getInstance());
                autoRecord = false; // prevent future attempts to begin record onCallMediaState
                return false;
            }
            CallInfo ci;
            try {
                ci = getInfo();
            } catch (Exception e) {
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                return false;
            }
            CallMediaInfoVector cmiv = ci.getMedia();
            for (int i = 0; i < cmiv.size(); i++) {
                if (DEV) gLog.l(TAG, Logger.lvVerbose, "Recording: cmiv " + i);
                CallMediaInfo cmi = cmiv.get(i);
                if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO)
                    gLog.l(TAG, Logger.lvVerbose, "Recording: pjsua_call_media_status " + cmi.getStatus().toString());
                if (cmi.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                        (cmi.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE || cmi.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)) {
                    Media m = getMedia(i);
                    AudioMedia am = AudioMedia.typecastFromMedia(m);
                    if (DEV)
                        if (audioMediaRecorder != null)
                            gLog.l(TAG, Logger.lvDebug, "Recording: audioMediaRecorder NOT NULL");
                    try {
                        audioMediaRecorder = new AudioMediaRecorder();
                        audioMediaRecorder.createRecorder(getNewAudioRecordFilePath());
                        AudDevManager audDevManager = SipService.getInstance().getSipEndpoint().getAudDevManager();
                        audDevManager.getCaptureDevMedia().startTransmit(audioMediaRecorder);
                        am.startTransmit(audioMediaRecorder);
                        isRecording = true;
                        if (DEV) gLog.l(TAG, Logger.lvVerbose, "Recording: Started...");
                        return true;
                    } catch (Exception e) {
                        UiMessagesCommon.showCallRecordExternalStorageProblem(SipService.getInstance());
                        // cleanup audioMediaRecorder
                        try {
                            am.stopTransmit(audioMediaRecorder);
                        } catch (Exception e1) {
                            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e1);
                        }
                        audioMediaRecorder.delete();
                        audioMediaRecorder = null;
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                    }
                }
            }
            return false;
        }

        /**
         * Stop recording media streams immediately.
         *
         * @return TRUE = success, FALSE = nothing to stop
         */
        public boolean recordStop() {
            if (!isRecording) {
                if (DEV) gLog.l(TAG, Logger.lvVerbose, "Recording: Nothing to stop");
                return false;
            }
            if (audioMediaRecorder == null) {
                if (DEV) gLog.l(TAG, Logger.lvVerbose, "Recording: Nothing to stop, audioMediaRecorder NULL");
                return false;
            }
            isRecording = false;
            audioMediaRecorder.delete();
            audioMediaRecorder = null;
            if (DEV) gLog.l(TAG, Logger.lvVerbose, "Recording: Stopped");
            return true;
        }

        private boolean checkStorageFolder() {
            return SipService.getInstance().getAppExtStoragePath() != null;
        }

        private String getNewAudioRecordFileName() {
            final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            final Date date = new Date();
            StringBuilder sb = new StringBuilder();
            sb.append(dateFormat.format(date));
            if (isOutgoing()) {
                sb.append("_TO_");
                sb.append(SipService.sanitizeForFileSystem(getExtension()));
            } else {
                sb.append("_FROM_");
                sb.append(SipService.sanitizeForFileSystem(getExtension()));
            }
            return sb.toString();
        }

        private String getNewAudioRecordFilePath() {
            StringBuilder sb = new StringBuilder();
            sb.append(SipService.getInstance().getAppExtStoragePath());
            sb.append("/");
            sb.append(getNewAudioRecordFileName());
            sb.append(".wav");
            return sb.toString();
        }


    }

}
