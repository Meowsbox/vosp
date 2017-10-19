/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.TaskStackBuilder;
import android.view.View;
import android.widget.RemoteViews;

import com.meowsbox.vosp.ContactProvider;
import com.meowsbox.vosp.DialtactsActivity;
import com.meowsbox.vosp.InCallActivity;
import com.meowsbox.vosp.R;
import com.meowsbox.vosp.android.common.util.BitmapUtil;
import com.meowsbox.vosp.android.dialer.callog.CallLogActivity;
import com.meowsbox.vosp.common.Logger;
import com.meowsbox.vosp.service.receivers.NotificationReceiver;

import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedList;

import static com.meowsbox.vosp.InCallActivity.EXTRA_BIND_PJSIPCALLID;

/**
 * Created by dhon on 11/5/2016.
 */

public class NotificationController {
    public static final boolean DEBUG = SipService.DEBUG;
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_PJSIP_CALL_ID = "pjsip_call_id";
    public static final String TAG_INCOMING = "tag_incoming";
    public static final String TAG_MISSED = "tag_missed";
    public static final String TAG_ONGOING = "tag_ongoing";
    public static final String KEY_PENDING_INTENT_CALL_LOG = "key_pending_intent_call_log";
    public final String TAG = this.getClass().getName();
    final int iconHeight;
    final int iconWidth;
    public Notification serviceForgroundNotification;
    private Logger gLog = null;
    private Context context;
    private SipService sipService;
    private NotificationManager notificationManager;
    private LinkedList<NotificationMeta> llNotifications = new LinkedList<>(); // ll of active notifications needed by cancelNotificationAll method
    private volatile int unseenCallMissedCount = 0;
    private String fgTitle, fgText, fgSubText;

    NotificationController(SipService sipService) {
        this.sipService = sipService;
        this.context = sipService;
        gLog = sipService.getLoggerInstanceShared();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        iconHeight = (int) context.getResources().getDimension(android.R.dimen.notification_large_icon_height);
        iconWidth = (int) context.getResources().getDimension(android.R.dimen.notification_large_icon_width);
    }

    /**
     * Returns PendingIntent from the specified action and optional extras.
     *
     * @param context
     * @param action
     * @param extras  optional extras bundle, usually specific to the action
     * @return
     */
    private static PendingIntent createNotificationPendingIntent(Context context, String action, Bundle extras) {
        final Intent intent = new Intent(action, null, context, NotificationReceiver.class);
        int requestCode = 0;
        if (extras != null) {
            intent.putExtras(extras);
            requestCode = extras.getInt(EXTRA_PJSIP_CALL_ID); // zero if none is set
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }


    /**
     * Cancel all notifications
     */
    void cancelNotificationAll() {
        notificationManager.cancelAll(); // does not work with notifications that have a tag specified
        synchronized (llNotifications) {
            for (NotificationMeta notification : llNotifications) {
                cancelNotificationById(notification.tag, notification.id);
            }
            llNotifications.clear();
        }
    }

    /**
     * Cancel a notification with specified ID and a NULL tag. Notifications with a specified tag silently fail to cancel.
     *
     * @param id
     */
    void cancelNotificationById(int id) {
        notificationManager.cancel(id);
    }

    /**
     * Cancel a notification with specified ID and tag.
     *
     * @param tag
     * @param id
     */
    void cancelNotificationById(String tag, int id) {
        notificationManager.cancel(tag, id);
        removeTrackedNotificationEntry(id, tag);
    }

    void cancelNotificationByTag(String tag) {
        synchronized (llNotifications) {
            LinkedList<NotificationMeta> toRemove = new LinkedList<>();
            for (NotificationMeta n : llNotifications) {
                if (n.tag == tag) toRemove.add(n);
            }
            for (NotificationMeta nm : toRemove) {
                notificationManager.cancel(nm.tag, nm.id);
            }
            llNotifications.removeAll(toRemove);
        }
    }

    void clearUnSeenCallMissedCounter() {
        unseenCallMissedCount = 0;
    }

    void createNotificationCallIncomingMissed(int pjsipCallId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "missedcall");
        if (getWasDeclinedFromCallId(pjsipCallId))
            return; // call declined by user, no need to display missed call notification
        unseenCallMissedCount++;
        // check if a notification already exists
        synchronized (llNotifications) {
            for (NotificationMeta n : llNotifications) {
                if (n.tag == TAG_MISSED) {
                    createNotificationCallIncomingMissedConbined(pjsipCallId); // update existing call missed notification
                    return; // return and do not create a new notification
                }
            }
        }

        String EXTENSION = getExtensionFromCallId(pjsipCallId); // get incoming extension
        Integer contactId = ContactProvider.getContactIdByNumber(context, EXTENSION); // lookup extension in user contacts
        if (contactId != null)
            EXTENSION = ContactProvider.getContactName(context, contactId); // replace extension with friendly contact display_name if found
        {
            String remoteDisplayName = getRemoteDisplayNameFromCallId(pjsipCallId);
            if (remoteDisplayName != null && !remoteDisplayName.isEmpty())
                EXTENSION = remoteDisplayName;  // replace extension with remote provided display name
        }
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, pjsipCallId + " " + EXTENSION);
        Notification.Builder mBuilder = new Notification.Builder(context);
        mBuilder.setPriority(Notification.PRIORITY_DEFAULT);
        mBuilder.setCategory(Notification.CATEGORY_CALL);
        mBuilder.setVisibility(Notification.VISIBILITY_PRIVATE);

        mBuilder.setUsesChronometer(false);

        long statCallStartTimeFromCallId = getStatCallStartTimeFromCallId(pjsipCallId);
        if (statCallStartTimeFromCallId == 0) statCallStartTimeFromCallId = System.currentTimeMillis(); // sanity check
        mBuilder.setWhen(statCallStartTimeFromCallId);
        mBuilder.setShowWhen(true);

//        if (contactId != null) {
//            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
//            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
//            mBuilder.setLargeIcon(roundedBitmap);
//        }

        mBuilder.setSmallIcon(R.drawable.ic_phone_missed_white_24dp)
                .setContentTitle(EXTENSION)
                .setContentText(SipService.getInstance().getI18n().getString("missed_call", "Missed Call"));


