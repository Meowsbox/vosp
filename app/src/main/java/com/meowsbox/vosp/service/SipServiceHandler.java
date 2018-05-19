/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;

import com.meowsbox.internal.siptest.PjSipTimerWrapper;
import com.meowsbox.vosp.InCallActivity;
import com.meowsbox.vosp.common.Logger;

import java.util.concurrent.ExecutionException;

import static com.meowsbox.vosp.service.NotificationController.KEY_PENDING_INTENT_CALL_LOG;
import static com.meowsbox.vosp.service.NotificationController.TAG_MISSED;

/**
 * Handles message based commands in an independent thread. Commands invoked via handler provide the async capabilities and limited isolation.
 * Created by dhon on 1/10/2017.
 */

class SipServiceHandler extends Handler {
    public final static boolean DEBUG = SipService.DEBUG;
    public final static boolean DEV = SipService.DEV;
    public final String TAG = this.getClass().getName();
    private SipService sipService;
    private Logger gLog;

    SipServiceHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(final Message msg) {
        if (sipService == null) sipService = SipService.getInstance();
        if (gLog == null) gLog = sipService.getLoggerInstanceShared();
        Bundle extras = msg.getData();

        switch (msg.arg1) {
            case SipServiceMessages.MSG_CALL_ANSWER:
                onCallAnswer(extras);
                break;
            case SipServiceMessages.MSG_CALL_DECLINE:
                onCallDecline(extras);
                break;
            case SipServiceMessages.MSG_CALL_HANGUP:
                onCallHangup(extras);
                break;
            case SipServiceMessages.MSG_CALL_HOLD:
                onCallHold(extras);
                break;
            case SipServiceMessages.MSG_CALL_HOLD_RESUME:
                onCallHoldResume(extras);
                break;
            case SipServiceMessages.MSG_CALL_MISSED_DISMISS:
                onCallMissedDismiss(extras);
                break;
            case SipServiceMessages.MSG_SHOW_CALL_LOG:
                onNotificationShowCallLog(extras);
                break;
            case SipServiceMessages.MSG_NEWOUTGOINGCALL:
                onNewOutgoingCall(extras);
                break;
            case SipServiceMessages.MSG_CONNECTIVITY_CHANGED:
                onConnectivityChanged(msg.arg2, msg.getData().getBoolean("netdiff"), extras.getString("wlsource"));
                break;
            case SipServiceMessages.MSG_DOZE_STATE_CHANGED:
                onDozeStateChanged();
                break;
            case SipServiceMessages.MSG_ACCOUNTS_REGISTER_SINGLE:
                onAccountRegSingle(msg);
                break;
            case SipServiceMessages.MSG_ACCOUNTS_REGISTER_ALL:
                onAccountRegAll();
                break;
            case SipServiceMessages.MSG_REQ_BATTERY_SAVER_EXEMPT:
                onReqBatterySaverExempt();
                break;
            case SipServiceMessages.MSG_SCREEN_STATE_CHANGED:
                onScreenStateChanged(msg);
                break;
            case SipServiceMessages.MSG_EP_TRANSPORT_FLUSH:
                onEpTransportFlush();
                break;
//            case SipServiceMessages.MSG_EP_TRANSPORT_REGEN:
//                onEpTransportRegen();
//                break;
            case SipServiceMessages.MSG_STACK_RESTART:
                onStackRestart();
                break;
            case SipServiceMessages.MSG_STACK_STOP:
                onStackStop();
                break;
            case SipServiceMessages.MSG_STACK_START:
                onStackStart();
                break;
            case SipServiceMessages.MSG_STACK_START_AND_REGISTER:
                onStackStartAndRegister();
                break;
            case SipServiceMessages.MSG_CALL_INCOMING:
                onIncomingCall(msg);
                break;
            case SipServiceMessages.MSG_WAKELOCK_ACQUIRE:
                onWakelock(true, msg);
                break;
            case SipServiceMessages.MSG_WAKELOCK_RELEASE:
                onWakelock(false, msg);
                break;
            case SipServiceMessages.MSG_SERVICE_STOP:
                onStopService();
                break;
            default:
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Unhandled message");
                break;
        }

    }

    private void onAccountRegAll() {
        try {
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    sipService.accountsRegisterAll(true);
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
        }
    }

