/*
 * Copyright (c) 2018. Darryl Hon
 * Modifications Copyright (c) 2018. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import com.meowsbox.vosp.common.Logger;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by dhon on 3/14/2017.
 */

public class DozeAmRelax {
    public static final boolean DEBUG = SipService.DEBUG;
    private static final int CHECK_INTERVAL = 3600000; // hourly
    private static final String AM_CONSTANT = "min_interval=1000,allow_while_idle_long_time=1000";
    private static final String SETTINGS_GLOBAL_AM_CONSTANT = "alarm_manager_constants";
    public final String TAG = this.getClass().getName();
    Logger gLog;
    Timer timer = null;
    Context context = null;
    boolean hasPermissionWriteSecure = false;

    public DozeAmRelax(Context context) {
        this.context = context;
        gLog = SipService.getInstance().getLoggerInstanceShared();
    }

    public void destroy() {
        if (timer != null) timer.cancel();
        timer = null;
        dozeAmRelaxClear(context);
        context = null;
    }

    public void disable() {
        if (timer != null) timer.cancel();
        timer = null;
        dozeAmRelaxClear(context);
    }

    public boolean enable() {
        if (Build.VERSION.SDK_INT < 23) { // doze not available on old platforms
            gLog.l(TAG, Logger.lvDebug, "SDK_INT < 23");
            return false;
        }
        hasPermissionWriteSecure = checkPermissionWriteSecure();
        boolean result = dozeAmRelax(context);
        if (timer != null) timer.cancel();
        timer = new Timer("Doze AM Relax Watcher");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                dozeAmRelax(context);
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL);
        gLog.l(TAG, Logger.lvDebug, "Enabled " + result);
        return result;
    }

    public boolean hasPermissionWriteSecure() {
        return checkPermissionWriteSecure();
    }

    private boolean checkPermissionWriteSecure() {
        return context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    private boolean dozeAmRelax(Context context) {
        if (context == null) {
            gLog.l(TAG, Logger.lvDebug, "Context NULL");
            return false;
        }
        if (!hasPermissionWriteSecure) {
            gLog.l(TAG, Logger.lvDebug, "Not granted WRITE_SECURE_SETTINGS");
            return false;
        }
        final ContentResolver cr = context.getContentResolver();
        if (cr == null) {
            gLog.l(TAG, Logger.lvDebug, "ContentResolver NULL");
            return false;
        }
        try {
            if (!Settings.Global.putString(cr, SETTINGS_GLOBAL_AM_CONSTANT, AM_CONSTANT)) {
                gLog.l(TAG, Logger.lvDebug, "Failed to write settings");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            gLog.l(TAG, Logger.lvDebug, e);
            return false;
        }
        return true;
    }
    private boolean dozeAmRelaxClear(Context context) {
        if (context == null) {
            gLog.l(TAG, Logger.lvDebug, "Context NULL");
            return false;
        }
        if (!hasPermissionWriteSecure) {
            gLog.l(TAG, Logger.lvDebug, "Not granted WRITE_SECURE_SETTINGS");
            return false;
        }
        final ContentResolver cr = context.getContentResolver();
        if (cr == null) {
            gLog.l(TAG, Logger.lvDebug, "ContentResolver NULL");
            return false;
        }
        try {
            if (!Settings.Global.putString(cr, SETTINGS_GLOBAL_AM_CONSTANT, "")) {
                gLog.l(TAG, Logger.lvDebug, "Failed to write settings");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            gLog.l(TAG, Logger.lvDebug, e);
            return false;
        }
        try {
            if (!Settings.Global.getString(cr, SETTINGS_GLOBAL_AM_CONSTANT).contains(AM_CONSTANT)) {
                gLog.l(TAG, Logger.lvDebug, "Verify Failure " + Settings.Global.getString(cr, SETTINGS_GLOBAL_AM_CONSTANT));
                return false; // verify
            }
        } catch (Exception e) {
            e.printStackTrace();
            gLog.l(TAG, Logger.lvDebug, e);
            return false;
        }
        return true;
    }

}