        Bundle callBundle = new Bundle();
        callBundle.putInt(EXTRA_BIND_PJSIPCALLID, pjsipCallId);
        callBundle.putInt(EXTRA_NOTIFICATION_ID, pjsipCallId);
        callBundle.putInt(EXTRA_PJSIP_CALL_ID, pjsipCallId);

//        addCallAnswerAction(mBuilder, callBundle);
//        addCallDismissAction(mBuilder, callBundle);
        addCallMissedSwipeAwayAction(mBuilder, callBundle);

//        // Creates an explicit intent for an Activity in your app
//        Intent resultIntent = new Intent(context, DialtactsActivity.class);
//
//        // The stack builder object will contain an artificial back stack for the
//        // started Activity.
//        // This ensures that navigating backward from the Activity leads out of
//        // your application to the Home screen.
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
//        // Adds the back stack for the Intent (but not the Intent itself)
//        stackBuilder.addParentStack(DialtactsActivity.class);
//        // Adds the Intent that starts the Activity to the top of the stack
//        stackBuilder.addNextIntent(resultIntent);
//
//        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

//        mBuilder.setFullScreenIntent(createFullScreenActivityCallPendingIntent(pjsipCallId, callBundle), true);
//        mBuilder.setContentIntent(createFullScreenActivityCallPendingIntentCallLog(pjsipCallId, callBundle));

        // have SipService send the pending intent as to handle notification state cleanup
        callBundle.putParcelable(KEY_PENDING_INTENT_CALL_LOG, createFullScreenActivityCallPendingIntentCallLog(pjsipCallId, callBundle));
        PendingIntent callLogPendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_SHOW_CALL_LOG, callBundle);
        mBuilder.setContentIntent(callLogPendingIntent);


        mBuilder.setAutoCancel(true);

        Notification notification = mBuilder.build();
