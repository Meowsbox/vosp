/*
 * Copyright (c) 2017. Darryl Hon
 * Modifications Copyright (c) 2017. Darryl Hon
 *
 * This file is part of VOSP and may be covered under one or more licenses.
 * Refer to the project LICENSE and NOTICE files for details.
 */

package com.meowsbox.vosp.service;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.meowsbox.vosp.common.Logger;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by dhon on 3/14/2017.
 */

public class DozeDisabler {
    public final static boolean DEBUG = SipService.DEBUG;
    private final static int CHECK_INTERVAL = 3600000; // hourly
    public final String TAG = this.getClass().getName();
    Logger gLog;
    Timer timer = null;
    boolean isEnabled = false;
    boolean useRoot = false;
    Context context = null;
    boolean hasPermissionDump = false;

    public DozeDisabler(Context context) {
        this.context = context;
        gLog = SipService.getInstance().getLoggerInstanceShared();
    }

    public void destroy() {
        if (timer != null) timer.cancel();
        timer = null;
        context = null;
    }

    public void disable() {
        if (timer != null) timer.cancel();
        timer = null;
    }

    /**
     * Enable doze disabler and watchdog
     *
     * @param useRoot
     * @return TRUE = doze disable successfully, FALSE on failure
     */
    public boolean enable(final boolean useRoot) {
        if (Build.VERSION.SDK_INT < 23) return false; // doze not available on old platforms
        hasPermissionDump = checkPermissionDump();
        boolean result = false;
        Boolean isDozeEnabled = isDozeEnabled();
        if (isEnabled && this.useRoot == useRoot)
            return !isDozeEnabled; // return current doze status if already running in same mode
        this.useRoot = useRoot;
        if (isDozeEnabled == null || isDozeEnabled) {
            dozeDisable(useRoot, hasPermissionDump);
            isDozeEnabled = isDozeEnabled(); // check result
            if (!isDozeEnabled) {
                result = true;
                gLog.l(TAG, Logger.lvVerbose, "Doze successfully disabled");
            } else gLog.l(TAG, Logger.lvVerbose, "Failed to disable doze");
        } else {
            result = true;
            gLog.l(TAG, Logger.lvVerbose, "Doze not enabled");
        }

        if (timer != null) timer.cancel();
        timer = new Timer("Doze Watcher");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                dozeDisable(useRoot, hasPermissionDump);
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL);
        return result;
    }

    public boolean hasPermissionDump() {
        return checkPermissionDump();
    }

    public boolean isRootAvaialble() {
        return Shell.SU.available();
    }

    private boolean checkPermissionDump() {
        return context.checkCallingOrSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED;
    }

    private void dozeDisable(boolean useRoot, boolean hasDump) {
        List<String> result = null;
        if (hasPermissionDump || !useRoot) result = Shell.SH.run("dumpsys deviceidle disable");
        else if (useRoot) result = Shell.SU.run("dumpsys deviceidle disable");
        if (DEBUG) if (result != null) for (String s : result) gLog.l(TAG, Logger.lvVerbose, s);
    }

    private Boolean isDozeEnabled() {
        List<String> result = null;
        if (hasPermissionDump || !useRoot) result = Shell.SH.run("dumpsys deviceidle enabled");
        else if (useRoot) result = Shell.SU.run("dumpsys deviceidle enabled");
        if (result == null) return null;
        for (String s : result) {
            gLog.l(TAG, Logger.lvVerbose, s);
            if (s.contains("1")) return true;
            else if (s.contains("0")) return false;
        }
        return null;
    }
}
