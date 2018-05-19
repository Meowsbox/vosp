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

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by dhon on 3/11/2018.
 */

public class DozeController {
    public static final boolean DEBUG = SipService.DEBUG;
    public static final int MODE_DEFAULT = 0; // idle
    public static final int MODE_ALL = 1; // disable doze completely
    public static final int MODE_LIGHT_ONLY = 2; // aggressively light, block deep
    public static final int MODE_DEEP_ONLY = 3; // aggressively deep, skip light
    private static final String SETTINGS_GLOBAL_DOZE_CONSTANT = "device_idle_constants";
    private static final String DOZE_DISABLE_IDLE_CONSTANT = "inactive_to=604800000,idle_after_inactive_to=604800000"; // extends inactive to one week, effectively disabling doze
    private static final String DOZE_DEEP_ONLY_CONSTANT = "light_after_inactive_to=0,light_pre_idle_to=0,light_idle_to=10000,light_idle_factor=2.0,light_max_idle_to=10000,light_idle_maintenance_min_budget=5000,light_idle_maintenance_max_budget=10000,min_light_maintenance_time=5000,min_deep_maintenance_time=5000,inactive_to=0,sensing_to=20000,locating_to=0,location_accuracy=20.0m,motion_inactive_to=0,idle_after_inactive_to=0,idle_pending_to=0,max_idle_pending_to=0,idle_pending_factor=2.0,idle_to=3600000,max_idle_to=3600000,idle_factor=2.0,min_time_to_alarm=0,max_temp_app_whitelist_duration=30000,mms_temp_app_whitelist_duration=30000,sms_temp_app_whitelist_duration=30000,notification_whitelist_duration=10000";
    private static final String DOZE_LIGHT_ONLY_CONSTANT = "inactive_to=172800000,idle_after_inactive_to=172800000";
    private final static int CHECK_INTERVAL = 3600000; // hourly
    public final String TAG = this.getClass().getName();
    Logger gLog;
    Context context;
    Timer timer = null;
    boolean isEnabled = false;
    int mode = MODE_DEFAULT;
    private boolean hasPermissionDump, hasPermissionRoot, hasPermissionWriteSecure;

    DozeController(Context context) {
        this.context = context;
        gLog = SipService.getInstance().getLoggerInstanceShared();
        mode = MODE_DEFAULT;
        checkPermissions();
    }

    public void checkPermissions() {
        hasPermissionRoot = Shell.SU.available();
        hasPermissionDump = context.checkCallingOrSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED;
        hasPermissionWriteSecure = context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    public void destroy() {
        disable();
        if (timer != null) timer.cancel();
        timer = null;
        context = null;
    }

    public void disable() {
        if (!isEnabled) return;
        switch (mode) {
            case MODE_LIGHT_ONLY:
                if (hasPermissionRoot) Shell.SU.run("dumpsys deviceidle enable");
                else if (hasPermissionWriteSecure && hasPermissionDump) clearDozeConfig();
                isEnabled = false;
                break;
            case MODE_DEEP_ONLY:
                if (hasPermissionRoot) Shell.SU.run("dumpsys deviceidle enable");
                else if (hasPermissionWriteSecure && hasPermissionDump) clearDozeConfig();
                isEnabled = false;
                break;
            case MODE_ALL:
                if (hasPermissionRoot) Shell.SU.run("dumpsys deviceidle enable");
                else if (hasPermissionWriteSecure && hasPermissionDump) clearDozeConfig();
                isEnabled = false;
                break;
            case MODE_DEFAULT:
            default:
        }
    }

    public void enable() {
        switch (mode) {
            case MODE_LIGHT_ONLY:
                if (hasPermissionRoot) Shell.SU.run("dumpsys deviceidle disable deep");
                else if (hasPermissionWriteSecure && hasPermissionDump) applyDozeConfig(DOZE_LIGHT_ONLY_CONSTANT);
                isEnabled = true;
                timerEnable();
                break;
            case MODE_DEEP_ONLY:
                if (hasPermissionRoot) Shell.SU.run("dumpsys deviceidle disable light");
                else if (hasPermissionWriteSecure && hasPermissionDump) applyDozeConfig(DOZE_DEEP_ONLY_CONSTANT);
                isEnabled = true;
                timerEnable();
                break;
            case MODE_ALL:
                if (hasPermissionRoot) Shell.SU.run("dumpsys deviceidle disable");
                else if (hasPermissionWriteSecure && hasPermissionDump) applyDozeConfig(DOZE_DISABLE_IDLE_CONSTANT);
                isEnabled = true;
                timerEnable();
                break;
            case MODE_DEFAULT:
            default:
        }
    }

    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT < 23)
            return true;
        if (Build.VERSION.SDK_INT == 23) if (hasPermissionRoot || hasPermissionDump) return true;
        if (hasPermissionRoot || (hasPermissionDump && hasPermissionWriteSecure)) return true;
        return false;
    }

    public void onRefreshState() {
        if (!isEnabled) return;
        enable();
    }

    public void setMode(int mode) {
        if (Build.VERSION.SDK_INT < 23) {
            gLog.l(TAG, Logger.lvDebug, "SDK_INT < 23, forcing MODE_DEFAULT");
            return;
        }
        if (Build.VERSION.SDK_INT == 23) {
            if (mode == MODE_LIGHT_ONLY) {
                gLog.l(TAG, Logger.lvDebug, "SDK_INT == 23, Invalid Mode");
                return;
            }
        }
        this.mode = mode;
    }

    private boolean applyDozeConfig(final String config) {
        final ContentResolver cr = context.getContentResolver();
        try {
            if (!Settings.Global.putString(cr, SETTINGS_GLOBAL_DOZE_CONSTANT, config)) {
                gLog.l(TAG, Logger.lvDebug, "Failed to write settings");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            gLog.l(TAG, Logger.lvDebug, e);
            return false;
        }
        try {
            if (!Settings.Global.getString(cr, SETTINGS_GLOBAL_DOZE_CONSTANT).contains(config)) {
                gLog.l(TAG, Logger.lvDebug, "Verify Failure " + Settings.Global.getString(cr, SETTINGS_GLOBAL_DOZE_CONSTANT));
                return false; // verify
            }
        } catch (Exception e) {
            e.printStackTrace();
            gLog.l(TAG, Logger.lvDebug, e);
            return false;
        }
        return true;
    }

    private boolean clearDozeConfig() {
        final ContentResolver cr = context.getContentResolver();
        try {
            if (!Settings.Global.putString(cr, SETTINGS_GLOBAL_DOZE_CONSTANT, "")) {
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

    private void timerEnable() {
        if (timer == null) {
            timer = new Timer("Doze Watcher");
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    enable();
                }
            }, 0, CHECK_INTERVAL);
        }
    }

}