//        notification.flags |= Notification.FLAG_INSISTENT;
//        notification.flags |= Notification.FLAG_NO_CLEAR;

        final RemoteViewBuilder remoteViewBuilder = RemoteViewBuilder.newInstance(context);

        remoteViewBuilder.setHeaderText(SipService.getInstance().getI18n().getString("app_name", "VOSP"))
                .setSmallIcon(R.drawable.ic_noti_default)
                .setHeaderAltText(SipService.getInstance().getI18n().getString("missed_call", "Missed Call"))
                .setMainText(EXTENSION)
                .setChronoAsString(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()))
                .setUseThemeDark(SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false));


        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            remoteViewBuilder.setLargeIcon(roundedBitmap);
        } else remoteViewBuilder.setLargeIcon(R.drawable.ic_phone_missed_white_24dp);


        final RemoteViews rv = remoteViewBuilder.buildStandard();
        notification.contentView = rv;


        notificationManager.notify(TAG_MISSED, pjsipCallId, notification);
        synchronized (llNotifications) {
            llNotifications.add(new NotificationMeta(pjsipCallId, TAG_MISSED));
        }
    }

    void createNotificationCallIncomingMissedConbined(int pjsipCallId) {

        // clear all existing call missed notifications
        cancelNotificationByTag(TAG_MISSED);

        if (getWasDeclinedFromCallId(pjsipCallId))
            return; // call declined by user, no need to display missed call notification
        Notification.Builder mBuilder = new Notification.Builder(context);
        mBuilder.setPriority(Notification.PRIORITY_DEFAULT);
        mBuilder.setCategory(Notification.CATEGORY_CALL);
        mBuilder.setVisibility(Notification.VISIBILITY_PRIVATE);

        mBuilder.setUsesChronometer(false);

        long statCallStartTimeFromCallId = getStatCallStartTimeFromCallId(pjsipCallId);
        if (statCallStartTimeFromCallId == 0) statCallStartTimeFromCallId = System.currentTimeMillis(); // sanity check
        mBuilder.setWhen(statCallStartTimeFromCallId);
        mBuilder.setShowWhen(true);

        String missedCallString = String.valueOf(unseenCallMissedCount) + SipService.getInstance().getI18n().getString("missed_calls", " Missed Calls");

        mBuilder.setSmallIcon(R.drawable.ic_phone_missed_white_24dp)
                .setContentTitle(missedCallString);

        Bundle callBundle = new Bundle();
        callBundle.putInt(EXTRA_BIND_PJSIPCALLID, pjsipCallId);
        callBundle.putInt(EXTRA_NOTIFICATION_ID, pjsipCallId);
        callBundle.putInt(EXTRA_PJSIP_CALL_ID, pjsipCallId);

        addCallMissedSwipeAwayAction(mBuilder, callBundle);

        callBundle.putParcelable(KEY_PENDING_INTENT_CALL_LOG, createFullScreenActivityCallPendingIntentCallLog(pjsipCallId, callBundle));
        PendingIntent callLogPendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_SHOW_CALL_LOG, callBundle);
        mBuilder.setContentIntent(callLogPendingIntent);

        mBuilder.setAutoCancel(true);

        Notification notification = mBuilder.build();

        final RemoteViewBuilder remoteViewBuilder = RemoteViewBuilder.newInstance(context);

        remoteViewBuilder.setHeaderText(SipService.getInstance().getI18n().getString("app_name", "VOSP"))
                .setSmallIcon(R.drawable.ic_noti_default)
                .setMainText(missedCallString)
                .setChronoAsString(DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()))
                .setUseThemeDark(SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false));


        remoteViewBuilder.setLargeIcon(R.drawable.ic_phone_missed_white_24dp);

        final RemoteViews rv = remoteViewBuilder.buildStandard();
        notification.contentView = rv;


        notificationManager.notify(TAG_MISSED, pjsipCallId, notification);
        synchronized (llNotifications) {
            llNotifications.add(new NotificationMeta(pjsipCallId, TAG_MISSED));
        }
    }

    Notification.Builder getForegroundNotificationBuilder() {
        Notification.Builder builder = new Notification.Builder(context)
                .setSmallIcon(R.drawable.ic_noti_default)
                .setContentTitle(SipService.getInstance().getI18n().getString("app_name", "VOSP"));
        return builder;
    }

    PendingIntent getForegroundServiceIntent() {
        Intent notificationIntent = new Intent(context, DialtactsActivity.class);
        return PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    /**
     * Create the foreground service notification
     *
     * @param optionalBuilder Optional custom Notification.Builder
     * @return
     */
    Notification getForegroundServiceNotification(Notification.Builder optionalBuilder) {
        Notification.Builder builder;
        if (optionalBuilder != null) builder = optionalBuilder;
        else builder = getForegroundNotificationBuilder();
        serviceForgroundNotification = builder.setContentIntent(getForegroundServiceIntent()).build();
        return serviceForgroundNotification;
    }

    void setForegroundMessage(String title, String text, String subText) {
        Notification.Builder builder = getForegroundNotificationBuilder();
        if (title != null) builder.setContentTitle(title);
        if (text != null) builder.setContentText(text);
        if (subText != null) builder.setSubText(subText);
        fgTitle = title;
        fgText = text;
        fgSubText = subText;
        serviceForgroundNotification = getForegroundServiceNotification(builder);

        final RemoteViewBuilder remoteViewBuilder = RemoteViewBuilder.newInstance(context);
        remoteViewBuilder
                .populateHeaderDefault(sipService)
                .setMainText(text)
                .setAltText(subText)
                .setUseThemeDark(SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false));


        serviceForgroundNotification.contentView = remoteViewBuilder.buildStandard();

        notificationManager.notify(SipService.SERVICE_FOREGROUND_ID, serviceForgroundNotification);
        SipService.getInstance().startForeground(SipService.SERVICE_FOREGROUND_ID, serviceForgroundNotification);
    }

    void updateOnCallState(int pjsipCallId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, pjsipCallId);
        SipCall sipCall = SipService.getInstance().getSipCallById(pjsipCallId);
        if (sipCall == null) {
            cancelNotificationById(TAG_ONGOING, pjsipCallId);
            cancelNotificationById(TAG_INCOMING, pjsipCallId);
            return;
        }
        int state = sipCall.getSipState();
        switch (state) {
            case SipCall.SIPSTATE_PROGRESS_IN:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "SIPSTATE_PROGRESS_IN");
                createNotificationCallIncoming(pjsipCallId);
                break;
            case SipCall.SIPSTATE_PROGRESS_OUT:
                if (DEBUG) gLog.l(TAG, Logger.lvVerbose, "SIPSTATE_PROGRESS_OUT");
                createNotificationCallOngoing(pjsipCallId);
                break;
            case SipCall.SIPSTATE_ACCEPTED:
                createNotificationCallOngoing(pjsipCallId);
                break;
            case SipCall.SIPSTATE_HOLD:
                createNotificationCallHold(pjsipCallId);
                break;
            case SipCall.SIPSTATE_DISCONNECTED:
                cancelNotificationById(TAG_ONGOING, pjsipCallId);
                break;
            default:
                if (DEBUG) gLog.l(TAG, Logger.lvDebug, "Unhandled sip call state: " + state);
        }

    }

    private void addCallAnswerAction(Notification.Builder builder, Bundle extras) {

        PendingIntent answerVoicePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_ANSWER_VOICE_INCOMING_CALL, extras);
        builder.addAction(R.drawable.ic_call_white_24dp, "Answer", answerVoicePendingIntent);
    }

    private void addCallDismissAction(Notification.Builder builder, Bundle extras) {
        PendingIntent declinePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_DECLINE_INCOMING_CALL, extras);
        builder.addAction(R.drawable.ic_close_white_24dp, "Decline", declinePendingIntent);
    }

    private void addCallHangupAction(Notification.Builder builder, Bundle extras) {
        PendingIntent hangupPendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_HANG_UP_ONGOING_CALL, extras);
        builder.addAction(R.drawable.ic_call_end_white_24dp, "Hangup", hangupPendingIntent);
    }

    private void addCallHoldAction(Notification.Builder builder, Bundle extras) {
        PendingIntent holdPendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_HOLD_ONGOING_CALL, extras);
        builder.addAction(R.drawable.ic_pause_white_24dp, "Hold", holdPendingIntent);
    }

    private void addCallHoldResumeAction(Notification.Builder builder, Bundle extras) {
        PendingIntent resumePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_HOLD_RESUME_ONGOING_CALL, extras);
        builder.addAction(R.drawable.ic_play_arrow_white_24dp, "Resume", resumePendingIntent);
    }

    private void addCallIncomingSwipeAwayAction(Notification.Builder builder, Bundle extras) {
        PendingIntent declinePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_IGNORE_INCOMING_CALL, extras);
        builder.setDeleteIntent(declinePendingIntent);
    }

    private void addCallMissedSwipeAwayAction(Notification.Builder builder, Bundle extras) {
        PendingIntent declinePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_DISMISS_MISSED_CALL, extras);
        builder.setDeleteIntent(declinePendingIntent);
    }

    private PendingIntent createFullScreenActivityCallPendingIntent(int pjsipCallId, Bundle extras) {
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(context, InCallActivity.class);
        if (extras != null) resultIntent.putExtras(extras);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(InCallActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);


        int requestCode = 0;
        if (extras != null) requestCode = extras.getInt(EXTRA_PJSIP_CALL_ID); // zero if none is set

        return stackBuilder.getPendingIntent(requestCode, PendingIntent.FLAG_UPDATE_CURRENT);

//        return PendingIntent.getBroadcast(context, requestCode, resultIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private PendingIntent createFullScreenActivityCallPendingIntentCallLog(int pjsipCallId, Bundle extras) {
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(context, CallLogActivity.class);
        if (extras != null) resultIntent.putExtras(extras);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(CallLogActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);


        int requestCode = 0;
        if (extras != null) requestCode = extras.getInt(EXTRA_PJSIP_CALL_ID); // zero if none is set

        return stackBuilder.getPendingIntent(requestCode, PendingIntent.FLAG_UPDATE_CURRENT);

//        return PendingIntent.getBroadcast(context, requestCode, resultIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private void createNotificationCallHold(int pjsipCallId) {
        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, pjsipCallId);

        String contactName = null; // preferred display name
        String remoteDisplayName = null; // display name when !contactName
        String EXTENSION = getExtensionFromCallId(pjsipCallId); // get incoming extension
        Integer contactId = ContactProvider.getContactIdByNumber(context, EXTENSION); // lookup extension in user contacts
        if (contactId != null)
            contactName = ContactProvider.getContactName(context, contactId); // replace extension with friendly contact display_name if found
        else
            remoteDisplayName = getRemoteDisplayNameFromCallId(pjsipCallId);

        Notification.Builder mBuilder = new Notification.Builder(context);
        mBuilder.setPriority(Notification.PRIORITY_HIGH);