    private void onAccountRegSingle(final Message msg) {
        try {
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        SipAccount sipAccount = sipService.getSipAccountById(msg.arg2);
                        if (sipAccount == null) return;
                        sipAccount.setRegistration(true);
                        sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("connection_lost_reconnect", "Connection lost, reconnecting"), null);
                    } catch (Exception e) {
                        if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
                    }
                }
            });
        } catch (Exception e) {
            gLog.l(TAG, Logger.lvError, e);
        }
    }

    private void onCallAnswer(Bundle extras) {
        if (extras == null) return;
        sipService.callAnswer(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
    }

    private void onCallDecline(Bundle extras) {
        if (extras == null) return;
        sipService.callDecline(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
    }

    private void onCallHangup(Bundle extras) {
        if (extras == null) return;
        sipService.callHangup(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
    }

    private void onCallHold(Bundle extras) {
        if (extras == null) return;
        sipService.callHold(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
    }

    private void onCallHoldResume(Bundle extras) {
        if (extras == null) return;
        sipService.callHoldResume(extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
    }

    private void onCallMissedDismiss(Bundle extras) {
        if (extras == null) return;
        if (DEV) gLog.l(Logger.lvVerbose);
        final NotificationController notificationController = sipService.getNotificationController();
        notificationController.cancelNotificationById(TAG_MISSED, extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        notificationController.clearUnSeenCallMissedCounter();
    }

    private void onConnectivityChanged(final int arg2, final boolean netdiff, final String wlSource) { // wakelock is in effect from caller
        try {
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    if (sipService.isNetworkAvailable()) { // has network connectivity
                        sipService.uiMessageDismissByType(InAppNotifications.TYPE_WARN_NO_NETWORK); // clear any previous shown no_network ui messages

                        //BUGFIX: force reset process bind to interface needed on some devices
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                            ConnectivityManager.setProcessDefaultNetwork(null);
                        else sipService.getConnectivityManager().bindProcessToNetwork(null);

                        if (!sipService.isStackReady()) sipService.stackStart();
                        else {
                            final NetworkInfo activeNetworkInfo = sipService.getConnectivityManager().getActiveNetworkInfo();
                            if (activeNetworkInfo != null) {
                                sipService.getSipEndpoint().networkTypeChange(activeNetworkInfo.getType());
                                sipService.updateAccountUdpKeepAliveFromEndpointAll();
                            }
                            if (netdiff) sipService.getSipEndpoint().regenTransport();
                        }
                        if (sipService.hasActiveCalls()) UiMessagesCommon.showInCallNetworkChange(sipService);
                    } else { // no network connectivity
                        UiMessagesCommon.showNoNetwork(sipService);
                        // pjsip 2.7 internally handles connectivity and ip address changes, no need to stackStop
//                        sipService.stackStop();
                        sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("no_network", "No Network"), null);
//                        sipService.wakelockPartialReleaseAll();
                    }
                    sipService.updateWifiLockState();
                    if (arg2 == 0) {
                        sipService.wlOnNetworkChange(false, wlSource, null); // release caller's wakelock
                    }
                }
            });
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void onDozeStateChanged() {
        sipService.refreshRegistrationOnDoze();
        sipService.refreshDozeController();
    }

    private void onEpTransportFlush() {
        try {
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("network_problem_retry", "Network problem, retrying"), null);
                    sipService.getSipEndpoint().flushTransport();
                    sipService.resetAccountRetryCountAll();
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        } catch (InterruptedException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
    }

    private void onIncomingCall(Message msg) {
        sipService.onCallIncoming(msg.arg2);
    }

//    private void onEpTransportRegen() {
//        try {
//            sipService.runOnServiceThread(new Runnable() {
//                @Override
//                public void run() {
//                    sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("network_problem_retry", "Network problem, retrying"), null);
//                    sipService.getSipEndpoint().regenTransport();
//                    sipService.accountsRegisterAll(false);
//                    sipService.resetAccountRetryCountAll();
//                }
//            });
//        } catch (ExecutionException e) {
//            if (DEBUG) gLog.l(TAG ,Logger.lvDebug, e);
//        } catch (InterruptedException e) {
//            if (DEBUG) gLog.l(TAG ,Logger.lvDebug, e);
//        }
//    }

    private void onNewOutgoingCall(Bundle extras) {
        if (extras == null) return;
        String phoneNumber = extras.getString(Intent.EXTRA_PHONE_NUMBER);
        if (phoneNumber == null) return;
//                // Launch activity to choose what to do with this call
//                Intent outgoingCallChooserIntent = new Intent(Intent.ACTION_DIAL);
//                outgoingCallChooserIntent.setData(Uri.parse("tel:" + phoneNumber));
//                Intent chooser = Intent.createChooser(outgoingCallChooserIntent, "choose");
//                chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                sipService.startActivity(chooser);

        // create outbound sip call, then pass to InCallActivity
        int sipCallId = sipService.callOutDefault(phoneNumber);
//        if (sipCallId == -1) {
////            sipService.toastLocal("call_failed", "Call Failed", Toast.LENGTH_LONG);
//            UiMessagesCommon.showCallFailed(sipService);
//            return; // call failed
//        }
        Intent intent = new Intent(sipService, InCallActivity.class);
        intent.putExtra(InCallActivity.EXTRA_BIND_PJSIPCALLID, sipCallId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sipService.startActivity(intent);
    }

    private void onNotificationShowCallLog(Bundle extras) {
        if (extras == null) return;
        final NotificationController notificationController = sipService.getNotificationController();
        notificationController.cancelNotificationById(TAG_MISSED, extras.getInt(NotificationController.EXTRA_PJSIP_CALL_ID));
        notificationController.clearUnSeenCallMissedCounter();
        final Parcelable callLogParcel = extras.getParcelable(KEY_PENDING_INTENT_CALL_LOG);
        if (callLogParcel != null) {
            final PendingIntent pi = (PendingIntent) callLogParcel;
            try {
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                if (DEBUG) {
                    gLog.l(TAG, Logger.lvVerbose, e);
                    e.printStackTrace();
                }
            }
        }

    }

    private void onReqBatterySaverExempt() {
        sipService.showBatteryExemptRequest();
    }

    private void onScreenStateChanged(Message msg) {
        switch (msg.arg2) {
            case 0: // off
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MSG_SCREEN_STATE_CHANGED");
                PjSipTimerWrapper.getInstance().onScreenStateChanged(false);
                sipService.getScheduledRun().onScreenStateChanged(false);
                sipService.refreshDozeController();
                break;
            case 1: // on
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "MSG_SCREEN_STATE_CHANGED");
                PjSipTimerWrapper.getInstance().onScreenStateChanged(true);
                sipService.getScheduledRun().onScreenStateChanged(true);
                sipService.refreshRegistrationsIfRequired();
                sipService.refreshDozeController();
                break;
        }
    }

    private void onStackRestart() {
        try {
            sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("restarting_backend", "Restarting backend"), null);
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    sipService.stackRestart();
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        } catch (InterruptedException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
    }

    private void onStackStart() {
        try {
            sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("starting", "Starting"), null);
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    sipService.stackStart();
                    sipService.accountsRegisterAll(true);
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        } catch (InterruptedException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
    }

    private void onStackStartAndRegister() {
        try {
            sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("starting", "Starting"), null);
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    sipService.stackStart();
                    sipService.accountsRegisterAll(true);
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        } catch (InterruptedException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
    }

    private void onStackStop() {
        try {
            sipService.getNotificationController().setForegroundMessage(null, sipService.getI18n().getString("stopping", "Stopping"), null);
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    sipService.stackStop();
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        } catch (InterruptedException e) {
            if (DEBUG) gLog.l(TAG, Logger.lvDebug, e);
        }
    }

    private void onStopService() {
        try {
            sipService.runOnServiceThread(new Runnable() {
                @Override
                public void run() {
                    gLog.l(TAG, Logger.lvVerbose, "onStopService");
                    sipService.pushEventOnExit();
                    gLog.l(TAG, Logger.lvVerbose, "onStopService");
                    sipService.stackStop();
                    gLog.l(TAG, Logger.lvVerbose, "onStopService");
                    sipService.stopForeground(true);
                    gLog.l(TAG, Logger.lvVerbose, "onStopService");
                    sipService.wlReleaseAll();
                    gLog.l(TAG, Logger.lvVerbose, "onStopService");
                    sipService.stopSelf();
                    gLog.l(TAG, Logger.lvVerbose, "onStopService");
                }
            });
        } catch (ExecutionException e) {
            if (DEBUG) e.printStackTrace();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }

    }

    private void onWakelock(boolean acquire, Message msg) {
        String tag = null;
        Bundle data = msg.getData();
        if (data != null) {
            tag = data.getString(SipServiceMessages.EXTRA_WAKELOCK_TAG, "not specified");
        }
        sipService.wlSipService(acquire,tag,null);
    }

}