//        mBuilder.setCategory(Notification.CATEGORY_CALL);
//        mBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);

        mBuilder.setUsesChronometer(true);
        mBuilder.setWhen(System.currentTimeMillis()); // start new chrono from hold beginning time

        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            mBuilder.setLargeIcon(roundedBitmap);
        }

        mBuilder.setSmallIcon(R.drawable.ic_phone_paused_white_24dp)
                .setContentText(SipService.getInstance().getI18n().getString("held_call", "Held Call"));

        setNotificationDisplayName(mBuilder, contactName, remoteDisplayName, EXTENSION);

        Bundle callBundle = new Bundle();
        callBundle.putInt(EXTRA_BIND_PJSIPCALLID, pjsipCallId);
        callBundle.putInt(EXTRA_NOTIFICATION_ID, pjsipCallId);
        callBundle.putInt(EXTRA_PJSIP_CALL_ID, pjsipCallId);

        addCallHangupAction(mBuilder, callBundle);
        addCallHoldResumeAction(mBuilder, callBundle);
        mBuilder.setDeleteIntent(null);

        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(context, DialtactsActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(DialtactsActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);

//        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
//        mBuilder.setFullScreenIntent(resultPendingIntent, true);

        mBuilder.setContentIntent(createFullScreenActivityCallPendingIntent(pjsipCallId, callBundle));


        Notification notification = mBuilder.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        notification.flags |= Notification.FLAG_NO_CLEAR;

        final RemoteViewBuilder remoteViewBuilder = RemoteViewBuilder.newInstance(context)
                .populateHeaderDefault(SipService.getInstance())
                .setHeaderAltText(SipService.getInstance().getI18n().getString("held_call", "Held Call"))
                .setUseThemeDark(SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false));

        if (contactName != null && !contactName.isEmpty()) {
            remoteViewBuilder.setMainText(contactName);
            remoteViewBuilder.setAltText2(EXTENSION);
        } else if (remoteDisplayName != null && !remoteDisplayName.isEmpty()) {
            remoteViewBuilder.setMainText(remoteDisplayName);
            remoteViewBuilder.setAltText2(EXTENSION);
        } else remoteViewBuilder.setMainText(EXTENSION);

        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            remoteViewBuilder.setLargeIcon(roundedBitmap);
        } else remoteViewBuilder.setLargeIcon(R.drawable.ic_call_white_24dp);

        remoteViewBuilder.addCallHangupAction(sipService, callBundle);
        remoteViewBuilder.addCallHoldResumeAction(sipService, callBundle);
        remoteViewBuilder.enableChronoCountUp(true);
        notification.contentView = remoteViewBuilder.buildStandard();
        RemoteViews rv = remoteViewBuilder.buildExtended();
        notification.bigContentView = rv;
        notification.headsUpContentView = rv;

        notificationManager.notify(TAG_ONGOING, pjsipCallId, notification);
        synchronized (llNotifications) {
            llNotifications.add(new NotificationMeta(pjsipCallId, TAG_ONGOING));
        }
    }

    private void createNotificationCallIncoming(int pjsipCallId) {
        String contactName = null; // preferred display name

        String remoteDisplayName = null; // display name when !contactName
        String EXTENSION = getExtensionFromCallId(pjsipCallId); // get incoming extension
        Integer contactId = ContactProvider.getContactIdByNumber(context, EXTENSION); // lookup extension in user contacts
        if (contactId != null)
            contactName = ContactProvider.getContactName(context, contactId); // replace extension with friendly contact display_name if found
        else
            remoteDisplayName = getRemoteDisplayNameFromCallId(pjsipCallId);

        if (DEBUG) gLog.l(TAG, Logger.lvVerbose, pjsipCallId + " " + EXTENSION);
        Notification.Builder mBuilder = new Notification.Builder(context);
        mBuilder.setPriority(Notification.PRIORITY_HIGH);
        mBuilder.setCategory(Notification.CATEGORY_CALL);
        mBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);

        mBuilder.setUsesChronometer(true);

        long statCallStartTimeFromCallId = getStatCallStartTimeFromCallId(pjsipCallId);
        if (statCallStartTimeFromCallId == 0) statCallStartTimeFromCallId = System.currentTimeMillis(); // sanity check
        mBuilder.setWhen(statCallStartTimeFromCallId);

        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            mBuilder.setLargeIcon(roundedBitmap);
        }

        mBuilder.setSmallIcon(R.drawable.ic_call_white_24dp);
        mBuilder.setContentText(SipService.getInstance().getI18n().getString("incoming_call", "Incoming Call"));
        setNotificationDisplayName(mBuilder, contactName, remoteDisplayName, EXTENSION);


        Bundle callBundle = new Bundle();
        callBundle.putInt(EXTRA_BIND_PJSIPCALLID, pjsipCallId);
        callBundle.putInt(EXTRA_NOTIFICATION_ID, pjsipCallId);
        callBundle.putInt(EXTRA_PJSIP_CALL_ID, pjsipCallId);

        addCallAnswerAction(mBuilder, callBundle);
        addCallDismissAction(mBuilder, callBundle);
        addCallIncomingSwipeAwayAction(mBuilder, callBundle);

//        // Creates an explicit intent for an Activity in your app
//        Intent resultIntent = new Intent(context, DialtactsActivity.class);
//
//        // The stack builder object will contain an artificial back stack for the
//        // started Activity.
//        // This ensures that navigating backward from the Activity leads out of
//        // your application to the Home screen.
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
//        // Adds the back stack for the Intent (but not the Intent itself)
//        stackBuilder.addParentStack(DialtactsActivity.class);
//        // Adds the Intent that starts the Activity to the top of the stack
//        stackBuilder.addNextIntent(resultIntent);
//
//        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // the full screen intent will be automatically shown when the device is locked
        mBuilder.setFullScreenIntent(createFullScreenActivityCallPendingIntent(pjsipCallId, callBundle), true);
        mBuilder.setContentIntent(createFullScreenActivityCallPendingIntent(pjsipCallId, callBundle));
//        PendingIntent answerVoicePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_ANSWER_VOICE_INCOMING_CALL, callBundle);
//        mBuilder.setFullScreenIntent(answerVoicePendingIntent, true);

//        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
//        mBuilder.setSound(alarmSound);

        Notification notification = mBuilder.build();
        notification.flags |= Notification.FLAG_INSISTENT;
//        notification.flags |= Notification.FLAG_NO_CLEAR;


        final RemoteViewBuilder remoteViewBuilder = RemoteViewBuilder.newInstance(context)
                .populateHeaderDefault(sipService)
                .setHeaderAltText(SipService.getInstance().getI18n().getString("incoming_call", "Incoming Call"))
                .setUseThemeDark(SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false));

        if (contactName != null && !contactName.isEmpty()) {
            remoteViewBuilder.setMainText(contactName);
            remoteViewBuilder.setAltText2(EXTENSION);
        } else if (remoteDisplayName != null && !remoteDisplayName.isEmpty()) {
            remoteViewBuilder.setMainText(remoteDisplayName);
            remoteViewBuilder.setAltText2(EXTENSION);
        } else remoteViewBuilder.setMainText(EXTENSION);


        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            remoteViewBuilder.setLargeIcon(roundedBitmap);
        } else remoteViewBuilder.setLargeIcon(R.drawable.ic_call_white_24dp);

        remoteViewBuilder.addCallAnswerButton(sipService, callBundle);
        remoteViewBuilder.addCallDismissButton(sipService, callBundle);
        remoteViewBuilder.enableChronoCountUp(true);
        notification.contentView = remoteViewBuilder.buildStandard();
        RemoteViews rv = remoteViewBuilder.buildExtended();
        notification.bigContentView = rv;
        notification.headsUpContentView = rv;


        notificationManager.notify(TAG_INCOMING, pjsipCallId, notification);
        synchronized (llNotifications) {
            llNotifications.add(new NotificationMeta(pjsipCallId, TAG_INCOMING));
        }
    }

    private void createNotificationCallOngoing(int pjsipCallId) {
        cancelNotificationById(TAG_INCOMING, pjsipCallId);

        String contactName = null; // preferred display name
        String remoteDisplayName = null; // display name when !contactName
        String EXTENSION = getExtensionFromCallId(pjsipCallId); // get incoming extension
        Integer contactId = ContactProvider.getContactIdByNumber(context, EXTENSION); // lookup extension in user contacts
        if (contactId != null)
            contactName = ContactProvider.getContactName(context, contactId); // replace extension with friendly contact display_name if found
        else
            remoteDisplayName = getRemoteDisplayNameFromCallId(pjsipCallId);

        Notification.Builder mBuilder = new Notification.Builder(context);
        mBuilder.setPriority(Notification.PRIORITY_HIGH);
        mBuilder.setCategory(Notification.CATEGORY_CALL);
        mBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);

        mBuilder.setUsesChronometer(true);

//        mBuilder.setSound(null);

        long statCallStartTimeFromCallId = getStatCallStartTimeFromCallId(pjsipCallId);
        if (statCallStartTimeFromCallId == 0) statCallStartTimeFromCallId = System.currentTimeMillis(); // sanity check
        mBuilder.setWhen(statCallStartTimeFromCallId);

        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            mBuilder.setLargeIcon(roundedBitmap);
        }

        mBuilder.setSmallIcon(R.drawable.ic_call_white_24dp)
                .setContentText(SipService.getInstance().getI18n().getString("ongoing_call", "Ongoing Call"));

        setNotificationDisplayName(mBuilder, contactName, remoteDisplayName, EXTENSION);

        Bundle callBundle = new Bundle();
        callBundle.putInt(EXTRA_BIND_PJSIPCALLID, pjsipCallId);
        callBundle.putInt(EXTRA_NOTIFICATION_ID, pjsipCallId);
        callBundle.putInt(EXTRA_PJSIP_CALL_ID, pjsipCallId);

        addCallHangupAction(mBuilder, callBundle);
        addCallHoldAction(mBuilder, callBundle);
        mBuilder.setDeleteIntent(null);

//        // Creates an explicit intent for an Activity in your app
//        Intent resultIntent = new Intent(context, DialtactsActivity.class);
//
//        // The stack builder object will contain an artificial back stack for the
//        // started Activity.
//        // This ensures that navigating backward from the Activity leads out of
//        // your application to the Home screen.
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
//        // Adds the back stack for the Intent (but not the Intent itself)
//        stackBuilder.addParentStack(DialtactsActivity.class);
//        // Adds the Intent that starts the Activity to the top of the stack
//        stackBuilder.addNextIntent(resultIntent);
//
//        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
////        mBuilder.setFullScreenIntent(resultPendingIntent, true);

        mBuilder.setContentIntent(createFullScreenActivityCallPendingIntent(pjsipCallId, callBundle));

//        mBuilder.setSound(null);

        Notification notification = mBuilder.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        notification.flags |= Notification.FLAG_NO_CLEAR;

        final RemoteViewBuilder remoteViewBuilder = RemoteViewBuilder.newInstance(context)
                .populateHeaderDefault(SipService.getInstance())
                .setHeaderAltText(SipService.getInstance().getI18n().getString("ongoing_call", "Ongoing Call"))
                .setUseThemeDark(SipService.getInstance().getLocalStore().getBoolean(Prefs.KEY_UI_COLOR_NOTIF_DARK, false));


        if (contactName != null && !contactName.isEmpty()) {
            remoteViewBuilder.setMainText(contactName);
            remoteViewBuilder.setAltText2(EXTENSION);
        } else if (remoteDisplayName != null && !remoteDisplayName.isEmpty()) {
            remoteViewBuilder.setMainText(remoteDisplayName);
            remoteViewBuilder.setAltText2(EXTENSION);
        } else remoteViewBuilder.setMainText(EXTENSION);


        if (contactId != null) {
            Bitmap contactPhotoThumb = ContactProvider.getContactPhotoThumb(context, contactId);
            Bitmap roundedBitmap = BitmapUtil.getRoundedBitmap(contactPhotoThumb, iconWidth, iconHeight);
            remoteViewBuilder.setLargeIcon(roundedBitmap);
        } else remoteViewBuilder.setLargeIcon(R.drawable.ic_call_white_24dp);

        remoteViewBuilder.addCallHangupAction(sipService, callBundle);
        remoteViewBuilder.addCallHoldAction(sipService, callBundle);
        remoteViewBuilder.enableChronoCountUp(true);
        notification.contentView = remoteViewBuilder.buildStandard();
        RemoteViews rv = remoteViewBuilder.buildExtended();
        notification.bigContentView = rv;
        notification.headsUpContentView = rv;

        notificationManager.notify(TAG_ONGOING, pjsipCallId, notification);
        synchronized (llNotifications) {
            llNotifications.add(new NotificationMeta(pjsipCallId, TAG_ONGOING));
        }
    }

    private SipCall getCallFromId(int pjsipCallId) {
        return SipService.getInstance().getSipCallById(pjsipCallId);
    }

    private String getExtensionFromCallId(int pjsipCallId) {
        SipCall sipCall = getCallFromId(pjsipCallId);
        if (sipCall == null) return "Unknown";
        return sipCall.getExtension();
    }

    private String getRemoteDisplayNameFromCallId(int pjsipCallId) {
        SipCall sipCall = getCallFromId(pjsipCallId);
        if (sipCall == null) return null;
        return sipCall.getRemoteDisplayName();
    }

    private long getStatCallStartTimeFromCallId(int pjsipCallId) {
        SipCall sipCall = getCallFromId(pjsipCallId);
        if (sipCall == null) return 0;
        return sipCall.getStatCallStartTime();
    }

    private boolean getWasDeclinedFromCallId(int pjsipCallId) {
        SipCall sipCall = getCallFromId(pjsipCallId);
        if (sipCall == null) return false;
        return sipCall.wasDeclined();
    }

    private void removeTrackedNotificationEntry(int id, String tag) {
        synchronized (llNotifications) {
            NotificationMeta toRemove = null;
            for (NotificationMeta notificationMeta : llNotifications) {
                if (notificationMeta.id == id && notificationMeta.tag == tag) {
                    toRemove = notificationMeta;
                    break;
                }
            }
            llNotifications.remove(toRemove);
            toRemove = null;
        }
    }

    /**
     * Notification Builder Helper: set content title and subtext based on available call info
     *
     * @param mBuilder
     * @param contactName
     * @param remoteDisplayName
     * @param extension
     */
    private void setNotificationDisplayName(Notification.Builder mBuilder, String contactName, String remoteDisplayName, String extension) {
        if (contactName != null && !contactName.isEmpty()) mBuilder.setContentTitle(contactName);
        else if (remoteDisplayName != null && !remoteDisplayName.isEmpty()) {
            mBuilder.setContentTitle(remoteDisplayName);
            mBuilder.setSubText(extension);
        } else mBuilder.setContentTitle(extension);
    }

    static class RemoteViewBuilder {
        private Context context;
        private String headerText;
        private String headerAltText;
        private String mainText;
        private String altText;
        private String altText2;
        private Integer smallIconResource;
        private Integer largeIconResource;
        private Integer colorBg;
        private Bitmap largeIcon;
        private Bitmap smallIcon;
        private boolean useChronoCountUp = false;
        private LinkedList<ActionButton> llActionButtons = new LinkedList<>();
        private String chronoFormat;
        private String chronoString;
        private boolean useThemeDark = false;
        private Integer colorText;
        private int themeDarkBg, themeDarkBgAlt;
        private int themeDarkTextSecondary;


        static RemoteViewBuilder newInstance(final Context context) {
            final RemoteViewBuilder remoteViewBuilder = new RemoteViewBuilder();
            remoteViewBuilder.context = context;
            return remoteViewBuilder;
        }

        RemoteViewBuilder addCallAnswerButton(SipService sipService, Bundle extras) {
            PendingIntent answerVoicePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_ANSWER_VOICE_INCOMING_CALL, extras);
            addRemoteActionButton(R.drawable.ic_call_white_24dp, sipService.getI18n().getString("answer", "Answer"), answerVoicePendingIntent);
            return this;
        }

        RemoteViewBuilder addCallDismissButton(SipService sipService, Bundle extras) {
            PendingIntent declinePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_DECLINE_INCOMING_CALL, extras);
            addRemoteActionButton(R.drawable.ic_close_white_24dp, sipService.getI18n().getString("decline", "Decline"), declinePendingIntent);
            return this;
        }

        RemoteViewBuilder addCallHangupAction(SipService sipService, Bundle extras) {
            PendingIntent hangupPendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_HANG_UP_ONGOING_CALL, extras);
            addRemoteActionButton(R.drawable.ic_call_end_white_24dp, sipService.getI18n().getString("hangup", "Hangup"), hangupPendingIntent);
            return this;

        }

        RemoteViewBuilder addCallHoldAction(SipService sipService, Bundle extras) {
            PendingIntent holdPendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_HOLD_ONGOING_CALL, extras);
            addRemoteActionButton(R.drawable.ic_pause_white_24dp, sipService.getI18n().getString("hold", "Hold"), holdPendingIntent);
            return this;

        }

        RemoteViewBuilder addCallHoldResumeAction(SipService sipService, Bundle extras) {
            PendingIntent resumePendingIntent = createNotificationPendingIntent(context, NotificationReceiver.ACTION_HOLD_RESUME_ONGOING_CALL, extras);
            addRemoteActionButton(R.drawable.ic_play_arrow_white_24dp, sipService.getI18n().getString("resume", "Resume"), resumePendingIntent);
            return this;

        }

        RemoteViewBuilder addRemoteActionButton(final int iconResource, final String text, final PendingIntent pendingIntent) {
            final ActionButton b = new ActionButton();
            b.imageResource = iconResource;
            b.pendingIntent = pendingIntent;
            b.text = text;
            llActionButtons.add(b);
            return this;
        }

        RemoteViews buildExtended() {
            generateThemeColors();
            final RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.notif_ext);

            if (smallIconResource != null) {
                remoteViews.setInt(R.id.nIvIconSmall, "setColorFilter", useThemeDark ? Color.WHITE : Color.DKGRAY);
                remoteViews.setImageViewResource(R.id.nIvIconSmall, smallIconResource);
            } else if (smallIcon != null) {
                remoteViews.setImageViewBitmap(R.id.nIvIconSmall, smallIcon);
            }

            if (largeIconResource != null) {
                remoteViews.setImageViewResource(R.id.nIvIconLarge, largeIconResource);
                remoteViews.setInt(R.id.nIvIconLarge, "setColorFilter", useThemeDark ? Color.WHITE : Color.DKGRAY);
                remoteViews.setInt(R.id.nIvIconLarge, "setVisibility", View.VISIBLE);
            } else if (largeIcon != null) {
                remoteViews.setImageViewBitmap(R.id.nIvIconLarge, largeIcon);
                remoteViews.setInt(R.id.nIvIconLarge, "setVisibility", View.VISIBLE);
            } else remoteViews.setInt(R.id.nIvIconLarge, "setVisibility", View.GONE);


            remoteViews.setTextViewText(R.id.nTvHeaderTitle, headerText);
            if (headerAltText != null) {
                remoteViews.setTextViewText(R.id.nTvHeaderAlt, headerAltText);
                remoteViews.setInt(R.id.nTvHeaderAlt, "setVisibility", View.VISIBLE);
                remoteViews.setInt(R.id.nTvHeaderAltSep, "setVisibility", View.VISIBLE);
            } else {
                remoteViews.setInt(R.id.nTvHeaderAlt, "setVisibility", View.GONE);
                remoteViews.setInt(R.id.nTvHeaderAltSep, "setVisibility", View.GONE);

            }

            if (mainText != null) {
                remoteViews.setTextViewText(R.id.tvValue, mainText);
                remoteViews.setInt(R.id.tvValue, "setVisibility", View.VISIBLE);
            } else remoteViews.setInt(R.id.tvValue, "setVisibility", View.GONE);


            if (altText != null) {
                remoteViews.setTextViewText(R.id.tvValue2, altText);
                remoteViews.setInt(R.id.tvValue2, "setVisibility", View.VISIBLE);
            } else remoteViews.setInt(R.id.tvValue2, "setVisibility", View.GONE);

            if (altText2 != null) {
                remoteViews.setTextViewText(R.id.tvValue3, altText2);
                remoteViews.setInt(R.id.tvValue3, "setVisibility", View.VISIBLE);
            } else remoteViews.setInt(R.id.tvValue3, "setVisibility", View.GONE);

            if (colorBg != null) {
                remoteViews.setInt(R.id.llListItem, "setVisibility", View.VISIBLE);
                remoteViews.setInt(R.id.llListItem, "setBackgroundColor", colorBg);
            } else
                remoteViews.setInt(R.id.llListItem, "setVisibility", View.GONE);
            if (useChronoCountUp) {
                remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.VISIBLE);
                remoteViews.setChronometer(R.id.nTvHeaderCounter, SystemClock.elapsedRealtime(), chronoFormat, true);
            } else remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.GONE);
            if (chronoString != null) {
                remoteViews.setTextViewText(R.id.nTvHeaderCounter, chronoString);
                remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.VISIBLE);
            } else remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.GONE);

            if (useThemeDark) {
                remoteViews.setInt(R.id.nBase, "setBackgroundColor", themeDarkBg);
                remoteViews.setInt(R.id.nLlButtonBar, "setBackgroundColor", themeDarkBgAlt);
                remoteViews.setInt(R.id.nTvHeaderTitle, "setTextColor", Color.WHITE);
                remoteViews.setInt(R.id.nTvHeaderAltSep, "setTextColor", themeDarkTextSecondary);
                remoteViews.setInt(R.id.nTvHeaderAlt, "setTextColor", themeDarkTextSecondary);
                remoteViews.setInt(R.id.tvValue, "setTextColor", Color.WHITE);
                remoteViews.setInt(R.id.tvValue2, "setTextColor", themeDarkTextSecondary);
                remoteViews.setInt(R.id.tvValue3, "setTextColor", themeDarkTextSecondary);
                remoteViews.setInt(R.id.nTvHeaderCounter, "setTextColor", themeDarkTextSecondary);
            }

            remoteViews.removeAllViews(R.id.nLlButtonBar);
            for (ActionButton b : llActionButtons) {
                final RemoteViews remoteButton = new RemoteViews(context.getPackageName(), R.layout.compf_img_btn);
                if (useThemeDark) remoteButton.setInt(R.id.nLlButton, "setBackgroundColor", themeDarkBgAlt);
                remoteButton.setImageViewResource(R.id.nLlButtonImageView, b.imageResource);
                remoteButton.setInt(R.id.nLlButtonImageView, "setColorFilter", useThemeDark ? Color.WHITE : Color.DKGRAY);
                remoteButton.setTextViewText(R.id.nLlButtonTextView, b.text.toUpperCase());
                if (useThemeDark) remoteButton.setInt(R.id.nLlButtonTextView, "setTextColor", themeDarkTextSecondary);
                remoteButton.setOnClickPendingIntent(R.id.nLlButton, b.pendingIntent);
                remoteViews.addView(R.id.nLlButtonBar, remoteButton);
            }

            return remoteViews;
        }

        RemoteViews buildStandard() {
            generateThemeColors();
            final RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.notif_std);

            if (smallIconResource != null) {
                remoteViews.setInt(R.id.nIvIconSmall, "setColorFilter", useThemeDark ? Color.WHITE : Color.DKGRAY);
                remoteViews.setImageViewResource(R.id.nIvIconSmall, smallIconResource);
            } else if (smallIcon != null) {
                remoteViews.setImageViewBitmap(R.id.nIvIconSmall, smallIcon);
            }
            if (largeIconResource != null) {
                remoteViews.setImageViewResource(R.id.nIvIconLarge, largeIconResource);
                remoteViews.setInt(R.id.nIvIconLarge, "setVisibility", View.VISIBLE);
            } else if (largeIcon != null) {
                remoteViews.setImageViewBitmap(R.id.nIvIconLarge, largeIcon);
                remoteViews.setInt(R.id.nIvIconLarge, "setVisibility", View.VISIBLE);
            } else {
                remoteViews.setInt(R.id.nIvIconLarge, "setVisibility", View.GONE);
            }

            remoteViews.setTextViewText(R.id.nTvHeaderTitle, headerText);
            if (headerAltText != null) {
                remoteViews.setTextViewText(R.id.nTvHeaderAlt, headerAltText);
                remoteViews.setInt(R.id.nTvHeaderAlt, "setVisibility", View.VISIBLE);
                remoteViews.setInt(R.id.nTvHeaderAltSep, "setVisibility", View.VISIBLE);
            } else {
                remoteViews.setInt(R.id.nTvHeaderAlt, "setVisibility", View.GONE);
                remoteViews.setInt(R.id.nTvHeaderAltSep, "setVisibility", View.GONE);
            }

            if (mainText != null) {
                remoteViews.setTextViewText(R.id.tvValue, mainText);
                remoteViews.setInt(R.id.tvValue, "setVisibility", View.VISIBLE);
            } else
                remoteViews.setInt(R.id.tvValue, "setVisibility", View.GONE);
            if (altText != null) {
                remoteViews.setTextViewText(R.id.tvValue2, altText);
                remoteViews.setInt(R.id.tvValue2, "setVisibility", View.VISIBLE);
            } else
                remoteViews.setInt(R.id.tvValue2, "setVisibility", View.GONE);
            if (colorBg != null) {
                remoteViews.setInt(R.id.llListItem, "setVisibility", View.VISIBLE);
                remoteViews.setInt(R.id.llListItem, "setBackgroundColor", colorBg);
            } else remoteViews.setInt(R.id.llListItem, "setVisibility", View.GONE);

            if (useChronoCountUp) {
                remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.VISIBLE);
                remoteViews.setChronometer(R.id.nTvHeaderCounter, SystemClock.elapsedRealtime(), chronoFormat, true);
            } else remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.GONE);

            if (chronoString != null) {
                remoteViews.setTextViewText(R.id.nTvHeaderCounter, chronoString);
                remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.VISIBLE);
            } else remoteViews.setInt(R.id.nTvHeaderCounter, "setVisibility", View.GONE);

            if (useThemeDark) {
                remoteViews.setInt(R.id.nBase, "setBackgroundColor", themeDarkBg);
                remoteViews.setInt(R.id.nTvHeaderTitle, "setTextColor", Color.WHITE);
                remoteViews.setInt(R.id.nTvHeaderAltSep, "setTextColor", themeDarkTextSecondary);
                remoteViews.setInt(R.id.nTvHeaderAlt, "setTextColor", themeDarkTextSecondary);
                remoteViews.setInt(R.id.tvValue, "setTextColor", Color.WHITE);
                remoteViews.setInt(R.id.tvValue2, "setTextColor", themeDarkTextSecondary);
            }

            return remoteViews;
        }

        RemoteViewBuilder enableChronoCountUp(boolean enable) {
            useChronoCountUp = enable;
            return this;
        }

        RemoteViewBuilder populateHeaderDefault(SipService sipService) {
            setHeaderText(sipService.getI18n().getString("app_name", "VOSP"));
            setSmallIcon(R.drawable.ic_noti_default);
            return this;
        }

        RemoteViewBuilder setAltText(final String altText) {
            this.altText = altText;
            return this;
        }

        RemoteViewBuilder setAltText2(final String altText2) {
            this.altText2 = altText2;
            return this;
        }

        RemoteViewBuilder setBackgroundColor(final Integer color) {
            this.colorBg = color;
            return this;
        }

        RemoteViewBuilder setChronoAsString(String text) {
            chronoString = text;
            useChronoCountUp = false;
            return this;
        }

        RemoteViewBuilder setChronoFormat(String format) {
            chronoFormat = format;
            return this;
        }

        RemoteViewBuilder setHeaderAltText(final String headerAltText) {
            this.headerAltText = headerAltText;
            return this;
        }

        RemoteViewBuilder setHeaderText(final String headerText) {
            this.headerText = headerText;
            return this;
        }

        RemoteViewBuilder setLargeIcon(final Integer resourceId) {
            this.largeIconResource = resourceId;
            return this;
        }

        RemoteViewBuilder setLargeIcon(final Bitmap bitmap) {
            this.largeIcon = bitmap;
            return this;
        }

        RemoteViewBuilder setMainText(final String mainText) {
            this.mainText = mainText;
            return this;
        }

        RemoteViewBuilder setSmallIcon(final Bitmap bitmap) {
            this.smallIcon = bitmap;
            return this;
        }

        RemoteViewBuilder setSmallIcon(final Integer resourceId) {
            this.smallIconResource = resourceId;
            return this;
        }

        RemoteViewBuilder setUseThemeDark(boolean useThemeDark) {
            this.useThemeDark = useThemeDark;
            return this;
        }

        private void generateThemeColors() {
            if (useThemeDark) {
                themeDarkBg = Color.parseColor("#ff384248");
                themeDarkBgAlt = Color.parseColor("#ff505e66");
                themeDarkTextSecondary = context.getResources().getColor(R.color.md_grey_400);
            }
        }

        private class ActionButton {
            String text;
            PendingIntent pendingIntent;
            Integer imageResource;
        }

    }


    class NotificationMeta {
        public int id;
        public String tag;

        NotificationMeta(int id, String tag) {
            this.id = id;
            this.tag = tag;
        }
    }

}
